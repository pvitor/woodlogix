package com.woodlogix.desktop.persistence;

import com.woodlogix.application.edgebanding.EdgeBandingRow;
import com.woodlogix.application.material.MaterialRow;
import com.woodlogix.application.workpiece.ProjectWorkpiece;
import com.woodlogix.application.workpiece.ProjectWorkpieceInput;
import com.woodlogix.application.workpiece.ProjectWorkpiecePort;
import com.woodlogix.application.workpiece.ProjectWorkpieceRow;
import com.woodlogix.desktop.WoodLogixApplication;
import com.woodlogix.desktop.service.CustomerData;
import com.woodlogix.desktop.service.CustomerService;
import com.woodlogix.desktop.service.EdgeBandingData;
import com.woodlogix.desktop.service.EdgeBandingService;
import com.woodlogix.desktop.service.MaterialData;
import com.woodlogix.desktop.service.MaterialService;
import com.woodlogix.desktop.service.ProjectWorkpieceData;
import com.woodlogix.desktop.service.ProjectWorkpieceService;
import com.woodlogix.desktop.service.SalesPipelineService;
import com.woodlogix.desktop.service.SalesProjectData;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ProjectWorkpieceIntegrationTest {
    @TempDir
    Path temporaryDirectory;

    private ConfigurableApplicationContext context;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        context = new SpringApplicationBuilder(WoodLogixApplication.class)
                .web(WebApplicationType.NONE)
                .run("--spring.main.banner-mode=off", "--logging.level.root=ERROR",
                        "--logging.file.name=",
                        "--woodlogix.database.path=" + temporaryDirectory.resolve("workpieces.db"));
        jdbc = new JdbcTemplate(context.getBean(DataSource.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1' AND success = 1",
                Integer.class));
    }

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void createsUpdatesOrdersAndMapsCurrentCatalogRows() {
        var fixture = references();
        var service = context.getBean(ProjectWorkpieceService.class);
        var z = service.create(data(fixture.projectId(), "Z panel", fixture.materialId(),
                fixture.edgeBandingId(), true));
        var a = service.create(data(fixture.projectId(), "A shelf", fixture.materialId(),
                null, false));

        assertEquals(List.of("A shelf", "Z panel"),
                service.findByProject(fixture.projectId()).stream()
                        .map(ProjectWorkpieceRow::name).toList());
        var mapped = service.findByProject(fixture.projectId()).get(1);
        assertEquals(fixture.projectId(), mapped.projectId());
        assertEquals(fixture.materialId(), mapped.material().id());
        assertEquals("White", mapped.material().color());
        assertEquals(fixture.edgeBandingId(), mapped.edgeBanding().id());
        assertTrue(mapped.edgeTop());
        assertFalse(service.findByProject(fixture.projectId()).get(0).edgeTop());
        assertEquals(List.of("A shelf", "Z panel"),
                service.findAll().stream().map(ProjectWorkpieceRow::name).toList());

        var updated = service.update(z.id(), data(fixture.projectId(), "Updated panel",
                fixture.materialId(), fixture.edgeBandingId(), false));
        assertEquals(z.id(), updated.id());
        assertEquals("Updated panel", updated.name());
        assertFalse(updated.edgeTop());

        context.getBean(MaterialService.class).update(fixture.materialId(),
                new MaterialData("MDF updated", "Natural", bd("18"), bd("1000"),
                        bd("1000"), bd("240.00")));
        context.getBean(EdgeBandingService.class).update(fixture.edgeBandingId(),
                new EdgeBandingData("PVC updated", bd("22"), bd("5.00")));
        var current = service.findByProject(fixture.projectId()).stream()
                .filter(row -> row.id().equals(z.id())).findFirst().orElseThrow();
        assertEquals("MDF updated", current.material().name());
        assertEquals(0, bd("240.00").compareTo(current.material().pricePerSheet()));
        assertEquals("PVC updated", current.edgeBanding().name());
        assertEquals(0, bd("5.00").compareTo(current.edgeBanding().pricePerMeter()));

        service.delete(a.id());
        assertEquals(List.of(z.id()), service.findByProject(fixture.projectId()).stream()
                .map(ProjectWorkpieceRow::id).toList());
    }

    @Test
    void preservesValidationAndMissingReferenceExceptions() {
        var fixture = references();
        var service = context.getBean(ProjectWorkpieceService.class);
        assertThrows(ConstraintViolationException.class,
                () -> service.create(data(fixture.projectId(), "", fixture.materialId(), null, false)));

        assertNotFound("error.sales-project-not-found",
                () -> service.create(data("missing-project", "Panel",
                        fixture.materialId(), null, false)));
        assertNotFound("error.material-not-found",
                () -> service.create(data(fixture.projectId(), "Panel",
                        "missing-material", null, false)));
        assertNotFound("error.edge-banding-not-found",
                () -> service.create(data(fixture.projectId(), "Panel",
                        fixture.materialId(), "missing-edge", true)));
        var noEdge = service.create(data(fixture.projectId(), "Shelf",
                fixture.materialId(), " ", false));
        assertNull(noEdge.edgeBanding());
        assertEquals("edgeBanding must be configured when an edge is selected",
                assertThrows(IllegalArgumentException.class,
                        () -> service.create(data(fixture.projectId(), "Panel",
                                fixture.materialId(), " ", true))).getMessage());
        assertNotFound("error.workpiece-not-found",
                () -> service.update("missing-piece", data(fixture.projectId(), "Panel",
                        fixture.materialId(), null, false)));
        assertNotFound("error.workpiece-not-found", () -> service.delete("missing-piece"));
        assertEquals(List.of(noEdge.id()), service.findAll().stream()
                .map(ProjectWorkpieceRow::id).toList());
    }

    @Test
    void rollsBackCreateAfterAdapterFlushFailure() {
        var fixture = references();
        var service = context.getBean(ProjectWorkpieceService.class);
        var useCase = context.getBean(ProjectWorkpiece.class);
        var real = context.getBean(ProjectWorkpiecePort.class);
        var intercept = mock(ProjectWorkpiecePort.class, delegatesTo(real));
        var failure = new IllegalStateException("failure after flush");
        var before = rows("project_workpieces");
        doAnswer(invocation -> {
            real.createAndFlush(invocation.getArgument(0));
            throw failure;
        }).when(intercept).createAndFlush(any(ProjectWorkpieceInput.class));
        ReflectionTestUtils.setField(useCase, "workpieces", intercept);
        try {
            assertSame(failure, assertThrows(IllegalStateException.class,
                    () -> service.create(data(fixture.projectId(), "Panel",
                            fixture.materialId(), null, false))));
        } finally {
            ReflectionTestUtils.setField(useCase, "workpieces", real);
        }

        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertEquals(before, rows("project_workpieces"));
    }

    @Test
    void translatesIntegrityFailureAfterFlushedDeleteAndRollsBack() {
        var fixture = references();
        var service = context.getBean(ProjectWorkpieceService.class);
        var workpiece = service.create(data(fixture.projectId(), "Panel",
                fixture.materialId(), null, false));
        var useCase = context.getBean(ProjectWorkpiece.class);
        var real = context.getBean(ProjectWorkpiecePort.class);
        var intercept = mock(ProjectWorkpiecePort.class, delegatesTo(real));
        var failure = new DataIntegrityViolationException("simulated integrity failure");
        var before = rows("project_workpieces");
        doAnswer(invocation -> {
            real.deleteAndFlush(invocation.getArgument(0));
            throw failure;
        }).when(intercept).deleteAndFlush(anyString());
        ReflectionTestUtils.setField(useCase, "workpieces", intercept);
        try {
            var translated = assertThrows(IllegalStateException.class,
                    () -> service.delete(workpiece.id()));
            assertEquals("error.workpiece-has-related-records", translated.getMessage());
            assertSame(failure, translated.getCause());
        } finally {
            ReflectionTestUtils.setField(useCase, "workpieces", real);
        }

        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertEquals(before, rows("project_workpieces"));
    }

    @Test
    void propagatesNonIntegrityFailureAfterFlushedDeleteAndRollsBack() {
        var fixture = references();
        var service = context.getBean(ProjectWorkpieceService.class);
        var workpiece = service.create(data(fixture.projectId(), "Panel",
                fixture.materialId(), null, false));
        var useCase = context.getBean(ProjectWorkpiece.class);
        var real = context.getBean(ProjectWorkpiecePort.class);
        var intercept = mock(ProjectWorkpiecePort.class, delegatesTo(real));
        var failure = new IllegalStateException("non-integrity failure");
        var before = rows("project_workpieces");
        doAnswer(invocation -> {
            real.deleteAndFlush(invocation.getArgument(0));
            throw failure;
        }).when(intercept).deleteAndFlush(anyString());
        ReflectionTestUtils.setField(useCase, "workpieces", intercept);
        try {
            assertSame(failure, assertThrows(IllegalStateException.class,
                    () -> service.delete(workpiece.id())));
        } finally {
            ReflectionTestUtils.setField(useCase, "workpieces", real);
        }

        assertEquals(before, rows("project_workpieces"));
    }

    private ReferenceFixture references() {
        var customer = context.getBean(CustomerService.class)
                .create(new CustomerData("Workpiece customer", null, null, null));
        var project = context.getBean(SalesPipelineService.class)
                .create(new SalesProjectData(customer.id(), null, "Project", bd("100")));
        MaterialRow material = context.getBean(MaterialService.class)
                .create(new MaterialData("MDF", "White", bd("18"), bd("1000"),
                        bd("1000"), bd("120.00")));
        EdgeBandingRow edgeBanding = context.getBean(EdgeBandingService.class)
                .create(new EdgeBandingData("PVC", bd("22"), bd("2.50")));
        return new ReferenceFixture(project.id(), material.id(), edgeBanding.id());
    }

    private List<Map<String, Object>> rows(String table) {
        return jdbc.queryForList("SELECT * FROM " + table + " ORDER BY id");
    }

    private static ProjectWorkpieceData data(String projectId, String name, String materialId,
                                             String edgeBandingId, boolean edgeTop) {
        return new ProjectWorkpieceData(projectId, name, bd("100"), bd("200"), 2,
                materialId, edgeBandingId, edgeTop, false, false, false);
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private static void assertNotFound(String message, Runnable operation) {
        assertEquals(message, assertThrows(EntityNotFoundException.class, operation::run).getMessage());
    }

    private record ReferenceFixture(String projectId, String materialId, String edgeBandingId) {
    }
}
