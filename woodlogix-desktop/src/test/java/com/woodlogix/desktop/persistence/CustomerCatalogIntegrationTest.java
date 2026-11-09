package com.woodlogix.desktop.persistence;

import com.woodlogix.application.customer.CustomerCatalog;
import com.woodlogix.application.customer.CustomerCatalogPort;
import com.woodlogix.application.customer.CustomerInput;
import com.woodlogix.application.customer.CustomerRow;
import com.woodlogix.desktop.WoodLogixApplication;
import com.woodlogix.desktop.persistence.entity.CustomerEntity;
import com.woodlogix.desktop.persistence.entity.FurnitureItemEntity;
import com.woodlogix.desktop.persistence.repository.CustomerRepository;
import com.woodlogix.desktop.persistence.repository.FurnitureItemRepository;
import com.woodlogix.desktop.service.CustomerData;
import com.woodlogix.desktop.service.CustomerService;
import com.woodlogix.desktop.service.EnvironmentData;
import com.woodlogix.desktop.service.EnvironmentService;
import com.woodlogix.desktop.service.SalesPipelineService;
import com.woodlogix.desktop.service.SalesProjectData;
import com.woodlogix.desktop.service.event.CustomerChangedEvent;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteErrorCode;
import org.sqlite.SQLiteException;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class CustomerCatalogIntegrationTest {
    @TempDir Path temporaryDirectory;
    private ConfigurableApplicationContext context;
    private JdbcTemplate jdbc;
    private Path databasePath;
    private AfterCommitRecorder recorder;

    @BeforeEach
    void setUp() {
        databasePath = temporaryDirectory.resolve("customers.db");
        context = new SpringApplicationBuilder(WoodLogixApplication.class, AfterCommitRecorder.class)
                .web(WebApplicationType.NONE).run("--spring.main.banner-mode=off", "--logging.level.root=ERROR",
                        "--logging.file.name=", "--woodlogix.database.path=" + databasePath);
        jdbc = new JdbcTemplate(context.getBean(DataSource.class));
        recorder = context.getBean(AfterCommitRecorder.class);
        recorder.databasePath = databasePath;
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1' AND success = 1", Integer.class));
    }

    @AfterEach
    void tearDown() {
        if (context != null) context.close();
    }

    @Test
    void persistsUpdatesAndSearchesCustomers() {
        var service = context.getBean(CustomerService.class);
        var ana = service.create(new CustomerData("Ana Carvalho", "11999991111", "ana@example.com", "Street 1"));
        var bruno = service.create(new CustomerData("Bruno Lima", "21988887777", "bruno@wood.test", null));
        assertNotNull(ana.id());
        assertEquals("Ana Carvalho", ana.toString());
        assertEquals(2, service.findAll().size());
        assertEquals(List.of(ana.id()), ids(service.search("  CARVALHO ")));
        assertEquals(List.of(bruno.id()), ids(service.search("BRUNO@WOOD")));
        assertEquals(List.of(bruno.id()), ids(service.search("8888")));
        assertEquals(List.of(ana.id()), ids(service.findByName(" carvalho ")));
        assertTrue(service.findByName("8888").isEmpty());
        assertEquals(2, service.search(" ").size());
        assertEquals(2, service.findByName(null).size());

        var updated = service.update(ana.id(), new CustomerData("Ana Souza", null, null, null));
        assertEquals(ana.id(), updated.id());
        var persisted = jdbc.queryForMap("SELECT * FROM customers WHERE id = ?", ana.id());
        assertEquals("Ana Souza", persisted.get("full_name"));
        assertNull(persisted.get("phone"));
        assertNull(persisted.get("email"));
        assertNull(persisted.get("address"));
        service.delete(bruno.id());
        assertEquals(List.of(ana.id()), ids(service.findAll()));
    }

    @Test
    void validatesFacadeAndMissingIdsWithoutPersistingOrNotifying() {
        var service = context.getBean(CustomerService.class);
        for (var invalid : List.of(new CustomerData(" ", null, null, null),
                new CustomerData("N", "invalid phone", null, null),
                new CustomerData("N", null, "invalid-email", null))) {
            assertThrows(ConstraintViolationException.class, () -> service.create(invalid));
        }
        assertEquals("error.customer-not-found", assertThrows(EntityNotFoundException.class,
                () -> service.update("missing", new CustomerData("N", null, null, null))).getMessage());
        assertEquals("error.customer-not-found", assertThrows(EntityNotFoundException.class,
                () -> service.delete("missing")).getMessage());
        assertTrue(rows("customers").isEmpty());
        assertTrue(recorder.events.isEmpty());
    }

    @Test
    void afterCommitListenersRunOnlyAfterCommitForEachOperation() {
        var service = context.getBean(CustomerService.class);
        var customer = service.create(new CustomerData("First", null, null, null));
        assertEquals(List.of("customers=First"), recorder.events);
        service.update(customer.id(), new CustomerData("Second", null, null, null));
        assertEquals(List.of("customers=First", "customers=Second"), recorder.events);
        service.delete(customer.id());
        assertEquals(List.of("customers=First", "customers=Second", "customers="), recorder.events);
        assertTrue(service.findAll().isEmpty());
        assertEquals(3, recorder.events.size());
    }

    @Test
    void externalTransactionDefersNotificationsUntilItCommits() {
        var service = context.getBean(CustomerService.class);
        var template = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        template.executeWithoutResult(status -> {
            var customer = service.create(new CustomerData("Joined", null, null, null));
            service.update(customer.id(), new CustomerData("Joined again", null, null, null));
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertTrue(recorder.events.isEmpty());
        });
        assertEquals(List.of("customers=Joined again", "customers=Joined again"), recorder.events);
    }

    @Test
    void externalTransactionRollbackRevertsDataWithoutNotifications() {
        var service = context.getBean(CustomerService.class);
        var existing = service.create(new CustomerData("Existing", null, null, null));
        recorder.events.clear();
        var before = rows("customers");
        new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).executeWithoutResult(status -> {
            service.create(new CustomerData("Discarded", null, null, null));
            service.update(existing.id(), new CustomerData("Changed", null, null, null));
            assertEquals(2, rows("customers").size());
            status.setRollbackOnly();
        });
        assertEquals(before, rows("customers"));
        assertTrue(recorder.events.isEmpty());
    }

    @Test
    void physicalForeignKeyBlocksDeletionForEachKindOfRelatedRecord() {
        var service = context.getBean(CustomerService.class);
        var withEnvironment = service.create(new CustomerData("One", null, null, null));
        context.getBean(EnvironmentService.class).create(new EnvironmentData(withEnvironment.id(), "Kitchen"));
        var withProject = service.create(new CustomerData("Two", null, null, null));
        context.getBean(SalesPipelineService.class).create(
                new SalesProjectData(withProject.id(), null, "Project", new BigDecimal("100")));
        var withFurniture = context.getBean(CustomerRepository.class).saveAndFlush(
                new CustomerEntity("Three", null, null, null));
        context.getBean(FurnitureItemRepository.class).saveAndFlush(new FurnitureItemEntity(
                withFurniture, null, "Cabinet", "Cabinet", "Wood",
                new BigDecimal("100"), new BigDecimal("200"), new BigDecimal("300")));
        recorder.events.clear();

        var tables = Map.of(withEnvironment.id(), "project_environments", withProject.id(), "sales_projects",
                withFurniture.getId(), "furniture_items");
        for (var entry : tables.entrySet()) {
            var customers = rows("customers");
            var references = rows(entry.getValue());
            var failure = assertThrows(IllegalStateException.class, () -> service.delete(entry.getKey()));
            assertEquals("error.customer-has-related-records", failure.getMessage());
            assertTrue(SqliteIntegrityErrors.isForeignKeyViolation(failure.getCause()), entry.getValue());
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(customers, rows("customers"));
            assertEquals(references, rows(entry.getValue()));
        }
        assertTrue(recorder.events.isEmpty());
    }

    @Test
    void propagatesNonForeignKeyDeletionFailureWithoutTranslationOrNotification() {
        var service = context.getBean(CustomerService.class);
        var customer = service.create(new CustomerData("Customer", null, null, null));
        recorder.events.clear();
        var before = rows("customers");
        var failure = new DataIntegrityViolationException("unrelated", new SQLiteException(
                "unique", SQLiteErrorCode.SQLITE_CONSTRAINT_UNIQUE));
        var catalog = context.getBean(CustomerCatalog.class);
        var real = context.getBean(CustomerCatalogPort.class);
        var intercept = mock(CustomerCatalogPort.class, delegatesTo(real));
        doThrow(failure).when(intercept).deleteAndFlush(anyString());
        ReflectionTestUtils.setField(catalog, "customers", intercept);
        try {
            assertSame(failure, assertThrows(DataIntegrityViolationException.class,
                    () -> service.delete(customer.id())));
        } finally {
            ReflectionTestUtils.setField(catalog, "customers", real);
        }
        assertEquals(before, rows("customers"));
        assertTrue(recorder.events.isEmpty());
    }

    @Test
    void rollsBackFlushedWriteAndPropagatesNonForeignKeyFailureWithoutNotification() {
        var service = context.getBean(CustomerService.class);
        var customer = service.create(new CustomerData("Original", null, null, null));
        recorder.events.clear();
        var before = rows("customers");
        var catalog = context.getBean(CustomerCatalog.class);
        var real = context.getBean(CustomerCatalogPort.class);
        var intercept = mock(CustomerCatalogPort.class, delegatesTo(real));
        var observed = new AtomicBoolean();
        doAnswer(invocation -> {
            Optional<CustomerRow> updated = real.update(invocation.getArgument(0), invocation.getArgument(1));
            assertTrue(updated.isPresent());
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals("Changed", jdbc.queryForObject("SELECT full_name FROM customers WHERE id = ?",
                    String.class, customer.id()));
            observed.set(true);
            jdbc.update("INSERT INTO customers (id) VALUES (?)", "invalid");
            throw new AssertionError("Expected SQLite constraint failure");
        }).when(intercept).update(anyString(), any(CustomerInput.class));
        ReflectionTestUtils.setField(catalog, "customers", intercept);
        try {
            var failure = assertThrows(UncategorizedSQLException.class,
                    () -> service.update(customer.id(), new CustomerData("Changed", null, null, null)));
            assertFalse(SqliteIntegrityErrors.isForeignKeyViolation(failure));
        } finally {
            ReflectionTestUtils.setField(catalog, "customers", real);
        }
        assertTrue(observed.get());
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertEquals(before, rows("customers"));
        assertTrue(recorder.events.isEmpty());
    }

    @Test
    void rollsBackEarlierFlushedUpdateWhenDeleteViolatesForeignKey() {
        var service = context.getBean(CustomerService.class);
        var customer = service.create(new CustomerData("Original", null, null, null));
        context.getBean(EnvironmentService.class).create(new EnvironmentData(customer.id(), "Kitchen"));
        recorder.events.clear();
        var before = rows("customers");
        var failure = assertThrows(IllegalStateException.class, () ->
                new TransactionTemplate(context.getBean(PlatformTransactionManager.class))
                        .executeWithoutResult(status -> {
                            service.update(customer.id(), new CustomerData("Changed", null, null, null));
                            service.delete(customer.id());
                        }));
        assertEquals("error.customer-has-related-records", failure.getMessage());
        assertTrue(SqliteIntegrityErrors.isForeignKeyViolation(failure.getCause()));
        assertEquals(before, rows("customers"));
        assertTrue(recorder.events.isEmpty());
    }

    private static List<String> ids(List<CustomerRow> rows) {
        return rows.stream().map(CustomerRow::id).toList();
    }

    private List<Map<String, Object>> rows(String table) {
        return jdbc.queryForList("SELECT * FROM " + table + " ORDER BY id");
    }

    // Reads through a separate connection, so it only sees data that is already committed.
    public static class AfterCommitRecorder {
        final List<String> events = new CopyOnWriteArrayList<>();
        volatile Path databasePath;

        @TransactionalEventListener
        public void onCustomerChanged(CustomerChangedEvent event) {
            try (var connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
                 var statement = connection.createStatement();
                 var result = statement.executeQuery(
                         "SELECT COALESCE(group_concat(full_name, ','), '') FROM customers")) {
                result.next();
                events.add("customers=" + result.getString(1));
            } catch (SQLException exception) {
                events.add("error=" + exception.getMessage());
            }
        }
    }
}
