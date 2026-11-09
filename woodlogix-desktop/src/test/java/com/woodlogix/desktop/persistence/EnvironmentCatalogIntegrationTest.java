package com.woodlogix.desktop.persistence;

import com.woodlogix.application.environment.EnvironmentCatalog;
import com.woodlogix.application.environment.EnvironmentCatalogPort;
import com.woodlogix.application.environment.EnvironmentRow;
import com.woodlogix.core.enums.MaterialType;
import com.woodlogix.core.enums.RoomType;
import com.woodlogix.core.enums.VisibleEdge;
import com.woodlogix.desktop.WoodLogixApplication;
import com.woodlogix.desktop.persistence.entity.CustomerEntity;
import com.woodlogix.desktop.persistence.entity.FurnitureItemEntity;
import com.woodlogix.desktop.persistence.entity.FurniturePartEntity;
import com.woodlogix.desktop.persistence.entity.ProjectEnvironmentEntity;
import com.woodlogix.desktop.persistence.repository.CustomerRepository;
import com.woodlogix.desktop.persistence.repository.FurnitureItemRepository;
import com.woodlogix.desktop.persistence.repository.FurniturePartRepository;
import com.woodlogix.desktop.persistence.repository.ProjectEnvironmentRepository;
import com.woodlogix.desktop.service.CustomerData;
import com.woodlogix.desktop.service.CustomerService;
import com.woodlogix.desktop.service.EnvironmentData;
import com.woodlogix.desktop.service.EnvironmentService;
import com.woodlogix.desktop.service.SalesPipelineService;
import com.woodlogix.desktop.service.SalesProjectData;
import com.woodlogix.desktop.service.event.EnvironmentChangedEvent;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class EnvironmentCatalogIntegrationTest {
    @TempDir Path temporaryDirectory;
    private ConfigurableApplicationContext context;
    private JdbcTemplate jdbc;
    private AfterCommitRecorder recorder;
    private EnvironmentService service;
    private String customerId;

    @BeforeEach
    void setUp() {
        Path databasePath = temporaryDirectory.resolve("environments.db");
        context = new SpringApplicationBuilder(WoodLogixApplication.class, AfterCommitRecorder.class)
                .web(WebApplicationType.NONE).run("--spring.main.banner-mode=off", "--logging.level.root=ERROR",
                        "--logging.file.name=", "--woodlogix.database.path=" + databasePath);
        jdbc = new JdbcTemplate(context.getBean(DataSource.class));
        recorder = context.getBean(AfterCommitRecorder.class);
        recorder.databasePath = databasePath;
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1' AND success = 1", Integer.class));
        service = context.getBean(EnvironmentService.class);
        customerId = context.getBean(CustomerService.class)
                .create(new CustomerData("Customer", null, null, null)).id();
        recorder.events.clear();
    }

    @AfterEach
    void tearDown() {
        if (context != null) context.close();
    }

    @Test
    void persistsUpdatesListsAndDeletesEnvironments() {
        var kitchen = service.create(new EnvironmentData(customerId, "Kitchen"));
        var bedroom = service.create(new EnvironmentData(customerId, "Bedroom"));
        assertEquals(customerId, kitchen.customerId());
        assertEquals("Customer", kitchen.customerName());
        assertEquals("Kitchen — Customer", kitchen.toString());
        assertEquals(List.of("Bedroom", "Kitchen"), types(service.findAll()));
        assertEquals(2, service.findByCustomer(customerId).size());

        var updated = service.update(kitchen.id(), new EnvironmentData(customerId, "Pantry"));
        assertEquals(kitchen.id(), updated.id());
        assertEquals("Pantry", jdbc.queryForObject(
                "SELECT environment_type FROM project_environments WHERE id = ?", String.class, kitchen.id()));
        service.delete(bedroom.id());
        assertEquals(List.of(kitchen.id()), ids(service.findAll()));
    }

    @Test
    void createsCatalogAndCustomRoomsWithoutDuplicates() {
        var created = service.createForCustomer(customerId,
                List.of(RoomType.KITCHEN, RoomType.KITCHEN, RoomType.BATHROOM), " kitchen ");
        assertEquals(Set.of("KITCHEN", "BATHROOM"), Set.copyOf(types(created)));
        assertEquals(1, recorder.events.size());
        assertTrue(service.createForCustomer(customerId, List.of(RoomType.KITCHEN), "bathroom").isEmpty());
        assertEquals(1, recorder.events.size());
        assertEquals(1, service.createForCustomer(customerId, null, "My Studio").size());
        assertEquals(3, service.findByCustomer(customerId).size());
    }

    @Test
    void synchronizesToAnExactListRenamingCreatingAndRemoving() {
        var initial = service.saveOrUpdateCustomerEnvironments(customerId, List.of("Kitchen", "Bedroom"));
        var kitchenId = initial.stream().filter(row -> row.type().equals("Kitchen")).findFirst().orElseThrow().id();
        recorder.events.clear();
        var result = service.saveOrUpdateCustomerEnvironments(customerId, List.of(" KITCHEN ", "Pantry", "pantry"));
        assertEquals(List.of("KITCHEN", "Pantry"), types(result.stream()
                .sorted(Comparator.comparing(EnvironmentRow::type)).toList()));
        assertTrue(ids(result).contains(kitchenId));
        assertEquals(2, rows("project_environments").size());
        assertEquals(1, recorder.events.size());

        recorder.events.clear();
        assertEquals(ids(result), ids(service.saveOrUpdateCustomerEnvironments(customerId, List.of("KITCHEN", "Pantry"))));
        assertTrue(recorder.events.isEmpty());
        service.deleteAllByCustomer(customerId);
        assertTrue(rows("project_environments").isEmpty());
        assertEquals(1, recorder.events.size());
    }

    @Test
    void validatesFacadeAndMissingRecordsWithoutPersistingOrNotifying() {
        assertThrows(ConstraintViolationException.class,
                () -> service.create(new EnvironmentData(" ", "Kitchen")));
        assertThrows(ConstraintViolationException.class,
                () -> service.create(new EnvironmentData(customerId, " ")));
        assertEquals("error.customer-not-found", assertThrows(EntityNotFoundException.class,
                () -> service.create(new EnvironmentData("missing", "Kitchen"))).getMessage());
        assertEquals("error.environment-not-found", assertThrows(EntityNotFoundException.class,
                () -> service.update("missing", new EnvironmentData(customerId, "Kitchen"))).getMessage());
        assertEquals("error.environment-not-found", assertThrows(EntityNotFoundException.class,
                () -> service.delete("missing")).getMessage());
        assertEquals("error.customer-not-found", assertThrows(EntityNotFoundException.class,
                () -> service.createForCustomer("missing", List.of(RoomType.KITCHEN))).getMessage());
        assertEquals("error.environment-type", assertThrows(IllegalArgumentException.class,
                () -> service.createForCustomer(customerId, List.of(), " ")).getMessage());
        assertTrue(rows("project_environments").isEmpty());
        assertTrue(recorder.events.isEmpty());
    }

    @Test
    void afterCommitListenersRunOnlyAfterCommitAndNotInRollback() {
        var created = service.create(new EnvironmentData(customerId, "Kitchen"));
        assertEquals(List.of("environments=Kitchen"), recorder.events);
        service.update(created.id(), new EnvironmentData(customerId, "Pantry"));
        service.delete(created.id());
        assertEquals(List.of("environments=Kitchen", "environments=Pantry", "environments="), recorder.events);
        recorder.events.clear();

        var template = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        template.executeWithoutResult(status -> {
            service.create(new EnvironmentData(customerId, "Joined"));
            service.saveOrUpdateCustomerEnvironments(customerId, List.of("Joined", "Again"));
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertTrue(recorder.events.isEmpty());
        });
        assertEquals(2, recorder.events.size());

        recorder.events.clear();
        var before = rows("project_environments");
        template.executeWithoutResult(status -> {
            service.create(new EnvironmentData(customerId, "Discarded"));
            status.setRollbackOnly();
        });
        assertEquals(before, rows("project_environments"));
        assertTrue(recorder.events.isEmpty());
    }

    @Test
    void physicalForeignKeyBlocksSingleDeletionForEachKindOfReference() {
        var references = createReferencedEnvironments();
        for (var entry : references.entrySet()) {
            var environments = rows("project_environments");
            var referencing = rows(entry.getKey());
            var failure = assertThrows(IllegalStateException.class, () -> service.delete(entry.getValue()));
            assertEquals("error.environment-has-related-records", failure.getMessage());
            assertTrue(SqliteIntegrityErrors.isForeignKeyViolation(failure.getCause()), entry.getKey());
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(environments, rows("project_environments"));
            assertEquals(referencing, rows(entry.getKey()));
        }
        assertTrue(recorder.events.isEmpty());
    }

    @Test
    void referenceChecksBlockSynchronizationForEachKindWithoutChangingData() {
        var references = createReferencedEnvironments();
        for (var entry : references.entrySet()) {
            var environments = rows("project_environments");
            var referencing = rows(entry.getKey());
            var failure = assertThrows(IllegalStateException.class,
                    () -> service.deleteAllByCustomer(customerId));
            assertEquals("error.environment-has-related-records", failure.getMessage());
            assertNull(failure.getCause());
            assertEquals(environments, rows("project_environments"));
            assertEquals(referencing, rows(entry.getKey()));
        }
        assertTrue(recorder.events.isEmpty());
    }

    @Test
    void rollsBackFlushedSynchronizationWhenPersistenceFailsAfterwards() {
        var original = service.saveOrUpdateCustomerEnvironments(customerId, List.of("Kitchen", "Bedroom"));
        recorder.events.clear();
        var before = rows("project_environments");
        var catalog = context.getBean(EnvironmentCatalog.class);
        var real = context.getBean(EnvironmentCatalogPort.class);
        var intercepted = mock(EnvironmentCatalogPort.class, delegatesTo(real));
        var observed = new AtomicBoolean();
        doAnswer(invocation -> {
            real.createAllAndFlush(invocation.getArgument(0), invocation.getArgument(1));
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM project_environments WHERE environment_type = 'Pantry'", Integer.class));
            assertEquals(0, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM project_environments WHERE environment_type = 'Bedroom'", Integer.class));
            observed.set(true);
            jdbc.update("INSERT INTO project_environments (id) VALUES (?)", "invalid");
            throw new AssertionError("Expected SQLite constraint failure");
        }).when(intercepted).createAllAndFlush(anyString(), anyList());
        ReflectionTestUtils.setField(catalog, "environments", intercepted);
        try {
            var failure = assertThrows(UncategorizedSQLException.class,
                    () -> service.saveOrUpdateCustomerEnvironments(customerId, List.of("KITCHEN", "Pantry")));
            assertFalse(SqliteIntegrityErrors.isForeignKeyViolation(failure));
        } finally {
            ReflectionTestUtils.setField(catalog, "environments", real);
        }
        assertTrue(observed.get());
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertEquals(before, rows("project_environments"));
        assertEquals(ids(original), ids(service.findByCustomer(customerId)));
        assertTrue(recorder.events.isEmpty());
    }

    @Test
    void propagatesNonForeignKeyFailuresWithoutTranslationOrNotification() {
        var environment = service.create(new EnvironmentData(customerId, "Kitchen"));
        recorder.events.clear();
        var before = rows("project_environments");
        var failure = new DataIntegrityViolationException("unrelated", new SQLiteException(
                "unique", SQLiteErrorCode.SQLITE_CONSTRAINT_UNIQUE));
        var catalog = context.getBean(EnvironmentCatalog.class);
        var real = context.getBean(EnvironmentCatalogPort.class);
        var intercepted = mock(EnvironmentCatalogPort.class, delegatesTo(real));
        doThrow(failure).when(intercepted).deleteAndFlush(anyString());
        doThrow(failure).when(intercepted).deleteAllAndFlush(anyList());
        ReflectionTestUtils.setField(catalog, "environments", intercepted);
        try {
            assertSame(failure, assertThrows(DataIntegrityViolationException.class,
                    () -> service.delete(environment.id())));
            assertSame(failure, assertThrows(DataIntegrityViolationException.class,
                    () -> service.saveOrUpdateCustomerEnvironments(customerId, List.of("Pantry"))));
            assertSame(failure, assertThrows(DataIntegrityViolationException.class,
                    () -> service.deleteAllByCustomer(customerId)));
        } finally {
            ReflectionTestUtils.setField(catalog, "environments", real);
        }
        assertEquals(before, rows("project_environments"));
        assertTrue(recorder.events.isEmpty());
    }

    // Maps each referencing table to the environment it references.
    private Map<String, String> createReferencedEnvironments() {
        var customer = context.getBean(CustomerRepository.class).getReferenceById(customerId);
        var repository = context.getBean(ProjectEnvironmentRepository.class);
        var partEnvironment = repository.saveAndFlush(new ProjectEnvironmentEntity(customer, "Part"));
        var projectEnvironment = repository.saveAndFlush(new ProjectEnvironmentEntity(customer, "Project"));
        var itemEnvironment = repository.saveAndFlush(new ProjectEnvironmentEntity(customer, "Item"));
        context.getBean(FurniturePartRepository.class).saveAndFlush(new FurniturePartEntity(
                partEnvironment, "Panel", money("100"), money("200"), money("18"),
                MaterialType.MDF, Set.of(VisibleEdge.TOP)));
        context.getBean(SalesPipelineService.class).create(new SalesProjectData(
                customerId, projectEnvironment.getId(), "Project", money("100")));
        context.getBean(FurnitureItemRepository.class).saveAndFlush(new FurnitureItemEntity(
                customer, itemEnvironment, "Cabinet", "Cabinet", "Wood",
                money("100"), money("200"), money("300")));
        recorder.events.clear();
        return Map.of("furniture_parts", partEnvironment.getId(), "sales_projects", projectEnvironment.getId(),
                "furniture_items", itemEnvironment.getId());
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    private static List<String> ids(List<EnvironmentRow> rows) {
        return rows.stream().map(EnvironmentRow::id).sorted().toList();
    }

    private static List<String> types(List<EnvironmentRow> rows) {
        return rows.stream().map(EnvironmentRow::type).toList();
    }

    private List<Map<String, Object>> rows(String table) {
        return jdbc.queryForList("SELECT * FROM " + table + " ORDER BY id");
    }

    // Reads through a separate connection, so it only sees data that is already committed.
    public static class AfterCommitRecorder {
        final List<String> events = new CopyOnWriteArrayList<>();
        volatile Path databasePath;

        @TransactionalEventListener
        public void onEnvironmentChanged(EnvironmentChangedEvent event) {
            try (var connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
                 var statement = connection.createStatement();
                 var result = statement.executeQuery(
                         "SELECT COALESCE(group_concat(environment_type, ','), '') FROM project_environments")) {
                result.next();
                events.add("environments=" + result.getString(1));
            } catch (SQLException exception) {
                events.add("error=" + exception.getMessage());
            }
        }
    }
}
