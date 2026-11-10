package com.woodlogix.server.persistence;

import com.woodlogix.application.customer.CustomerCatalog;
import com.woodlogix.application.customer.CustomerInput;
import com.woodlogix.application.customer.CustomerRow;
import com.woodlogix.application.draft.EstimateDraft;
import com.woodlogix.application.draft.EstimateDraftInput;
import com.woodlogix.application.edgebanding.EdgeBandingCatalog;
import com.woodlogix.application.edgebanding.EdgeBandingInput;
import com.woodlogix.application.environment.EnvironmentCatalog;
import com.woodlogix.application.environment.EnvironmentCatalogPort;
import com.woodlogix.application.environment.EnvironmentRow;
import com.woodlogix.application.hardware.DeleteHardware;
import com.woodlogix.application.hardware.HardwareCatalog;
import com.woodlogix.application.hardware.HardwareInput;
import com.woodlogix.application.material.MaterialCatalog;
import com.woodlogix.application.material.MaterialCatalogPort;
import com.woodlogix.application.material.MaterialInput;
import com.woodlogix.application.sales.SalesPipeline;
import com.woodlogix.application.sales.SalesProjectInput;
import com.woodlogix.application.transaction.TransactionPort;
import com.woodlogix.application.workpiece.ProjectWorkpiece;
import com.woodlogix.application.workpiece.ProjectWorkpieceInput;
import com.woodlogix.core.enums.MaterialType;
import com.woodlogix.core.enums.SalesStage;
import com.woodlogix.core.enums.VisibleEdge;
import com.woodlogix.server.WoodLogixServerApplication;
import com.woodlogix.server.event.CustomerChangedEvent;
import com.woodlogix.server.event.EnvironmentChangedEvent;
import com.woodlogix.server.persistence.entity.CustomerEntity;
import com.woodlogix.server.persistence.entity.FurnitureItemEntity;
import com.woodlogix.server.persistence.entity.FurniturePartEntity;
import com.woodlogix.server.persistence.entity.ProjectEnvironmentEntity;
import com.woodlogix.server.persistence.repository.CustomerRepository;
import com.woodlogix.server.persistence.repository.FurnitureItemRepository;
import com.woodlogix.server.persistence.repository.FurniturePartRepository;
import com.woodlogix.server.persistence.repository.ProjectEnvironmentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerPersistenceIntegrationTest {
    @TempDir
    Path temporaryDirectory;

    private ConfigurableApplicationContext context;
    private JdbcTemplate jdbc;

    @BeforeEach
    void startServerContext() {
        context = new SpringApplicationBuilder(
                WoodLogixServerApplication.class, EventTestConfiguration.class)
                .web(WebApplicationType.NONE)
                .run("--spring.main.banner-mode=off",
                        "--logging.level.root=ERROR",
                        "--logging.file.name=",
                        "--woodlogix.database.path="
                                + temporaryDirectory.resolve("persistence.db"));
        jdbc = new JdbcTemplate(context.getBean(DataSource.class));
    }

    @AfterEach
    void closeServerContext() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void appliesV1ValidatesSchemaAndEnablesForeignKeysOnEveryConnection() throws Exception {
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history "
                        + "WHERE version = '1' AND success = 1",
                Integer.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM sqlite_master "
                        + "WHERE type = 'table' AND name = 'project_estimate_workpieces'",
                Integer.class));

        DataSource dataSource = context.getBean(DataSource.class);
        for (int attempt = 0; attempt < 3; attempt++) {
            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("PRAGMA foreign_keys")) {
                assertTrue(result.next());
                assertEquals(1, result.getInt(1));
            }
        }
    }

    @Test
    void persistsApplicationUseCasesThroughAllAdapters() {
        CustomerCatalog customers = bean(CustomerCatalog.class);
        CustomerRow customer = customers.create(customerInput("Customer"));
        CustomerRow updatedCustomer = customers.update(customer.id(), customerInput("Updated"));
        assertEquals("Updated", updatedCustomer.fullName());
        assertEquals(1, customers.findAll().size());

        EnvironmentCatalog environments = bean(EnvironmentCatalog.class);
        EnvironmentRow environment = environments.create(customer.id(), "Kitchen");
        assertEquals(environment.id(), environments.findByCustomer(customer.id()).get(0).id());
        assertEquals("Pantry", environments.update(
                environment.id(), customer.id(), "Pantry").type());

        MaterialCatalog materials = bean(MaterialCatalog.class);
        var material = materials.create(materialInput("MDF"));
        var updatedMaterial = materials.update(material.id(), materialInput("Plywood"));
        assertEquals("Plywood", updatedMaterial.name());
        assertEquals(1, materials.findAll().size());

        EdgeBandingCatalog edgeBandings = bean(EdgeBandingCatalog.class);
        var edgeBanding = edgeBandings.create(edgeBandingInput("PVC"));
        assertEquals("ABS", edgeBandings.update(
                edgeBanding.id(), edgeBandingInput("ABS")).name());
        assertEquals(1, edgeBandings.findAll().size());

        HardwareCatalog hardwares = bean(HardwareCatalog.class);
        var hardware = hardwares.create(hardwareInput("Hinge"));
        assertEquals("Slide", hardwares.update(
                hardware.id(), hardwareInput("Slide")).name());
        assertEquals(1, hardwares.findAll().size());

        SalesPipeline pipeline = bean(SalesPipeline.class);
        var project = pipeline.create(new SalesProjectInput(
                customer.id(), environment.id(), "Project", money("250.00")));
        pipeline.move(project.id(), SalesStage.NEGOTIATION);
        assertEquals(SalesStage.NEGOTIATION, pipeline.findById(project.id()).stage());
        assertEquals(1, pipeline.findPipeline().get(SalesStage.NEGOTIATION).size());

        ProjectWorkpiece workpieces = bean(ProjectWorkpiece.class);
        var piece = workpieces.create(new ProjectWorkpieceInput(
                project.id(), "Panel", money("100"), money("50"), 2,
                material.id(), edgeBanding.id(), true, false, false, false));
        assertEquals(piece.id(), workpieces.findByProject(project.id()).get(0).id());
        assertEquals(1, workpieces.findAll().size());
        workpieces.update(piece.id(), new ProjectWorkpieceInput(
                project.id(), "Door", money("120"), money("60"), 1,
                material.id(), edgeBanding.id(), true, false, false, false));
        assertEquals("Door", workpieces.findByProject(project.id()).get(0).name());
        workpieces.delete(piece.id());
        assertTrue(workpieces.findAll().isEmpty());

        EstimateDraft drafts = bean(EstimateDraft.class);
        drafts.saveActiveDraft(draftInput(project.id(), material.id(), edgeBanding.id(),
                hardware.id()));
        var draft = drafts.findActiveDraft().orElseThrow();
        assertEquals(project.id(), draft.projectId());
        assertEquals(material.id(), draft.workpieces().get(0).materialId());
        assertEquals(hardware.id(), draft.hardwares().get(0).hardwareId());

        CustomerRow disposableCustomer = customers.create(customerInput("Disposable"));
        customers.delete(disposableCustomer.id());
        EnvironmentRow disposableEnvironment = environments.create(customer.id(), "Unused");
        environments.delete(disposableEnvironment.id());
        var disposableMaterial = materials.create(materialInput("Unused"));
        materials.delete(disposableMaterial.id());
        var disposableEdgeBanding = edgeBandings.create(edgeBandingInput("Unused"));
        edgeBandings.delete(disposableEdgeBanding.id());
        var disposableHardware = hardwares.create(hardwareInput("Unused"));
        bean(DeleteHardware.class).execute(disposableHardware.id());
    }

    @Test
    void materialAndEdgeBandingDeletionIsBlockedByProjectWorkpieceReferences() {
        CustomerRow customer = bean(CustomerCatalog.class).create(customerInput("Customer"));
        var material = bean(MaterialCatalog.class).create(materialInput("MDF"));
        var edgeBanding = bean(EdgeBandingCatalog.class).create(edgeBandingInput("PVC"));
        var project = bean(SalesPipeline.class).create(new SalesProjectInput(
                customer.id(), null, "Project", money("100")));
        bean(ProjectWorkpiece.class).create(new ProjectWorkpieceInput(
                project.id(), "Panel", money("100"), money("50"), 1,
                material.id(), edgeBanding.id(), true, false, false, false));

        assertEquals("error.material-has-related-records", assertThrows(
                IllegalStateException.class,
                () -> bean(MaterialCatalog.class).delete(material.id())).getMessage());
        assertEquals("error.edge-banding-has-related-records", assertThrows(
                IllegalStateException.class,
                () -> bean(EdgeBandingCatalog.class).delete(edgeBanding.id())).getMessage());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM materials WHERE id = ?", Integer.class, material.id()));
    }

    @Test
    void materialEdgeBandingAndHardwareDeletionIsBlockedByDraftReferences() {
        bean(CustomerCatalog.class).create(customerInput("Customer"));
        var material = bean(MaterialCatalog.class).create(materialInput("MDF"));
        var edgeBanding = bean(EdgeBandingCatalog.class).create(edgeBandingInput("PVC"));
        var hardware = bean(HardwareCatalog.class).create(hardwareInput("Hinge"));

        bean(EstimateDraft.class).saveActiveDraft(draftInput(
                null, material.id(), edgeBanding.id(), hardware.id()));

        assertEquals("error.material-has-related-records", assertThrows(
                IllegalStateException.class,
                () -> bean(MaterialCatalog.class).delete(material.id())).getMessage());
        assertEquals("error.edge-banding-has-related-records", assertThrows(
                IllegalStateException.class,
                () -> bean(EdgeBandingCatalog.class).delete(edgeBanding.id())).getMessage());
        assertEquals("error.hardware-has-related-records", assertThrows(
                IllegalStateException.class,
                () -> bean(DeleteHardware.class).execute(hardware.id())).getMessage());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM project_estimate_workpieces "
                        + "WHERE material_id = ? AND edge_banding_id = ?",
                Integer.class, material.id(), edgeBanding.id()));
    }

    @Test
    void environmentDeletionRespectsFurnitureAndSalesProjectReferences() {
        CustomerRow customer = bean(CustomerCatalog.class).create(customerInput("Customer"));
        CustomerEntity customerEntity = bean(CustomerRepository.class)
                .getReferenceById(customer.id());
        EnvironmentCatalog environments = bean(EnvironmentCatalog.class);
        ProjectEnvironmentRepository environmentRepository =
                bean(ProjectEnvironmentRepository.class);

        EnvironmentRow partEnvironment = environments.create(customer.id(), "Part");
        ProjectEnvironmentEntity partEntity = environmentRepository
                .getReferenceById(partEnvironment.id());
        bean(FurniturePartRepository.class).saveAndFlush(new FurniturePartEntity(
                partEntity, "Part", money("100"), money("50"), money("18"),
                MaterialType.MDF, Set.of(VisibleEdge.TOP)));

        EnvironmentRow itemEnvironment = environments.create(customer.id(), "Item");
        new TransactionTemplate(bean(PlatformTransactionManager.class))
                .executeWithoutResult(status -> {
                    ProjectEnvironmentEntity itemEntity = environmentRepository
                            .findById(itemEnvironment.id()).orElseThrow();
                    bean(FurnitureItemRepository.class).saveAndFlush(new FurnitureItemEntity(
                            customerEntity, itemEntity, "Furniture", "Cabinet", "Wood",
                            money("100"), money("200"), money("300")));
                });

        EnvironmentRow projectEnvironment = environments.create(customer.id(), "Project");
        bean(SalesPipeline.class).create(new SalesProjectInput(
                customer.id(), projectEnvironment.id(), "Project", money("100")));

        EnvironmentCatalogPort port = bean(EnvironmentCatalogPort.class);
        assertTrue(port.hasPartReferences(partEnvironment.id()), "furniture part reference");
        assertTrue(port.hasFurnitureItemReferences(itemEnvironment.id()), "furniture item reference");
        assertTrue(port.hasSalesProjectReferences(projectEnvironment.id()), "sales project reference");

        for (EnvironmentRow environment : List.of(
                partEnvironment, itemEnvironment, projectEnvironment)) {
            DataAccessException failure = assertThrows(
                    DataAccessException.class,
                    () -> environments.delete(environment.id()), environment.type());
            assertTrue(SqliteIntegrityErrors.isForeignKeyViolation(failure), environment.type());
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM project_environments WHERE id = ?",
                    Integer.class, environment.id()));
        }

        TransactionPort transactions = bean(TransactionPort.class);
        IllegalStateException foreignKeyFailure = assertThrows(
                IllegalStateException.class,
                () -> transactions.execute(() ->
                        port.deleteAllAndFlush(List.of(partEnvironment.id()))));
        assertEquals("error.environment-has-related-records", foreignKeyFailure.getMessage());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM project_environments WHERE id = ?",
                Integer.class, partEnvironment.id()));
    }

    @Test
    void rollsBackAFlushedUpdateWhenAnOuterTransactionFails() {
        MaterialCatalog materials = bean(MaterialCatalog.class);
        var material = materials.create(materialInput("Original"));
        TransactionTemplate outerTransaction = new TransactionTemplate(
                bean(PlatformTransactionManager.class));

        assertThrows(IllegalStateException.class, () -> outerTransaction.executeWithoutResult(status -> {
            materials.update(material.id(), materialInput("Changed"));
            assertEquals("Changed", jdbc.queryForObject(
                    "SELECT name FROM materials WHERE id = ?", String.class, material.id()));
            throw new IllegalStateException("rollback after flush");
        }));

        assertEquals("Original", jdbc.queryForObject(
                "SELECT name FROM materials WHERE id = ?", String.class, material.id()));
    }

    @Test
    void publishesCustomerAndEnvironmentEventsOnlyAfterCommit() {
        TestEventRecorder recorder = bean(TestEventRecorder.class);
        CustomerRow customer = bean(CustomerCatalog.class).create(customerInput("Committed"));
        assertEquals(List.of("customer"), recorder.events());

        recorder.clear();
        bean(EnvironmentCatalog.class).create(customer.id(), "Committed");
        assertEquals(List.of("environment"), recorder.events());

        recorder.clear();
        TransactionTemplate transaction = new TransactionTemplate(
                bean(PlatformTransactionManager.class));
        transaction.executeWithoutResult(status -> {
            bean(EnvironmentCatalog.class).create(customer.id(), "Rolled back");
            assertTrue(recorder.events().isEmpty());
            status.setRollbackOnly();
        });
        assertTrue(recorder.events().isEmpty());
        assertFalse(Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM project_environments "
                        + "WHERE environment_type = 'Rolled back')",
                Boolean.class)));
    }

    private <T> T bean(Class<T> type) {
        return context.getBean(type);
    }

    private static CustomerInput customerInput(String name) {
        return new CustomerInput(name, null, null, null);
    }

    private static MaterialInput materialInput(String name) {
        return new MaterialInput(name, "White", money("18"), money("2750.25"),
                money("1840.50"), money("200.00"));
    }

    private static EdgeBandingInput edgeBandingInput(String name) {
        return new EdgeBandingInput(name, money("22"), money("1.25"));
    }

    private static HardwareInput hardwareInput(String name) {
        return new HardwareInput(name, "unit", money("3.50"));
    }

    private static EstimateDraftInput draftInput(
            String projectId, String materialId, String edgeBandingId, String hardwareId
    ) {
        return new EstimateDraftInput(projectId, money("0"), money("1.5"),
                money("25.00"), money("2.50"), money("10"),
                List.of(new EstimateDraftInput.WorkpieceInput(
                        null, "Draft panel", money("100"), money("50"), 1,
                        materialId, edgeBandingId, true, false, false, false)),
                List.of(new EstimateDraftInput.HardwareInput(hardwareId, 2)));
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class EventTestConfiguration {
        @Bean
        TestEventRecorder testEventRecorder() {
            return new TestEventRecorder();
        }
    }

    static class TestEventRecorder {
        private final List<String> recordedEvents = new CopyOnWriteArrayList<>();

        @TransactionalEventListener
        public void onCustomerChanged(CustomerChangedEvent event) {
            recordedEvents.add("customer");
        }

        @TransactionalEventListener
        public void onEnvironmentChanged(EnvironmentChangedEvent event) {
            recordedEvents.add("environment");
        }

        List<String> events() {
            return List.copyOf(recordedEvents);
        }

        void clear() {
            recordedEvents.clear();
        }
    }
}
