package com.woodlogix.application.customer;

import com.woodlogix.application.transaction.TransactionPort;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class CustomerCatalogTest {
    @Test
    void validatesOnlyTheNameWithoutNormalizingIt() {
        assertThrows(NullPointerException.class, () -> new CustomerInput(null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new CustomerInput(" ", null, null, null));
        assertEquals(" N ", new CustomerInput(" N ", null, null, null).fullName());
        var lenient = new CustomerInput("N", "invalid phone", "invalid-email", null);
        assertEquals("invalid phone", lenient.phone());
        assertEquals("invalid-email", lenient.email());
        assertEquals("N", new CustomerRow("id", "N", null, null, null).toString());
    }

    @Test
    void createsAndUpdatesInsideWriteTransactionsAndNotifiesAfterPersisting() {
        var fixture = new Fixture();
        var input = new CustomerInput("N", null, null, null);
        assertEquals("id", fixture.catalog.create(input).id());
        assertEquals("id", fixture.catalog.update("id", input).id());
        assertEquals(List.of("write", "create", "notify", "write", "update", "notify"), fixture.events);
    }

    @Test
    void missingCustomerFailsWithoutNotifying() {
        var fixture = new Fixture();
        var input = new CustomerInput("N", null, null, null);
        assertEquals("error.customer-not-found", assertThrows(CustomerNotFoundException.class,
                () -> fixture.catalog.update("missing", input)).getMessage());
        assertEquals(List.of("write", "update"), fixture.events);
        fixture.events.clear();
        fixture.exists = false;
        assertThrows(CustomerNotFoundException.class, () -> fixture.catalog.delete("id"));
        assertEquals(List.of("write", "exists"), fixture.events);
    }

    @Test
    void deletesInOrderAndNotifiesAfterFlush() {
        var fixture = new Fixture();
        fixture.catalog.delete("id");
        assertEquals(List.of("write", "exists", "delete", "notify"), fixture.events);
    }

    @Test
    void readsTrimTermsAndFallBackToFindAllWhenBlank() {
        var fixture = new Fixture();
        for (String blank : new String[]{null, "", "  "}) {
            fixture.events.clear();
            fixture.catalog.search(blank);
            fixture.catalog.findByName(blank);
            assertEquals(List.of("read", "list", "read", "list"), fixture.events);
        }
        fixture.events.clear();
        fixture.catalog.search("  ana ");
        fixture.catalog.findByName(" bruno  ");
        assertEquals(List.of("read", "any:ana", "read", "name:bruno"), fixture.events);
    }

    @Test
    void propagatesFailuresAndNeverNotifiesBeforeThePersistenceStepSucceeds() {
        for (String stage : List.of("create", "update", "exists", "delete", "list", "any:x", "name:x")) {
            var fixture = new Fixture();
            fixture.fail = stage;
            var input = new CustomerInput("N", null, null, null);
            assertSame(fixture.failure, assertThrows(IllegalStateException.class, () -> {
                switch (stage) {
                    case "create" -> fixture.catalog.create(input);
                    case "update" -> fixture.catalog.update("id", input);
                    case "list" -> fixture.catalog.findAll();
                    case "any:x" -> fixture.catalog.search("x");
                    case "name:x" -> fixture.catalog.findByName("x");
                    default -> fixture.catalog.delete("id");
                }
            }), stage);
            assertFalse(fixture.events.contains("notify"), stage);
        }
        var fixture = new Fixture();
        fixture.fail = "notify";
        assertSame(fixture.failure, assertThrows(IllegalStateException.class,
                () -> fixture.catalog.delete("id")));
        assertEquals(List.of("write", "exists", "delete", "notify"), fixture.events);
    }

    private static class Fixture implements CustomerCatalogPort, CustomerChangeNotifier, TransactionPort {
        final List<String> events = new ArrayList<>();
        final CustomerCatalog catalog = new CustomerCatalog(this, this, this);
        final IllegalStateException failure = new IllegalStateException("failure");
        boolean exists = true;
        String fail;

        void record(String event) {
            events.add(event);
            if (event.equals(fail)) throw failure;
        }

        private static CustomerRow row(String id, CustomerInput input) {
            return new CustomerRow(id, input.fullName(), input.phone(), input.email(), input.address());
        }

        public CustomerRow create(CustomerInput input) { record("create"); return row("id", input); }
        public Optional<CustomerRow> update(String id, CustomerInput input) {
            record("update");
            return id.equals("missing") ? Optional.empty() : Optional.of(row(id, input));
        }
        public List<CustomerRow> findAll() { record("list"); return List.of(); }
        public List<CustomerRow> searchByAnyField(String term) { record("any:" + term); return List.of(); }
        public List<CustomerRow> searchByName(String name) { record("name:" + name); return List.of(); }
        public boolean exists(String id) { record("exists"); return exists; }
        public void deleteAndFlush(String id) { record("delete"); }
        public void customerChanged() { record("notify"); }
        public void execute(Runnable operation) { record("write"); operation.run(); }
        public <T> T executeWithResult(Supplier<T> operation) { record("write"); return operation.get(); }
        public <T> T executeReadOnly(Supplier<T> operation) { record("read"); return operation.get(); }
    }
}
