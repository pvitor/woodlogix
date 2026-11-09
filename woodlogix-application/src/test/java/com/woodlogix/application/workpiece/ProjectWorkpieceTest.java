package com.woodlogix.application.workpiece;

import com.woodlogix.application.edgebanding.EdgeBandingRow;
import com.woodlogix.application.material.MaterialRow;
import com.woodlogix.application.transaction.TransactionPort;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class ProjectWorkpieceTest {
    @Test
    void createsAndUpdatesInsideWriteTransactions() {
        var fixture = new Fixture();
        var input = input("Project", "Panel", "material", "edge");
        var created = fixture.projectWorkpiece.create(input);

        assertEquals("piece", created.id());
        assertEquals(input, fixture.createdInput);
        assertEquals(List.of("writeResult", "create"), fixture.events);

        fixture.events.clear();
        fixture.rows.put("piece", created);
        var updated = fixture.projectWorkpiece.update("piece", input);

        assertEquals(created, updated);
        assertEquals(input, fixture.updatedInput);
        assertEquals(List.of("writeResult", "update:piece"), fixture.events);
    }

    @Test
    void deletesInsideWriteTransactionAndReportsMissingIds() {
        var fixture = new Fixture();
        fixture.rows.put("piece", row("piece"));

        fixture.projectWorkpiece.delete("piece");

        assertFalse(fixture.rows.containsKey("piece"));
        assertEquals(List.of("write", "delete:piece"), fixture.events);

        fixture.events.clear();
        var failure = assertThrows(ProjectWorkpieceNotFoundException.class,
                () -> fixture.projectWorkpiece.delete("missing"));
        assertEquals("error.workpiece-not-found", failure.getMessage());
        assertEquals(List.of("write", "delete:missing"), fixture.events);
    }

    @Test
    void readsByProjectAndGloballyInsideReadOnlyTransactions() {
        var fixture = new Fixture();
        var row = row("piece");
        fixture.rows.put(row.id(), row);
        fixture.projectRows = List.of(row);

        assertEquals(List.of(row), fixture.projectWorkpiece.findByProject("project"));
        assertEquals(List.of(row), fixture.projectWorkpiece.findAll());
        assertEquals(List.of("read", "findProject:project", "read", "findAll"),
                fixture.events);
    }

    @Test
    void updateReportsMissingIdWithoutCallingPersistenceOutsideTransaction() {
        var fixture = new Fixture();

        var failure = assertThrows(ProjectWorkpieceNotFoundException.class,
                () -> fixture.projectWorkpiece.update("missing", input(
                        "Project", "Panel", "material", null)));

        assertEquals("error.workpiece-not-found", failure.getMessage());
        assertEquals(List.of("writeResult", "update:missing"), fixture.events);
    }

    @Test
    void propagatesPortAndTransactionFailuresWithoutWrapping() {
        var fixture = new Fixture();
        fixture.failureOn = "create";

        var failure = assertThrows(IllegalStateException.class,
                () -> fixture.projectWorkpiece.create(input("Project", "Panel", "material", null)));

        assertSame(fixture.failure, failure);
        assertEquals(List.of("writeResult", "create"), fixture.events);
    }

    @Test
    void inputAndRowsCarryCatalogDataAndOptionalEdgeBanding() {
        var material = new MaterialRow("material", "MDF", "White", bd("18"),
                bd("1000"), bd("1000"), bd("120.00"));
        var edge = new EdgeBandingRow("edge", "PVC", bd("22"), bd("2.50"));
        var input = input("project", "Panel", "material", "edge");
        var rowWithEdge = new ProjectWorkpieceRow("piece", "project", "Panel", bd("100"),
                bd("200"), 2, material, edge, true, false, false, false);
        var rowWithoutEdge = new ProjectWorkpieceRow("piece-2", "project", "Shelf", bd("300"),
                bd("200"), 1, material, null, false, false, false, false);

        assertEquals("project", input.projectId());
        assertEquals("material", input.materialId());
        assertEquals("edge", input.edgeBandingId());
        assertEquals("edge", rowWithEdge.edgeBanding().id());
        assertNull(rowWithoutEdge.edgeBanding());
        assertEquals("material", rowWithoutEdge.material().id());
    }

    private static ProjectWorkpieceInput input(String projectId, String name,
                                               String materialId, String edgeBandingId) {
        return new ProjectWorkpieceInput(projectId, name, bd("100"), bd("200"), 2,
                materialId, edgeBandingId, true, false, false, false);
    }

    private static ProjectWorkpieceRow row(String id) {
        return new ProjectWorkpieceRow(id, "project", "Panel", bd("100"), bd("200"), 2,
                new MaterialRow("material", "MDF", "White", bd("18"), bd("1000"),
                        bd("1000"), bd("120.00")),
                null, false, false, false, false);
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private static final class Fixture implements ProjectWorkpiecePort, TransactionPort {
        private final List<String> events = new ArrayList<>();
        private final Map<String, ProjectWorkpieceRow> rows = new HashMap<>();
        private final ProjectWorkpiece projectWorkpiece = new ProjectWorkpiece(this, this);
        private final IllegalStateException failure = new IllegalStateException("failure");
        private List<ProjectWorkpieceRow> projectRows = List.of();
        private ProjectWorkpieceInput createdInput;
        private ProjectWorkpieceInput updatedInput;
        private String failureOn;

        private void record(String event) {
            events.add(event);
            if (event.equals(failureOn)) {
                throw failure;
            }
        }

        @Override
        public ProjectWorkpieceRow createAndFlush(ProjectWorkpieceInput input) {
            record("create");
            createdInput = input;
            return row("piece");
        }

        @Override
        public Optional<ProjectWorkpieceRow> updateAndFlush(
                String id, ProjectWorkpieceInput input
        ) {
            record("update:" + id);
            updatedInput = input;
            return Optional.ofNullable(rows.get(id));
        }

        @Override
        public boolean deleteAndFlush(String id) {
            record("delete:" + id);
            return rows.remove(id) != null;
        }

        @Override
        public List<ProjectWorkpieceRow> findByProjectOrderByName(String projectId) {
            record("findProject:" + projectId);
            return projectRows;
        }

        @Override
        public List<ProjectWorkpieceRow> findAllOrderByName() {
            record("findAll");
            return List.copyOf(rows.values());
        }

        @Override
        public void execute(Runnable operation) {
            record("write");
            operation.run();
        }

        @Override
        public <T> T executeWithResult(Supplier<T> operation) {
            record("writeResult");
            return operation.get();
        }

        @Override
        public <T> T executeReadOnly(Supplier<T> operation) {
            record("read");
            return operation.get();
        }
    }
}
