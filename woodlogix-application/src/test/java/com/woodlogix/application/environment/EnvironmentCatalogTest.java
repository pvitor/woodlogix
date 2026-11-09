package com.woodlogix.application.environment;

import com.woodlogix.application.customer.CustomerNotFoundException;
import com.woodlogix.application.transaction.TransactionPort;
import com.woodlogix.core.enums.RoomType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class EnvironmentCatalogTest {
    @Test
    void listsDistinctRowsByCustomerAndNormalizedTypeInsideReadOnlyTransactions() {
        var fixture = new Fixture();
        fixture.add("c1", "Home Office");
        fixture.add("c1", "home_office");
        fixture.add("c1", "Kitchen");
        fixture.add("c2", "HOME-OFFICE");
        assertEquals(List.of("Home Office", "Kitchen", "HOME-OFFICE"),
                types(fixture.catalog.findAll()));
        assertEquals(List.of("Home Office", "Kitchen"), types(fixture.catalog.findByCustomer("c1")));
        assertEquals(List.of("read", "listAll", "read", "byCustomer:c1"), fixture.events);
    }

    @Test
    void createsInsideWriteTransactionAndNotifiesAfterFlush() {
        var fixture = new Fixture();
        assertEquals("Kitchen", fixture.catalog.create("c1", "Kitchen").type());
        assertEquals(List.of("write", "customerExists", "create:c1:Kitchen", "notify"), fixture.events);
        fixture.events.clear();
        assertEquals("error.customer-not-found", assertThrows(CustomerNotFoundException.class,
                () -> fixture.catalog.create("missing", "Kitchen")).getMessage());
        assertEquals(List.of("write", "customerExists"), fixture.events);
    }

    @Test
    void createsOnlyMissingTypesAndIgnoresDuplicatesAgainstSelectionCustomTypeAndExistingRooms() {
        var fixture = new Fixture();
        fixture.add("c1", "bathroom");
        fixture.add("c1", "My Studio");
        var result = fixture.catalog.createForCustomer("c1",
                List.of(RoomType.BATHROOM, RoomType.KITCHEN, RoomType.KITCHEN), "  my studio  ");
        assertEquals(List.of("KITCHEN"), types(result));
        assertEquals(List.of("write", "customerExists", "byCustomer:c1", "createAll:c1:[KITCHEN]", "notify"),
                fixture.events);
    }

    @Test
    void createForCustomerAllowsCustomTypeAloneAndRejectsBlankOrDuplicateOfSelected() {
        var fixture = new Fixture();
        assertEquals(List.of("Studio"), types(fixture.catalog.createForCustomer(
                "c1", null, "Studio")));
        assertEquals("error.environment-type", assertThrows(IllegalArgumentException.class,
                () -> fixture.catalog.createForCustomer("c1", null, null)).getMessage());
        assertEquals("error.environment-type", assertThrows(IllegalArgumentException.class,
                () -> fixture.catalog.createForCustomer("c1", List.of(), "   ")).getMessage());

        fixture.events.clear();
        fixture.add("c1", "KITCHEN");
        assertTrue(fixture.catalog.createForCustomer("c1", List.of(RoomType.KITCHEN), "kitchen").isEmpty());
        assertTrue(fixture.events.stream().noneMatch("notify"::equals));
    }

    @Test
    void synchronizesToAnExactDeduplicatedListInTheExpectedOrder() {
        var fixture = new Fixture();
        fixture.add("c1", "Kitchen");
        fixture.add("c1", "kitchen");
        fixture.add("c1", "Bedroom");
        var result = fixture.catalog.saveOrUpdateCustomerEnvironments("c1",
                List.of(" KITCHEN ", "Pantry", "pantry", "Kitchen"));
        assertEquals(List.of("write", "customerExists", "byCustomer:c1",
                "refs:part:e2", "refs:item:e2", "refs:sales:e2",
                "refs:part:e3", "refs:item:e3", "refs:sales:e3",
                "deleteAll:[e2, e3]", "rename:e1:KITCHEN", "createAll:c1:[Pantry]", "notify",
                "byCustomer:c1"), fixture.events);
        assertEquals(List.of("KITCHEN", "Pantry"), types(result));
    }

    @Test
    void doesNotRenameWhenTheTextIsAlreadyTheRequestedOne() {
        var fixture = new Fixture();
        fixture.add("c1", "Kitchen");
        fixture.add("c1", "Bedroom");
        fixture.catalog.saveOrUpdateCustomerEnvironments("c1", List.of("Kitchen", "Pantry"));
        assertTrue(fixture.events.stream().noneMatch(event -> event.startsWith("rename")));
        assertEquals(List.of("Kitchen", "Pantry"), types(fixture.rows));
    }

    @Test
    void synchronizationWithoutChangesReturnsExistingRowsWithoutCheckingOrNotifying() {
        var fixture = new Fixture();
        fixture.add("c1", "Kitchen");
        var result = fixture.catalog.saveOrUpdateCustomerEnvironments("c1", List.of("Kitchen"));
        assertEquals(List.of("Kitchen"), types(result));
        assertEquals(List.of("write", "customerExists", "byCustomer:c1"), fixture.events);
    }

    @Test
    void synchronizationExecutesActionOnlyWhenChangesOccur() {
        var fixture = new Fixture();
        fixture.add("c1", "Kitchen");
        var run = new java.util.concurrent.atomic.AtomicBoolean();
        fixture.catalog.saveOrUpdateCustomerEnvironments("c1", List.of("Kitchen"), () -> run.set(true));
        assertFalse(run.get(), "Action must not execute on early return");

        fixture.catalog.saveOrUpdateCustomerEnvironments("c1", List.of("Pantry"), () -> run.set(true));
        assertTrue(run.get(), "Action must execute when environments change");
    }

    @Test
    void synchronizationRejectsUnknownCustomerBeforeAnyReadOrWrite() {
        var fixture = new Fixture();
        assertThrows(CustomerNotFoundException.class,
                () -> fixture.catalog.saveOrUpdateCustomerEnvironments("missing", List.of("Kitchen")));
        assertEquals(List.of("write", "customerExists"), fixture.events);
    }

    @Test
    void anyKindOfReferenceBlocksSynchronizationBeforeAnyChange() {
        var expectedChecks = java.util.Map.of(
                "part", List.of("refs:part:e2"),
                "item", List.of("refs:part:e2", "refs:item:e2"),
                "sales", List.of("refs:part:e2", "refs:item:e2", "refs:sales:e2"));
        for (var kind : List.of("part", "item", "sales")) {
            var fixture = new Fixture();
            fixture.add("c1", "Kitchen");
            fixture.add("c1", "Bedroom");
            fixture.referenced.add(kind + ":e2");
            var failure = assertThrows(IllegalStateException.class,
                    () -> fixture.catalog.saveOrUpdateCustomerEnvironments("c1", List.of("kitchen", "Pantry")));
            assertEquals("error.environment-has-related-records", failure.getMessage(), kind);
            assertNull(failure.getCause());
            assertEquals(expectedChecks.get(kind), fixture.events.subList(3, fixture.events.size()), kind);
            assertTrue(fixture.events.stream().noneMatch(event -> event.startsWith("deleteAll")
                    || event.startsWith("rename") || event.startsWith("createAll") || event.equals("notify")), kind);
            assertEquals(List.of("Kitchen", "Bedroom"), types(fixture.rows), kind);
        }
    }

    @Test
    void referencesOfKeptEnvironmentsDoNotBlockRemovalOfOthers() {
        var fixture = new Fixture();
        fixture.add("c1", "Kitchen");
        fixture.add("c1", "Bedroom");
        fixture.referenced.addAll(List.of("part:e1", "item:e1", "sales:e1"));
        fixture.catalog.saveOrUpdateCustomerEnvironments("c1", List.of("Kitchen"));
        assertEquals(List.of("Kitchen"), types(fixture.rows));
        assertTrue(fixture.events.contains("notify"));
    }

    @Test
    void deletesAllEnvironmentsOfCustomerInASingleWriteTransaction() {
        var fixture = new Fixture();
        fixture.add("c1", "Kitchen");
        fixture.add("c2", "Kitchen");
        fixture.catalog.deleteAllByCustomer("c1");
        assertEquals(1, fixture.events.stream().filter("write"::equals).count());
        assertEquals(List.of("c2"), fixture.rows.stream().map(EnvironmentRow::customerId).toList());
        assertEquals("notify", fixture.events.get(fixture.events.size() - 2));
        assertThrows(CustomerNotFoundException.class, () -> fixture.catalog.deleteAllByCustomer("missing"));
    }

    @Test
    void updatesInOrderAndNotifiesOnlyAfterPersisting() {
        var fixture = new Fixture();
        fixture.add("c1", "Kitchen");
        assertEquals("Pantry", fixture.catalog.update("e1", "c1", "Pantry").type());
        assertEquals(List.of("write", "exists", "customerExists", "update:e1:c1:Pantry", "notify"),
                fixture.events);

        fixture.events.clear();
        assertEquals("error.environment-not-found", assertThrows(EnvironmentNotFoundException.class,
                () -> fixture.catalog.update("missing", "c1", "Pantry")).getMessage());
        assertEquals(List.of("write", "exists"), fixture.events);
        fixture.events.clear();
        assertThrows(CustomerNotFoundException.class, () -> fixture.catalog.update("e1", "missing", "Pantry"));
        assertEquals(List.of("write", "exists", "customerExists"), fixture.events);
    }

    @Test
    void deletesInOrderAndRejectsMissingEnvironmentWithoutNotifying() {
        var fixture = new Fixture();
        fixture.add("c1", "Kitchen");
        fixture.catalog.delete("e1");
        assertEquals(List.of("write", "exists", "delete:e1", "notify"), fixture.events);
        fixture.events.clear();
        assertThrows(EnvironmentNotFoundException.class, () -> fixture.catalog.delete("e1"));
        assertEquals(List.of("write", "exists"), fixture.events);
    }

    @Test
    void propagatesTheSameFailureAndNeverNotifiesWhenPersistenceFails() {
        for (var stage : List.of("create:c1:Kitchen", "createAll:c1:[HOME_OFFICE]", "update:e1:c1:Pantry",
                "delete:e1", "deleteAll:[e2]", "rename:e1:KITCHEN", "listAll", "byCustomer:c1",
                "refs:part:e2", "refs:item:e2", "refs:sales:e2")) {
            var fixture = new Fixture();
            fixture.add("c1", "Kitchen");
            fixture.add("c1", "Bedroom");
            fixture.fail = stage;
            assertSame(fixture.failure, assertThrows(IllegalStateException.class, () -> {
                switch (stage.split(":")[0]) {
                    case "create" -> fixture.catalog.create("c1", "Kitchen");
                    case "createAll" -> fixture.catalog.createForCustomer("c1", List.of(RoomType.HOME_OFFICE), null);
                    case "update" -> fixture.catalog.update("e1", "c1", "Pantry");
                    case "delete" -> fixture.catalog.delete("e1");
                    case "listAll" -> fixture.catalog.findAll();
                    case "byCustomer" -> fixture.catalog.findByCustomer("c1");
                    default -> fixture.catalog.saveOrUpdateCustomerEnvironments("c1", List.of("KITCHEN"));
                }
            }), stage);
            assertFalse(fixture.events.contains("notify"), stage);
        }
        var fixture = new Fixture();
        fixture.add("c1", "Kitchen");
        fixture.fail = "notify";
        assertSame(fixture.failure, assertThrows(IllegalStateException.class,
                () -> fixture.catalog.delete("e1")));
    }

    private static List<String> types(List<EnvironmentRow> rows) {
        return rows.stream().map(EnvironmentRow::type).toList();
    }

    private static class Fixture implements EnvironmentCatalogPort, EnvironmentChangeNotifier, TransactionPort {
        final List<String> events = new ArrayList<>();
        final List<EnvironmentRow> rows = new ArrayList<>();
        final Set<String> referenced = new java.util.HashSet<>();
        final EnvironmentCatalog catalog = new EnvironmentCatalog(this, this, this);
        final IllegalStateException failure = new IllegalStateException("failure");
        String fail;
        int sequence;

        void add(String customerId, String type) {
            rows.add(new EnvironmentRow("e" + ++sequence, customerId, "Customer " + customerId, type));
        }

        void record(String event) {
            events.add(event);
            if (event.equals(fail)) throw failure;
        }

        private EnvironmentRow stored(String customerId, String type) {
            var row = new EnvironmentRow("e" + ++sequence, customerId, "Customer " + customerId, type);
            rows.add(row);
            return row;
        }

        public boolean customerExists(String customerId) {
            events.add("customerExists");
            return !customerId.equals("missing");
        }
        public boolean exists(String id) {
            events.add("exists");
            return rows.stream().anyMatch(row -> row.id().equals(id));
        }
        public List<EnvironmentRow> findAllOrderedByType() { record("listAll"); return List.copyOf(rows); }
        public List<EnvironmentRow> findByCustomer(String customerId) {
            record("byCustomer:" + customerId);
            return rows.stream().filter(row -> row.customerId().equals(customerId)).toList();
        }
        public EnvironmentRow createAndFlush(String customerId, String type) {
            record("create:" + customerId + ":" + type);
            return stored(customerId, type);
        }
        public List<EnvironmentRow> createAllAndFlush(String customerId, List<String> types) {
            record("createAll:" + customerId + ":" + types);
            return types.stream().map(type -> stored(customerId, type)).toList();
        }
        public Optional<EnvironmentRow> updateAndFlush(String id, String customerId, String type) {
            record("update:" + id + ":" + customerId + ":" + type);
            replace(id, customerId, type);
            return Optional.of(rows.stream().filter(row -> row.id().equals(id)).findFirst().orElseThrow());
        }
        public EnvironmentRow renameAndSave(String id, String type) {
            record("rename:" + id + ":" + type);
            var current = rows.stream().filter(row -> row.id().equals(id)).findFirst().orElseThrow();
            replace(id, current.customerId(), type);
            return rows.stream().filter(row -> row.id().equals(id)).findFirst().orElseThrow();
        }
        private void replace(String id, String customerId, String type) {
            rows.replaceAll(row -> row.id().equals(id)
                    ? new EnvironmentRow(id, customerId, "Customer " + customerId, type) : row);
        }
        public boolean hasPartReferences(String id) { record("refs:part:" + id); return referenced.contains("part:" + id); }
        public boolean hasFurnitureItemReferences(String id) { record("refs:item:" + id); return referenced.contains("item:" + id); }
        public boolean hasSalesProjectReferences(String id) { record("refs:sales:" + id); return referenced.contains("sales:" + id); }
        public void deleteAndFlush(String id) {
            record("delete:" + id);
            rows.removeIf(row -> row.id().equals(id));
        }
        public void deleteAllAndFlush(List<String> ids) {
            record("deleteAll:" + ids);
            rows.removeIf(row -> ids.contains(row.id()));
        }
        public void environmentChanged() { record("notify"); }
        public void execute(Runnable operation) { record("write"); operation.run(); }
        public <T> T executeWithResult(Supplier<T> operation) { record("write"); return operation.get(); }
        public <T> T executeReadOnly(Supplier<T> operation) { record("read"); return operation.get(); }
    }
}
