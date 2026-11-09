package com.woodlogix.desktop.persistence;

import com.woodlogix.core.enums.MaterialType;
import com.woodlogix.core.enums.SalesStage;
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
import com.woodlogix.desktop.service.EdgeBandingData;
import com.woodlogix.desktop.service.EdgeBandingService;
import com.woodlogix.application.environment.EnvironmentCatalog;
import com.woodlogix.application.environment.EnvironmentCatalogPort;
import com.woodlogix.desktop.service.EnvironmentData;
import com.woodlogix.desktop.service.EnvironmentService;
import com.woodlogix.desktop.service.EstimateDraftData;
import com.woodlogix.desktop.service.EstimateDraftService;
import com.woodlogix.desktop.service.HardwareData;
import com.woodlogix.desktop.service.HardwareService;
import com.woodlogix.desktop.service.MaterialData;
import com.woodlogix.desktop.service.MaterialService;
import com.woodlogix.desktop.service.ProjectWorkpieceData;
import com.woodlogix.desktop.service.ProjectWorkpieceService;
import com.woodlogix.desktop.service.SalesPipelineService;
import com.woodlogix.desktop.service.SalesProjectData;
import com.woodlogix.desktop.service.event.CustomerChangedEvent;
import com.woodlogix.desktop.service.event.EnvironmentChangedEvent;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityNotFoundException;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.sqlite.SQLiteException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

class DeletionAndPipelineCharacterizationTest {
    @TempDir
    Path temporaryDirectory;

    private ConfigurableApplicationContext context;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        context = new SpringApplicationBuilder(WoodLogixApplication.class)
                .web(WebApplicationType.NONE)
                .run("--spring.main.banner-mode=off",
                        "--logging.level.root=ERROR",
                        "--logging.file.name=",
                        "--woodlogix.database.path=" + temporaryDirectory.resolve("b2.db"));
        jdbc = new JdbcTemplate(context.getBean(DataSource.class));
    }

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void migratedSchemaHasAllMappedForeignKeys() {
        assertForeignKeys("project_environments", Set.of("customer_id:customers:id"));
        assertForeignKeys("sales_projects", Set.of(
                "customer_id:customers:id", "environment_id:project_environments:id"));
        assertForeignKeys("furniture_items", Set.of(
                "customer_id:customers:id", "environment_id:project_environments:id"));
        assertForeignKeys("furniture_parts", Set.of("project_environment_id:project_environments:id"));
        assertForeignKeys("furniture_part_visible_edges", Set.of("furniture_part_id:furniture_parts:id"));
        assertForeignKeys("project_workpieces", Set.of("project_id:sales_projects:id",
                "material_id:materials:id", "edge_banding_id:edge_bandings:id"));
        assertForeignKeys("project_estimates", Set.of("project_id:sales_projects:id"));
        assertForeignKeys("project_estimate_workpieces", Set.of("estimate_id:project_estimates:id",
                "material_id:materials:id", "edge_banding_id:edge_bandings:id"));
        assertForeignKeys("project_estimate_hardwares", Set.of(
                "estimate_id:project_estimates:id", "hardware_id:hardwares:id"));
        assertTrue(jdbc.queryForList("PRAGMA foreign_key_check").isEmpty());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1' AND success = 1",
                Integer.class));
    }

    @Test
    void migratedSchemaRejectsInsertingAnEnvironmentWithAnUnknownCustomer() {
        withForeignKeys(() -> {
            var entityManager = EntityManagerFactoryUtils.getTransactionalEntityManager(
                    context.getBean(EntityManagerFactory.class));
            assertTrue(entityManager != null && entityManager.isJoinedToTransaction());
            entityManager.unwrap(Session.class).doWork(connection -> {
                try (var statement = connection.prepareStatement(
                        "INSERT INTO project_environments (id, customer_id, environment_type) VALUES (?, ?, ?)")) {
                    statement.setString(1, "environment");
                    statement.setString(2, "missing");
                    statement.setString(3, "Kitchen");
                    var failure = assertThrows(SQLiteException.class, statement::executeUpdate);
                    assertEquals("SQLITE_CONSTRAINT_FOREIGNKEY", failure.getResultCode().name());
                }
            });
        });
        assertTrue(rows("project_environments").isEmpty());
    }

    @Test
    void deletesUnreferencedRecordsAndRejectsMissingIds() {
        var customers = context.getBean(CustomerService.class);
        var environments = context.getBean(EnvironmentService.class);
        var materials = context.getBean(MaterialService.class);
        var edges = context.getBean(EdgeBandingService.class);
        var hardwares = context.getBean(HardwareService.class);
        var customer = customers.create(new CustomerData("Customer", null, null, null));
        var environment = environments.create(new EnvironmentData(customer.id(), "Kitchen"));
        var catalog = createCatalog();

        withForeignKeys(() -> environments.delete(environment.id()));
        withForeignKeys(() -> customers.delete(customer.id()));
        withForeignKeys(() -> materials.delete(catalog.materialId()));
        withForeignKeys(() -> edges.delete(catalog.edgeId()));
        withForeignKeys(() -> hardwares.delete(catalog.hardwareId()));
        assertTrue(rows("customers").isEmpty());
        assertTrue(rows("project_environments").isEmpty());
        assertTrue(rows("materials").isEmpty());
        assertTrue(rows("edge_bandings").isEmpty());
        assertTrue(rows("hardwares").isEmpty());

        assertEquals("error.customer-not-found",
                assertThrows(EntityNotFoundException.class, () -> customers.delete("missing")).getMessage());
        assertEquals("error.environment-not-found",
                assertThrows(EntityNotFoundException.class, () -> environments.delete("missing")).getMessage());
        assertEquals("error.material-not-found",
                assertThrows(EntityNotFoundException.class, () -> materials.delete("missing")).getMessage());
        assertEquals("error.edge-banding-not-found",
                assertThrows(EntityNotFoundException.class, () -> edges.delete("missing")).getMessage());
        assertEquals("error.hardware-not-found",
                assertThrows(EntityNotFoundException.class, () -> hardwares.delete("missing")).getMessage());
    }

    @Test
    void customerDeletionIsBlockedByEachKindOfRelatedRecord() {
        var customers = context.getBean(CustomerService.class);
        var environments = context.getBean(EnvironmentService.class);
        var pipeline = context.getBean(SalesPipelineService.class);
        var customerWithEnvironment = customers.create(new CustomerData("One", null, null, null));
        environments.create(new EnvironmentData(customerWithEnvironment.id(), "Kitchen"));
        var customerWithProject = customers.create(new CustomerData("Two", null, null, null));
        pipeline.create(new SalesProjectData(customerWithProject.id(), null, "Project", money("100")));
        var customerWithFurniture = context.getBean(CustomerRepository.class).saveAndFlush(
                new CustomerEntity("Three", null, null, null));
        context.getBean(FurnitureItemRepository.class).saveAndFlush(new FurnitureItemEntity(
                customerWithFurniture, null, "Cabinet", "Cabinet", "Wood",
                money("100"), money("200"), money("300")));

        var customerRows = rows("customers");
        var environmentRows = rows("project_environments");
        var projectRows = rows("sales_projects");
        var furnitureRows = rows("furniture_items");
        for (String id : List.of(customerWithEnvironment.id(), customerWithProject.id(),
                customerWithFurniture.getId())) {
            assertForeignKeyFailure("error.customer-has-related-records",
                    () -> withForeignKeys(() -> customers.delete(id)));
        }
        assertEquals(customerRows, rows("customers"));
        assertEquals(environmentRows, rows("project_environments"));
        assertEquals(projectRows, rows("sales_projects"));
        assertEquals(furnitureRows, rows("furniture_items"));
        assertEquals(0, countOrphans("project_environments", "customer_id", "customers"));
        assertEquals(0, countOrphans("sales_projects", "customer_id", "customers"));
        assertEquals(0, countOrphans("furniture_items", "customer_id", "customers"));
    }

    @Test
    void environmentDeletionIsBlockedByEachKindOfRelatedRecord() {
        var customer = context.getBean(CustomerRepository.class).saveAndFlush(
                new CustomerEntity("Customer", null, null, null));
        var repository = context.getBean(ProjectEnvironmentRepository.class);
        var partEnvironment = repository.saveAndFlush(new ProjectEnvironmentEntity(customer, "Part"));
        var projectEnvironment = repository.saveAndFlush(new ProjectEnvironmentEntity(customer, "Project"));
        var itemEnvironment = repository.saveAndFlush(new ProjectEnvironmentEntity(customer, "Item"));
        context.getBean(FurniturePartRepository.class).saveAndFlush(new FurniturePartEntity(
                partEnvironment, "Panel", money("100"), money("200"), money("18"),
                MaterialType.MDF, Set.of(VisibleEdge.TOP)));
        context.getBean(SalesPipelineService.class).create(new SalesProjectData(
                customer.getId(), projectEnvironment.getId(), "Project", money("100")));
        context.getBean(FurnitureItemRepository.class).saveAndFlush(new FurnitureItemEntity(
                customer, itemEnvironment, "Cabinet", "Cabinet", "Wood",
                money("100"), money("200"), money("300")));

        var environmentRows = rows("project_environments");
        var partRows = rows("furniture_parts");
        var visibleEdges = jdbc.queryForList(
                "SELECT * FROM furniture_part_visible_edges ORDER BY furniture_part_id, visible_edge");
        var projectRows = rows("sales_projects");
        var itemRows = rows("furniture_items");
        var environments = context.getBean(EnvironmentService.class);
        for (String id : List.of(partEnvironment.getId(), projectEnvironment.getId(), itemEnvironment.getId())) {
            assertForeignKeyFailure("error.environment-has-related-records",
                    () -> withForeignKeys(() -> environments.delete(id)));
        }
        assertEquals(environmentRows, rows("project_environments"));
        assertEquals(partRows, rows("furniture_parts"));
        assertEquals(visibleEdges, jdbc.queryForList(
                "SELECT * FROM furniture_part_visible_edges ORDER BY furniture_part_id, visible_edge"));
        assertEquals(projectRows, rows("sales_projects"));
        assertEquals(itemRows, rows("furniture_items"));
        assertEquals(0, countOrphans("furniture_parts", "project_environment_id", "project_environments"));
        assertEquals(0, countOrphans("sales_projects", "environment_id", "project_environments"));
        assertEquals(0, countOrphans("furniture_items", "environment_id", "project_environments"));
    }

    @Test
    void bulkEnvironmentRemovalChecksFurnitureItemBeforeChanges() {
        var customer = context.getBean(CustomerRepository.class).saveAndFlush(
                new CustomerEntity("Customer", null, null, null));
        var environment = context.getBean(ProjectEnvironmentRepository.class)
                .saveAndFlush(new ProjectEnvironmentEntity(customer, "Kitchen"));
        context.getBean(ProjectEnvironmentRepository.class)
                .saveAndFlush(new ProjectEnvironmentEntity(customer, "Bedroom"));
        context.getBean(FurnitureItemRepository.class).saveAndFlush(new FurnitureItemEntity(
                customer, environment, "Cabinet", "Cabinet", "Wood",
                money("100"), money("200"), money("300")));
        var itemsBefore = rows("furniture_items");
        var environmentsBefore = rows("project_environments");

        var environments = context.getBean(EnvironmentService.class);
        var failure = assertThrows(IllegalStateException.class, () -> withForeignKeys(
                () -> environments.saveOrUpdateCustomerEnvironments(
                        customer.getId(), List.of("BEDROOM", "Pantry"))));
        assertEquals("error.environment-has-related-records", failure.getMessage());
        assertEquals(null, failure.getCause());

        assertEquals(environmentsBefore, rows("project_environments"));
        assertEquals(itemsBefore, rows("furniture_items"));
        assertEquals(0, countOrphans("furniture_items", "environment_id", "project_environments"));
        var bulkFailure = assertThrows(IllegalStateException.class, () -> withForeignKeys(
                () -> environments.deleteAllByCustomer(customer.getId())));
        assertEquals("error.environment-has-related-records", bulkFailure.getMessage());
        assertEquals(environmentsBefore, rows("project_environments"));
        assertEquals(itemsBefore, rows("furniture_items"));
    }

    @Test
    void bulkEnvironmentRemovalRollsBackRealDeletionWhenForeignKeyRejectsLaterDelete() {
        var customer = context.getBean(CustomerRepository.class).saveAndFlush(
                new CustomerEntity("Customer", null, null, null));
        var repository = context.getBean(ProjectEnvironmentRepository.class);
        var free = repository.saveAndFlush(new ProjectEnvironmentEntity(customer, "Free"));
        var linked = repository.saveAndFlush(new ProjectEnvironmentEntity(customer, "Linked"));
        var itemRepository = context.getBean(FurnitureItemRepository.class);
        itemRepository.saveAndFlush(new FurnitureItemEntity(customer, linked, "Cabinet",
                "Cabinet", "Wood", money("100"), money("200"), money("300")));
        var environmentRows = rows("project_environments");
        var itemRows = rows("furniture_items");
        var service = context.getBean(EnvironmentService.class);
        var catalog = context.getBean(EnvironmentCatalog.class);
        var realPort = context.getBean(EnvironmentCatalogPort.class);
        var intercepted = mock(EnvironmentCatalogPort.class, delegatesTo(realPort));
        var observedWrite = new AtomicBoolean();
        doReturn(false).when(intercepted).hasFurnitureItemReferences(linked.getId());
        doAnswer(invocation -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            repository.deleteById(free.getId());
            repository.flush();
            var entityManager = EntityManagerFactoryUtils.getTransactionalEntityManager(
                    context.getBean(EntityManagerFactory.class));
            assertTrue(entityManager != null && entityManager.isJoinedToTransaction());
            long count = entityManager.createQuery(
                            "SELECT COUNT(e) FROM ProjectEnvironmentEntity e WHERE e.id = :id", Long.class)
                    .setParameter("id", free.getId()).getSingleResult();
            assertEquals(0L, count);
            observedWrite.set(true);
            repository.deleteById(linked.getId());
            repository.flush();
            return null;
        }).when(intercepted).deleteAllAndFlush(any());
        ReflectionTestUtils.setField(catalog, "environments", intercepted);
        try {
            assertForeignKeyFailure("error.environment-has-related-records", () -> withForeignKeys(
                    () -> service.saveOrUpdateCustomerEnvironments(customer.getId(), List.of("New"))));
        } finally {
            ReflectionTestUtils.setField(catalog, "environments", realPort);
        }
        assertTrue(observedWrite.get());
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertEquals(environmentRows, rows("project_environments"));
        assertEquals(itemRows, rows("furniture_items"));
    }

    @Test
    void catalogDeletionIsBlockedByProjectWorkpieceReferences() {
        var customer = context.getBean(CustomerService.class)
                .create(new CustomerData("Customer", null, null, null));
        var project = context.getBean(SalesPipelineService.class).create(
                new SalesProjectData(customer.id(), null, "Project", money("100")));
        var catalog = createCatalog();
        context.getBean(ProjectWorkpieceService.class).create(new ProjectWorkpieceData(
                project.id(), "Panel", money("100"), money("200"), 2,
                catalog.materialId(), catalog.edgeId(), true, false, false, false));
        var materials = rows("materials");
        var edges = rows("edge_bandings");
        var pieces = rows("project_workpieces");

        assertEquals("error.material-has-related-records", assertThrows(IllegalStateException.class,
                () -> withForeignKeys(() -> context.getBean(MaterialService.class).delete(catalog.materialId())))
                .getMessage());
        assertEquals("error.edge-banding-has-related-records", assertThrows(IllegalStateException.class,
                () -> withForeignKeys(() -> context.getBean(EdgeBandingService.class).delete(catalog.edgeId())))
                .getMessage());
        assertEquals(materials, rows("materials"));
        assertEquals(edges, rows("edge_bandings"));
        assertEquals(pieces, rows("project_workpieces"));
    }

    @Test
    void catalogDeletionIsBlockedByDraftReferences() {
        var catalog = createCatalog();
        var piece = new EstimateDraftData.EstimateDraftWorkpieceData(
                null, "Panel", money("100"), money("200"), 2, catalog.materialId(),
                catalog.edgeId(), true, false, false, false);
        var hardware = new EstimateDraftData.EstimateDraftHardwareData(catalog.hardwareId(), 3);
        context.getBean(EstimateDraftService.class).saveActiveDraft(new EstimateDraftData(
                null, money("1"), money("2"), money("3"), money("4"), money("5"),
                List.of(piece), List.of(hardware)));
        var materials = rows("materials");
        var edges = rows("edge_bandings");
        var hardwares = rows("hardwares");
        var draft = rows("project_estimates");
        var pieces = rows("project_estimate_workpieces");
        var hardwareRows = rows("project_estimate_hardwares");

        assertEquals("error.material-has-related-records", assertThrows(IllegalStateException.class,
                () -> withForeignKeys(() -> context.getBean(MaterialService.class).delete(catalog.materialId())))
                .getMessage());
        assertEquals("error.edge-banding-has-related-records", assertThrows(IllegalStateException.class,
                () -> withForeignKeys(() -> context.getBean(EdgeBandingService.class).delete(catalog.edgeId())))
                .getMessage());
        assertEquals("error.hardware-has-related-records", assertThrows(IllegalStateException.class,
                () -> withForeignKeys(() -> context.getBean(HardwareService.class).delete(catalog.hardwareId())))
                .getMessage());
        assertEquals(materials, rows("materials"));
        assertEquals(edges, rows("edge_bandings"));
        assertEquals(hardwares, rows("hardwares"));
        assertEquals(draft, rows("project_estimates"));
        assertEquals(pieces, rows("project_estimate_workpieces"));
        assertEquals(hardwareRows, rows("project_estimate_hardwares"));
    }

    @Test
    void customerDeletionRollsBackWhenListenerFailsAfterFlush() {
        var customers = context.getBean(CustomerService.class);
        var customer = customers.create(new CustomerData("Customer", null, null, null));
        var before = rows("customers");
        var observed = failAfterDeletion(CustomerChangedEvent.class, "customers", customer.id());

        var failure = assertThrows(IllegalStateException.class, () -> customers.delete(customer.id()));

        assertEquals("injected listener failure", failure.getMessage());
        assertTrue(observed.get());
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertEquals(before, rows("customers"));
    }

    @Test
    void environmentDeletionRollsBackWhenListenerFailsAfterFlush() {
        var customer = context.getBean(CustomerService.class)
                .create(new CustomerData("Customer", null, null, null));
        var environments = context.getBean(EnvironmentService.class);
        var environment = environments.create(new EnvironmentData(customer.id(), "Kitchen"));
        var before = rows("project_environments");
        var customersBefore = rows("customers");
        var observed = failAfterDeletion(EnvironmentChangedEvent.class, "project_environments", environment.id());

        var failure = assertThrows(IllegalStateException.class, () -> environments.delete(environment.id()));

        assertEquals("injected listener failure", failure.getMessage());
        assertTrue(observed.get());
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertEquals(before, rows("project_environments"));
        assertEquals(customersBefore, rows("customers"));
    }

    @Test
    void createsProjectsWithMatchingOrOptionalEnvironment() {
        var customers = context.getBean(CustomerService.class);
        var customer = customers.create(new CustomerData("Customer", "11999999999", "test@example.test", null));
        var environment = context.getBean(EnvironmentService.class)
                .create(new EnvironmentData(customer.id(), "Kitchen"));
        var pipeline = context.getBean(SalesPipelineService.class);
        var project = pipeline.create(new SalesProjectData(customer.id(), environment.id(), "Kitchen project", money("1234.56")));
        var freeProject = pipeline.create(new SalesProjectData(customer.id(), null, "Free project", money("99")));
        var blankProject = pipeline.create(new SalesProjectData(customer.id(), "  ", "Blank environment", money("1")));

        assertEquals(SalesStage.CONTACT, project.stage());
        assertEquals("Kitchen project", project.projectName());
        assertEquals("Customer", project.customerName());
        assertEquals("Kitchen", project.environmentName());
        assertEquals(0, money("1234.56").compareTo(project.totalValue()));
        assertEquals(project, pipeline.findById(project.id()));
        assertEquals("", pipeline.findById(freeProject.id()).environmentName());
        assertEquals("", pipeline.findById(blankProject.id()).environmentName());
        var persisted = jdbc.queryForMap("SELECT * FROM sales_projects WHERE id = ?", project.id());
        assertEquals(customer.id(), persisted.get("customer_id"));
        assertEquals(environment.id(), persisted.get("environment_id"));
        assertEquals(3, rows("sales_projects").size());
    }

    @Test
    void rejectsUnknownCustomerOrEnvironmentAndMismatchedEnvironment() {
        var customers = context.getBean(CustomerService.class);
        var first = customers.create(new CustomerData("First", null, null, null));
        var second = customers.create(new CustomerData("Second", null, null, null));
        var environment = context.getBean(EnvironmentService.class)
                .create(new EnvironmentData(second.id(), "Kitchen"));
        var pipeline = context.getBean(SalesPipelineService.class);
        var before = rows("sales_projects");

        assertEquals("error.customer-not-found", assertThrows(IllegalArgumentException.class,
                () -> pipeline.create(new SalesProjectData("missing", null, "Project", money("1")))).getMessage());
        assertEquals("error.environment-not-found", assertThrows(IllegalArgumentException.class,
                () -> pipeline.create(new SalesProjectData(first.id(), "missing", "Project", money("1")))).getMessage());
        assertEquals("error.environment-customer-mismatch", assertThrows(IllegalArgumentException.class,
                () -> pipeline.create(new SalesProjectData(first.id(), environment.id(), "Project", money("1")))).getMessage());
        assertEquals(before, rows("sales_projects"));
    }

    @Test
    void movesProjectWithoutChangingOtherPersistedFieldsAndRejectsInvalidMoves() {
        var customer = context.getBean(CustomerService.class)
                .create(new CustomerData("Customer", "11999999999", "test@example.test", null));
        var environment = context.getBean(EnvironmentService.class)
                .create(new EnvironmentData(customer.id(), "Kitchen"));
        var pipeline = context.getBean(SalesPipelineService.class);
        var project = pipeline.create(new SalesProjectData(customer.id(), environment.id(), "Project", money("1234.56")));
        var before = jdbc.queryForMap("SELECT * FROM sales_projects WHERE id = ?", project.id());

        pipeline.move(project.id(), SalesStage.CLOSED);
        var after = jdbc.queryForMap("SELECT * FROM sales_projects WHERE id = ?", project.id());
        var expected = new java.util.LinkedHashMap<>(before);
        expected.put("stage", "CLOSED");
        assertEquals(expected, after);
        assertEquals(SalesStage.CLOSED, pipeline.findById(project.id()).stage());
        assertTrue(pipeline.findPipeline().get(SalesStage.CLOSED).stream()
                .anyMatch(row -> row.id().equals(project.id())));

        assertThrows(NullPointerException.class, () -> pipeline.move(project.id(), null));
        assertEquals("error.project-not-found",
                assertThrows(IllegalArgumentException.class, () -> pipeline.move("missing", SalesStage.CONTACT)).getMessage());
        assertEquals("error.project-not-found",
                assertThrows(IllegalArgumentException.class, () -> pipeline.findById("missing")).getMessage());
        assertEquals(after, jdbc.queryForMap("SELECT * FROM sales_projects WHERE id = ?", project.id()));
    }

    private Catalog createCatalog() {
        var material = context.getBean(MaterialService.class).create(new MaterialData(
                "MDF", "White", money("18"), money("2750"), money("1840"), money("200")));
        var edge = context.getBean(EdgeBandingService.class).create(
                new EdgeBandingData("PVC", money("22"), money("3")));
        var hardware = context.getBean(HardwareService.class).create(
                new HardwareData("Hinge", "UNIT", money("14")));
        return new Catalog(material.id(), edge.id(), hardware.id());
    }

    private AtomicBoolean failAfterDeletion(Class<?> eventType, String table, String id) {
        var observed = new AtomicBoolean();
        context.addApplicationListener((ApplicationListener<ApplicationEvent>) event -> {
            if (event instanceof PayloadApplicationEvent<?> payload
                    && eventType.isInstance(payload.getPayload())) {
                var entityManager = EntityManagerFactoryUtils.getTransactionalEntityManager(
                        context.getBean(EntityManagerFactory.class));
                assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                assertTrue(entityManager != null && entityManager.isJoinedToTransaction());
                entityManager.unwrap(Session.class).doWork(connection -> {
                    try (var pragma = connection.createStatement();
                         var result = pragma.executeQuery("PRAGMA foreign_keys")) {
                        assertTrue(result.next());
                        assertEquals(1, result.getInt(1));
                    }
                    try (var statement = connection.prepareStatement(
                            "SELECT COUNT(*) FROM " + table + " WHERE id = ?")) {
                        statement.setString(1, id);
                        try (var result = statement.executeQuery()) {
                            assertTrue(result.next());
                            assertEquals(0, result.getInt(1));
                        }
                    }
                });
                observed.set(true);
                throw new IllegalStateException("injected listener failure");
            }
        });
        return observed;
    }

    private void withForeignKeys(Runnable action) {
        new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).executeWithoutResult(status -> {
            var entityManager = EntityManagerFactoryUtils.getTransactionalEntityManager(
                    context.getBean(EntityManagerFactory.class));
            assertTrue(entityManager != null && entityManager.isJoinedToTransaction());
            int enabled = entityManager.unwrap(Session.class).doReturningWork(connection -> {
                try (var statement = connection.createStatement();
                     var result = statement.executeQuery("PRAGMA foreign_keys")) {
                    if (!result.next()) {
                        throw new SQLException("SQLite foreign_keys pragma returned no rows");
                    }
                    return result.getInt(1);
                }
            });
            assertEquals(1, enabled);
            action.run();
        });
    }

    private List<Map<String, Object>> rows(String table) {
        return jdbc.queryForList("SELECT * FROM " + table + " ORDER BY id");
    }

    private void assertForeignKeyFailure(String message, Runnable action) {
        var failure = assertThrows(IllegalStateException.class, action::run);
        assertEquals(message, failure.getMessage());
        var persistenceFailure = assertInstanceOf(
                org.springframework.dao.DataAccessException.class, failure.getCause());
        assertEquals("SQLITE_CONSTRAINT_FOREIGNKEY",
                assertInstanceOf(SQLiteException.class, persistenceFailure.getMostSpecificCause()).getResultCode().name());
    }

    private void assertForeignKeys(String table, Set<String> expected) {
        var foreignKeys = jdbc.queryForList("PRAGMA foreign_key_list(" + table + ")");
        assertEquals(expected.size(), foreignKeys.size(), table);
        assertEquals(expected, foreignKeys.stream()
                .map(row -> row.get("from") + ":" + row.get("table") + ":" + row.get("to"))
                .collect(java.util.stream.Collectors.toSet()), table);
    }

    private int countOrphans(String child, String column, String parent) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + child + " child LEFT JOIN "
                + parent + " parent ON child." + column + " = parent.id WHERE child."
                + column + " IS NOT NULL AND parent.id IS NULL", Integer.class);
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    private record Catalog(String materialId, String edgeId, String hardwareId) {
    }
}
