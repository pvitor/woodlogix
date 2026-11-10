package com.woodlogix.application.material;

import com.woodlogix.application.transaction.TransactionPort;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class MaterialCatalogTest {
    private static final BigDecimal ONE = BigDecimal.ONE;

    @Test
    void validatesEveryFieldAndLimits() {
        assertThrows(NullPointerException.class, () -> input(null, "C", ONE, ONE, ONE, ONE));
        assertThrows(IllegalArgumentException.class, () -> input(" ", "C", ONE, ONE, ONE, ONE));
        assertThrows(NullPointerException.class, () -> input("N", null, ONE, ONE, ONE, ONE));
        assertThrows(IllegalArgumentException.class, () -> input("N", "\t", ONE, ONE, ONE, ONE));
        for (int field = 0; field < 3; field++) {
            final int i = field;
            assertThrows(NullPointerException.class, () -> dimensions(i, null));
            assertThrows(IllegalArgumentException.class, () -> dimensions(i, BigDecimal.ZERO));
            assertThrows(IllegalArgumentException.class, () -> dimensions(i, ONE.negate()));
        }
        assertThrows(NullPointerException.class, () -> input("N", "C", ONE, ONE, ONE, null));
        assertThrows(IllegalArgumentException.class, () -> input("N", "C", ONE, ONE, ONE, ONE.negate()));
        assertEquals(BigDecimal.ZERO, input(" N ", " C ", ONE, ONE, ONE, BigDecimal.ZERO).pricePerSheet());
        assertEquals(" N ", input(" N ", " C ", ONE, ONE, ONE, ONE).name());
    }

    @Test
    void createsUpdatesAndListsThroughProperTransactions() {
        var fixture = new Fixture();
        var input = input("N", "C", ONE, ONE, ONE, BigDecimal.ZERO);
        assertEquals("id", fixture.catalog.create(input).id());
        assertEquals("id", fixture.catalog.update("id", input).id());
        var expected = List.of(row("A", "A", "C", ONE), row("B", "A", "C", BigDecimal.TEN),
                row("C", "A", "D", ONE), row("D", "B", "A", ONE));
        fixture.rows = expected;
        assertEquals(expected, fixture.catalog.findAll());
        assertEquals(List.of("write", "create", "write", "update", "read", "list"), fixture.events);
        assertEquals("error.material-not-found",
                assertThrows(MaterialNotFoundException.class, () -> fixture.catalog.update("missing", input)).getMessage());
    }

    @Test
    void deletesOnlyWhenUnreferencedAndInOrder() {
        var fixture = new Fixture();
        fixture.catalog.delete("id");
        assertEquals(List.of("write", "exists", "project", "draft", "delete"), fixture.events);
        fixture.events.clear();
        fixture.exists = false;
        assertThrows(MaterialNotFoundException.class, () -> fixture.catalog.delete("id"));
        assertEquals(List.of("write", "exists"), fixture.events);
        fixture.exists = true;
        fixture.project = true;
        fixture.events.clear();
        assertEquals("error.material-has-related-records",
                assertThrows(MaterialInUseException.class, () -> fixture.catalog.delete("id")).getMessage());
        assertEquals(List.of("write", "exists", "project"), fixture.events);
        fixture.project = false;
        fixture.draft = true;
        fixture.events.clear();
        assertEquals("error.material-has-related-records",
                assertThrows(MaterialInUseException.class, () -> fixture.catalog.delete("id")).getMessage());
        assertEquals(List.of("write", "exists", "project", "draft"), fixture.events);
    }

    @Test
    void propagatesPortFailures() {
        for (String stage : List.of("create", "update", "list", "exists", "project", "draft", "delete")) {
            var fixture = new Fixture();
            fixture.fail = stage;
            var input = input("N", "C", ONE, ONE, ONE, ONE);
            assertSame(fixture.failure, assertThrows(IllegalStateException.class, () -> {
                switch (stage) {
                    case "create" -> fixture.catalog.create(input);
                    case "update" -> fixture.catalog.update("id", input);
                    case "list" -> fixture.catalog.findAll();
                    default -> fixture.catalog.delete("id");
                }
            }));
        }
    }

    private static MaterialInput dimensions(int field, BigDecimal value) {
        return input("N", "C", field == 0 ? value : ONE, field == 1 ? value : ONE,
                field == 2 ? value : ONE, ONE);
    }

    private static MaterialInput input(String name, String color, BigDecimal thickness,
                                       BigDecimal length, BigDecimal width, BigDecimal price) {
        return new MaterialInput(name, color, thickness, length, width, price);
    }

    private static MaterialRow row(String id, String name, String color, BigDecimal thickness) {
        return new MaterialRow(id, name, color, thickness, ONE, ONE, ONE);
    }

    private static class Fixture implements MaterialCatalogPort, TransactionPort {
        final List<String> events = new ArrayList<>();
        final MaterialCatalog catalog = new MaterialCatalog(this, this);
        final IllegalStateException failure = new IllegalStateException("failure");
        boolean exists = true;
        boolean project;
        boolean draft;
        String fail;
        List<MaterialRow> rows = List.of();

        void record(String event) {
            events.add(event);
            if (event.equals(fail)) throw failure;
        }

        public MaterialRow create(MaterialInput input) {
            record("create");
            return row("id", input.name(), input.color(), input.thicknessMm());
        }
        public Optional<MaterialRow> update(String id, MaterialInput input) {
            record("update");
            return id.equals("missing") ? Optional.empty() : Optional.of(row(id, input.name(), input.color(), input.thicknessMm()));
        }
        public List<MaterialRow> findAllOrdered() { record("list"); return rows; }
        public boolean exists(String id) { record("exists"); return exists; }
        public boolean isReferencedByProjectWorkpiece(String id) { record("project"); return project; }
        public boolean isReferencedByDraftWorkpiece(String id) { record("draft"); return draft; }
        public void deleteAndFlush(String id) { record("delete"); }
        public void execute(Runnable operation) { record("write"); operation.run(); }
        public <T> T executeWithResult(Supplier<T> operation) { record("write"); return operation.get(); }
        public <T> T executeReadOnly(Supplier<T> operation) { record("read"); return operation.get(); }
    }
}
