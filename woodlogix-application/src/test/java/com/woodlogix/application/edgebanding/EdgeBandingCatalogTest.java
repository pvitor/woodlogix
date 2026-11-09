package com.woodlogix.application.edgebanding;

import com.woodlogix.application.transaction.TransactionPort;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class EdgeBandingCatalogTest {
    private static final BigDecimal ONE = BigDecimal.ONE;

    @Test
    void validatesEveryFieldWithoutNormalizingText() {
        assertThrows(NullPointerException.class, () -> input(null, ONE, ONE));
        assertThrows(IllegalArgumentException.class, () -> input(" ", ONE, ONE));
        assertThrows(NullPointerException.class, () -> input("N", null, ONE));
        assertThrows(IllegalArgumentException.class, () -> input("N", BigDecimal.ZERO, ONE));
        assertThrows(IllegalArgumentException.class, () -> input("N", ONE.negate(), ONE));
        assertThrows(NullPointerException.class, () -> input("N", ONE, null));
        assertThrows(IllegalArgumentException.class, () -> input("N", ONE, ONE.negate()));
        assertEquals(BigDecimal.ZERO, input("N", ONE, BigDecimal.ZERO).pricePerMeter());
        assertEquals(" N ", input(" N ", ONE, ONE).name());
    }

    @Test
    void createsUpdatesAndListsThroughProperTransactions() {
        var fixture = new Fixture();
        var input = input("N", ONE, BigDecimal.ZERO);
        assertEquals("id", fixture.catalog.create(input).id());
        assertEquals("id", fixture.catalog.update("id", input).id());
        var expected = List.of(row("A", "A", ONE), row("B", "A", BigDecimal.TEN), row("C", "B", ONE));
        fixture.rows = expected;
        assertSame(expected, fixture.catalog.findAll());
        assertEquals(List.of("write", "create", "write", "update", "read", "list"), fixture.events);
        assertEquals("error.edge-banding-not-found", assertThrows(EdgeBandingNotFoundException.class,
                () -> fixture.catalog.update("missing", input)).getMessage());
    }

    @Test
    void deletesOnlyWhenUnreferencedAndInOrder() {
        var fixture = new Fixture();
        fixture.catalog.delete("id");
        assertEquals(List.of("write", "exists", "project", "draft", "delete"), fixture.events);
        fixture.events.clear();
        fixture.exists = false;
        assertThrows(EdgeBandingNotFoundException.class, () -> fixture.catalog.delete("id"));
        assertEquals(List.of("write", "exists"), fixture.events);
        fixture.exists = true;
        fixture.project = true;
        fixture.events.clear();
        assertEquals("error.edge-banding-has-related-records",
                assertThrows(IllegalStateException.class, () -> fixture.catalog.delete("id")).getMessage());
        assertEquals(List.of("write", "exists", "project"), fixture.events);
        fixture.project = false;
        fixture.draft = true;
        fixture.events.clear();
        assertEquals("error.edge-banding-has-related-records",
                assertThrows(IllegalStateException.class, () -> fixture.catalog.delete("id")).getMessage());
        assertEquals(List.of("write", "exists", "project", "draft"), fixture.events);
    }

    @Test
    void propagatesPortFailures() {
        for (String stage : List.of("create", "update", "list", "exists", "project", "draft", "delete")) {
            var fixture = new Fixture();
            fixture.fail = stage;
            var input = input("N", ONE, ONE);
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

    private static EdgeBandingInput input(String name, BigDecimal width, BigDecimal price) {
        return new EdgeBandingInput(name, width, price);
    }

    private static EdgeBandingRow row(String id, String name, BigDecimal width) {
        return new EdgeBandingRow(id, name, width, ONE);
    }

    private static class Fixture implements EdgeBandingCatalogPort, TransactionPort {
        final List<String> events = new ArrayList<>();
        final EdgeBandingCatalog catalog = new EdgeBandingCatalog(this, this);
        final IllegalStateException failure = new IllegalStateException("failure");
        boolean exists = true;
        boolean project;
        boolean draft;
        String fail;
        List<EdgeBandingRow> rows = List.of();

        void record(String event) {
            events.add(event);
            if (event.equals(fail)) throw failure;
        }

        public EdgeBandingRow create(EdgeBandingInput input) {
            record("create");
            return row("id", input.name(), input.widthMm());
        }
        public Optional<EdgeBandingRow> update(String id, EdgeBandingInput input) {
            record("update");
            return id.equals("missing") ? Optional.empty() : Optional.of(row(id, input.name(), input.widthMm()));
        }
        public List<EdgeBandingRow> findAllOrdered() { record("list"); return rows; }
        public boolean exists(String id) { record("exists"); return exists; }
        public boolean isReferencedByProjectWorkpiece(String id) { record("project"); return project; }
        public boolean isReferencedByDraftWorkpiece(String id) { record("draft"); return draft; }
        public void deleteAndFlush(String id) { record("delete"); }
        public void execute(Runnable operation) { record("write"); operation.run(); }
        public <T> T executeWithResult(Supplier<T> operation) { record("write"); return operation.get(); }
        public <T> T executeReadOnly(Supplier<T> operation) { record("read"); return operation.get(); }
    }
}
