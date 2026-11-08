package com.woodlogix.application.hardware;

import com.woodlogix.application.transaction.TransactionPort;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HardwareCatalogTest {
    @Test
    void createsAndUpdatesInsideWriteTransactions() {
        var fixture = new Fixture();
        var input = new HardwareInput(" Hinge ", "PAIR", new BigDecimal("12.34"));
        assertEquals(new HardwareRow("id", " Hinge ", "PAIR", new BigDecimal("12.34")),
                fixture.catalog.create(input));
        assertEquals(new HardwareRow("id", " Hinge ", "PAIR", new BigDecimal("12.34")),
                fixture.catalog.update("id", input));
        assertEquals(List.of("write", "create", "write", "update:id"), fixture.calls);
    }

    @Test
    void validatesRequiredValuesAndAllowsZero() {
        assertThrows(NullPointerException.class, () -> new HardwareInput(null, "U", BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new HardwareInput(" \t", "U", BigDecimal.ZERO));
        assertThrows(NullPointerException.class, () -> new HardwareInput("H", null, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new HardwareInput("H", " ", BigDecimal.ZERO));
        assertThrows(NullPointerException.class, () -> new HardwareInput("H", "U", null));
        assertThrows(IllegalArgumentException.class, () -> new HardwareInput("H", "U", new BigDecimal("-0.01")));
        assertEquals(BigDecimal.ZERO, new HardwareInput("H", "U", BigDecimal.ZERO).unitPrice());
    }

    @Test
    void rejectsMissingUpdate() {
        var fixture = new Fixture();
        var failure = assertThrows(HardwareNotFoundException.class, () ->
                fixture.catalog.update("missing", new HardwareInput("H", "U", BigDecimal.ZERO)));
        assertEquals("error.hardware-not-found", failure.getMessage());
        assertEquals(List.of("write", "update:missing"), fixture.calls);
    }

    @Test
    void listsInPortOrderInsideReadOnlyTransaction() {
        var fixture = new Fixture();
        var expected = List.of(new HardwareRow("1", "A", "U", BigDecimal.ZERO),
                new HardwareRow("2", "Z", "U", BigDecimal.ONE));
        fixture.rows = expected;
        assertEquals(expected, fixture.catalog.findAll());
        assertEquals(List.of("read-only", "list"), fixture.calls);
    }

    @Test
    void propagatesPersistenceFailures() {
        for (String stage : List.of("create", "update", "list")) {
            var fixture = new Fixture();
            fixture.failedStage = stage;
            var input = new HardwareInput("H", "U", BigDecimal.ZERO);
            var failure = assertThrows(IllegalStateException.class, () -> {
                switch (stage) {
                    case "create" -> fixture.catalog.create(input);
                    case "update" -> fixture.catalog.update("id", input);
                    case "list" -> fixture.catalog.findAll();
                    default -> throw new AssertionError(stage);
                }
            });
            assertSame(fixture.failure, failure);
        }
    }

    private static class Fixture implements HardwareCatalogPort, TransactionPort {
        private final List<String> calls = new ArrayList<>();
        private final HardwareCatalog catalog = new HardwareCatalog(this, this);
        private final IllegalStateException failure = new IllegalStateException("persistence failure");
        private String failedStage;
        private List<HardwareRow> rows = List.of();

        @Override
        public HardwareRow create(HardwareInput input) {
            record("create");
            return new HardwareRow("id", input.name(), input.unit(), input.unitPrice());
        }

        @Override
        public Optional<HardwareRow> update(String id, HardwareInput input) {
            record("update:" + id);
            return id.equals("missing") ? Optional.empty()
                    : Optional.of(new HardwareRow(id, input.name(), input.unit(), input.unitPrice()));
        }

        @Override
        public List<HardwareRow> findAllByName() {
            record("list");
            return rows;
        }

        private void record(String call) {
            calls.add(call);
            if (call.equals(failedStage) || call.startsWith(failedStage == null ? "\0" : failedStage + ":")) {
                throw failure;
            }
        }

        @Override
        public void execute(Runnable operation) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T executeWithResult(Supplier<T> operation) {
            calls.add("write");
            return operation.get();
        }

        @Override
        public <T> T executeReadOnly(Supplier<T> operation) {
            calls.add("read-only");
            return operation.get();
        }
    }
}
