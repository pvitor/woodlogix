package com.woodlogix.desktop.persistence;

import com.woodlogix.desktop.WoodLogixApplication;
import com.woodlogix.desktop.persistence.entity.EstimateDraftEntity;
import com.woodlogix.desktop.persistence.entity.ProjectEnvironmentEntity;
import com.woodlogix.desktop.persistence.repository.EstimateDraftRepository;
import com.woodlogix.desktop.persistence.repository.ProjectEnvironmentRepository;
import com.woodlogix.desktop.service.CustomerData;
import com.woodlogix.desktop.service.CustomerService;
import com.woodlogix.desktop.service.EdgeBandingData;
import com.woodlogix.desktop.service.EdgeBandingService;
import com.woodlogix.desktop.service.EnvironmentRow;
import com.woodlogix.desktop.service.EnvironmentService;
import com.woodlogix.desktop.service.EstimateDraftData;
import com.woodlogix.desktop.service.EstimateDraftService;
import com.woodlogix.desktop.service.HardwareData;
import com.woodlogix.desktop.service.HardwareService;
import com.woodlogix.desktop.service.MaterialData;
import com.woodlogix.desktop.service.MaterialService;
import com.woodlogix.desktop.service.SalesPipelineService;
import com.woodlogix.desktop.service.SalesProjectData;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityNotFoundException;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class EnvironmentAndDraftCharacterizationTest {
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
                        "--woodlogix.database.path="
                                + temporaryDirectory.resolve("characterization.db"));
        jdbc = new JdbcTemplate(context.getBean(javax.sql.DataSource.class));
        assertEquals(1, count("PRAGMA foreign_keys"), "SQLite foreign keys must be enabled");
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void synchronizesNormalizedNamesAndPreservesIdsAcrossChanges() {
        var customer = context.getBean(CustomerService.class)
                .create(new CustomerData("Customer", null, null, null));
        var service = context.getBean(EnvironmentService.class);
        var initial = service.saveOrUpdateCustomerEnvironments(
                customer.id(), List.of("Living Room", "Bedroom"));
        String livingId = initial.stream()
                .filter(row -> row.type().equals("Living Room"))
                .findFirst().orElseThrow().id();
        String bedroomId = initial.stream()
                .filter(row -> row.type().equals("Bedroom"))
                .findFirst().orElseThrow().id();

        var updated = service.saveOrUpdateCustomerEnvironments(customer.id(),
                List.of("  LIVING_room  ", "living-room", "Pantry"));

        assertEquals(2, updated.size());
        assertTrue(updated.stream().anyMatch(row -> row.id().equals(livingId)
                && row.type().equals("LIVING_room") && row.customerId().equals(customer.id())));
        assertTrue(updated.stream().anyMatch(row -> row.type().equals("Pantry")
                && !row.id().equals(livingId) && row.customerId().equals(customer.id())));
        assertFalse(context.getBean(ProjectEnvironmentRepository.class).existsById(bedroomId));
        assertEquals(sortedRows(updated), sortedRows(service.findByCustomer(customer.id())));
        assertTrue(service.saveOrUpdateCustomerEnvironments(customer.id(), List.of()).isEmpty());
        assertTrue(service.findByCustomer(customer.id()).isEmpty());
    }

    @Test
    void rejectsReplacingEnvironmentReferencedBySalesProject() {
        var customer = context.getBean(CustomerService.class)
                .create(new CustomerData("Customer", null, null, null));
        var environments = context.getBean(EnvironmentService.class);
        var original = environments.saveOrUpdateCustomerEnvironments(
                customer.id(), List.of("Kitchen"));
        var project = context.getBean(SalesPipelineService.class).create(
                new SalesProjectData(customer.id(), original.get(0).id(),
                        "Project", new BigDecimal("1250")));

        var failure = assertThrows(IllegalStateException.class, () ->
                environments.saveOrUpdateCustomerEnvironments(customer.id(), List.of("Pantry")));

        assertEquals("error.environment-has-related-records", failure.getMessage());
        assertEquals(original, environments.findByCustomer(customer.id()));
        assertEquals("Kitchen", context.getBean(SalesPipelineService.class)
                .findById(project.id()).environmentName());
    }

    @Test
    void rollsBackEnvironmentReplacementAfterRealFlushedWrites() {
        var customer = context.getBean(CustomerService.class)
                .create(new CustomerData("Customer", null, null, null));
        var service = context.getBean(EnvironmentService.class);
        var original = service.saveOrUpdateCustomerEnvironments(
                customer.id(), List.of("Kitchen", "Bedroom"));
        var before = sortedRows(original);
        var physicalBefore = environmentRows();
        assertEquals(2, physicalBefore.size());
        String removedId = original.stream().filter(row -> row.type().equals("Bedroom"))
                .findFirst().orElseThrow().id();
        var realRepository = context.getBean(ProjectEnvironmentRepository.class);
        var intercepted = mock(ProjectEnvironmentRepository.class, delegatesTo(realRepository));
        var target = AopTestUtils.<EnvironmentService>getUltimateTargetObject(service);
        var observedWrite = new AtomicBoolean();

        doAnswer(invocation -> {
            Iterable<ProjectEnvironmentEntity> entities = invocation.getArgument(0);
            realRepository.saveAllAndFlush(entities);
            assertEquals(0L, transactionalCount(
                    "SELECT COUNT(e) FROM ProjectEnvironmentEntity e WHERE e.id = ?1", removedId));
            assertEquals(1L, transactionalCount(
                    "SELECT COUNT(e) FROM ProjectEnvironmentEntity e WHERE e.customer.id = ?1 AND e.type = ?2",
                    customer.id(), "Pantry"));
            observedWrite.set(true);
            throw new IllegalStateException("injected environment failure");
        }).when(intercepted).saveAllAndFlush(any());

        ReflectionTestUtils.setField(target, "environmentRepository", intercepted);
        try {
            var failure = assertThrows(IllegalStateException.class, () ->
                    service.saveOrUpdateCustomerEnvironments(
                            customer.id(), List.of("KITCHEN", "Pantry")));
            assertEquals("injected environment failure", failure.getMessage());
        } finally {
            ReflectionTestUtils.setField(target, "environmentRepository", realRepository);
        }

        assertTrue(observedWrite.get());
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertEquals(physicalBefore, environmentRows());
        assertEquals(before, sortedRows(service.findByCustomer(customer.id())));
        assertEquals(1, count("SELECT COUNT(*) FROM project_environments WHERE id = ?", removedId));
        assertEquals(0, count("SELECT COUNT(*) FROM project_environments WHERE customer_id = ? AND environment_type = ?",
                customer.id(), "Pantry"));
    }

    @Test
    void restoresDraftAndReplacesAllChildrenIncludingOldRows() {
        var fixture = createDraftFixture();
        var service = context.getBean(EstimateDraftService.class);
        assertTrue(service.findActiveDraft().isEmpty());
        var initial = draft(fixture.projectId(), "Original", fixture, 2, 3);
        service.saveActiveDraft(initial);
        assertEquals(initial, service.findActiveDraft().orElseThrow());
        assertEquals(1, count("SELECT COUNT(*) FROM project_estimates WHERE id = ?",
                EstimateDraftEntity.ACTIVE_DRAFT_ID));
        var oldPieces = childIds("project_estimate_workpieces");
        var oldHardwares = childIds("project_estimate_hardwares");

        var replacement = replacementDraft(fixture);
        service.saveActiveDraft(replacement);

        var restored = service.findActiveDraft().orElseThrow();
        assertEquals(replacement.projectId(), restored.projectId());
        assertEquals(replacement.wasteFactor(), restored.wasteFactor());
        assertEquals(replacement.estimatedHours(), restored.estimatedHours());
        assertEquals(replacement.hourlyRate(), restored.hourlyRate());
        assertEquals(replacement.machineRate(), restored.machineRate());
        assertEquals(replacement.profitMargin(), restored.profitMargin());
        assertEquals(Set.copyOf(replacement.workpieces()), Set.copyOf(restored.workpieces()));
        assertEquals(Set.copyOf(replacement.hardwares()), Set.copyOf(restored.hardwares()));
        assertEquals(2, childIds("project_estimate_workpieces").size());
        assertEquals(2, childIds("project_estimate_hardwares").size());
        assertNotEquals(oldPieces, childIds("project_estimate_workpieces"));
        assertNotEquals(oldHardwares, childIds("project_estimate_hardwares"));
        assertEquals(0, count("SELECT COUNT(*) FROM project_estimate_workpieces WHERE id = ?",
                oldPieces.get(0)));
        assertEquals(0, count("SELECT COUNT(*) FROM project_estimate_hardwares WHERE id = ?",
                oldHardwares.get(0)));

        var empty = new EstimateDraftData(null, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of(), List.of());
        service.saveActiveDraft(empty);
        assertEquals(empty, service.findActiveDraft().orElseThrow());
        assertTrue(childIds("project_estimate_workpieces").isEmpty());
        assertTrue(childIds("project_estimate_hardwares").isEmpty());
    }

    @Test
    void rejectsMissingDraftReferencesAndPreservesPreviousDraft() {
        var fixture = createDraftFixture();
        var service = context.getBean(EstimateDraftService.class);
        service.saveActiveDraft(draft(fixture.projectId(), "Original", fixture, 2, 3));
        var before = service.findActiveDraft().orElseThrow();
        var oldPieces = childIds("project_estimate_workpieces");
        var oldHardwares = childIds("project_estimate_hardwares");
        var missingMaterial = new DraftFixture(fixture.projectId(), "missing-material",
                fixture.edgeId(), fixture.hardwareId());
        var missingEdge = new DraftFixture(fixture.projectId(), fixture.materialId(),
                "missing-edge", fixture.hardwareId());
        var missingHardware = new DraftFixture(fixture.projectId(), fixture.materialId(),
                fixture.edgeId(), "missing-hardware");

        for (var invalid : List.of(
                draft("missing-project", "Invalid", fixture, 2, 3),
                draft(fixture.projectId(), "Invalid", missingMaterial, 2, 3),
                draft(fixture.projectId(), "Invalid", missingEdge, 2, 3),
                draft(fixture.projectId(), "Invalid", missingHardware, 2, 3))) {
            assertThrows(EntityNotFoundException.class, () -> service.saveActiveDraft(invalid));
            assertEquals(before, service.findActiveDraft().orElseThrow());
            assertEquals(oldPieces, childIds("project_estimate_workpieces"));
            assertEquals(oldHardwares, childIds("project_estimate_hardwares"));
        }

        var noEdge = new DraftFixture(fixture.projectId(), fixture.materialId(),
                null, fixture.hardwareId());
        assertThrows(IllegalArgumentException.class,
                () -> service.saveActiveDraft(draft(fixture.projectId(), "Invalid", noEdge, 2, 3)));
        assertThrows(IllegalArgumentException.class,
                () -> service.saveActiveDraft(draft(fixture.projectId(), "Invalid", fixture, 0, 3)));
        assertThrows(IllegalArgumentException.class,
                () -> service.saveActiveDraft(draft(fixture.projectId(), "Invalid", fixture, 2, 0)));
        assertEquals(before, service.findActiveDraft().orElseThrow());
        assertEquals(oldPieces, childIds("project_estimate_workpieces"));
        assertEquals(oldHardwares, childIds("project_estimate_hardwares"));
    }

    @Test
    void rollsBackDraftReplacementAfterRealFlushedWrites() {
        var fixture = createDraftFixture();
        var service = context.getBean(EstimateDraftService.class);
        service.saveActiveDraft(draft(fixture.projectId(), "Original", fixture, 2, 3));
        var before = service.findActiveDraft().orElseThrow();
        var headerBefore = draftHeaderRows();
        var piecesBefore = draftWorkpieceRows();
        var hardwaresBefore = draftHardwareRows();
        assertEquals(1, headerBefore.size());
        assertEquals(1, piecesBefore.size());
        assertEquals(1, hardwaresBefore.size());
        var oldPieces = childIds("project_estimate_workpieces");
        var oldHardwares = childIds("project_estimate_hardwares");
        var realRepository = context.getBean(EstimateDraftRepository.class);
        var intercepted = mock(EstimateDraftRepository.class, delegatesTo(realRepository));
        var target = AopTestUtils.<EstimateDraftService>getUltimateTargetObject(service);
        var observedWrite = new AtomicBoolean();

        doAnswer(invocation -> {
            EstimateDraftEntity entity = invocation.getArgument(0);
            realRepository.saveAndFlush(entity);
            assertEquals(0L, transactionalCount(
                    "SELECT COUNT(w) FROM EstimateDraftWorkpieceEntity w WHERE w.id = ?1",
                    oldPieces.get(0)));
            assertEquals(0L, transactionalCount(
                    "SELECT COUNT(h) FROM EstimateDraftHardwareEntity h WHERE h.id = ?1",
                    oldHardwares.get(0)));
            assertEquals(1L, transactionalCount(
                    "SELECT COUNT(w) FROM EstimateDraftWorkpieceEntity w WHERE w.name = ?1 AND w.quantity = ?2",
                    "Replacement", 7));
            assertEquals(1L, transactionalCount(
                    "SELECT COUNT(h) FROM EstimateDraftHardwareEntity h WHERE h.quantity = ?1", 9));
            observedWrite.set(true);
            throw new IllegalStateException("injected draft failure");
        }).when(intercepted).saveAndFlush(any());

        ReflectionTestUtils.setField(target, "draftRepository", intercepted);
        try {
            var failure = assertThrows(IllegalStateException.class, () ->
                    service.saveActiveDraft(replacementDraft(fixture)));
            assertEquals("injected draft failure", failure.getMessage());
        } finally {
            ReflectionTestUtils.setField(target, "draftRepository", realRepository);
        }

        assertTrue(observedWrite.get());
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertEquals(headerBefore, draftHeaderRows());
        assertEquals(piecesBefore, draftWorkpieceRows());
        assertEquals(hardwaresBefore, draftHardwareRows());
        assertEquals(before, service.findActiveDraft().orElseThrow());
        assertEquals(oldPieces, childIds("project_estimate_workpieces"));
        assertEquals(oldHardwares, childIds("project_estimate_hardwares"));
        assertEquals(0, count("SELECT COUNT(*) FROM project_estimate_workpieces WHERE name = ?",
                "Replacement"));
    }

    private DraftFixture createDraftFixture() {
        var customer = context.getBean(CustomerService.class)
                .create(new CustomerData("Customer", null, null, null));
        var project = context.getBean(SalesPipelineService.class).create(
                new SalesProjectData(customer.id(), null, "Project", new BigDecimal("1200")));
        var material = context.getBean(MaterialService.class).create(new MaterialData(
                "MDF", "White", new BigDecimal("18"), new BigDecimal("2750"),
                new BigDecimal("1840"), new BigDecimal("200")));
        var edge = context.getBean(EdgeBandingService.class).create(
                new EdgeBandingData("PVC", new BigDecimal("22"), new BigDecimal("3")));
        var hardware = context.getBean(HardwareService.class).create(
                new HardwareData("Hinge", "UNIT", new BigDecimal("14")));
        return new DraftFixture(project.id(), material.id(), edge.getId(), hardware.id());
    }

    private static EstimateDraftData draft(
            String projectId, String name, DraftFixture fixture, int pieces, int hardware
    ) {
        var workpiece = new EstimateDraftData.EstimateDraftWorkpieceData(
                "project-piece-id", name, new BigDecimal("1200"), new BigDecimal("600"),
                pieces, fixture.materialId(), fixture.edgeId(), true, false, true, false);
        var hardwareUsage = new EstimateDraftData.EstimateDraftHardwareData(
                fixture.hardwareId(), hardware);
        return new EstimateDraftData(projectId, new BigDecimal("12.5"), new BigDecimal("3.5"),
                new BigDecimal("42"), new BigDecimal("18"), new BigDecimal("35"),
                List.of(workpiece), List.of(hardwareUsage));
    }

    private static EstimateDraftData replacementDraft(DraftFixture fixture) {
        var first = draft(null, "Replacement", fixture, 7, 9);
        var second = draft(null, "Second", fixture, 4, 5);
        return new EstimateDraftData(null, new BigDecimal("20"), new BigDecimal("4"),
                new BigDecimal("50"), new BigDecimal("25"), new BigDecimal("40"),
                List.of(first.workpieces().get(0), second.workpieces().get(0)),
                List.of(first.hardwares().get(0), second.hardwares().get(0)));
    }

    private static List<EnvironmentRow> sortedRows(List<EnvironmentRow> rows) {
        return rows.stream().sorted(Comparator.comparing(EnvironmentRow::id)).toList();
    }

    private int count(String sql, Object... parameters) {
        return jdbc.queryForObject(sql, Integer.class, parameters);
    }

    private long transactionalCount(String jpql, Object... parameters) {
        assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
        var entityManager = EntityManagerFactoryUtils.getTransactionalEntityManager(
                context.getBean(EntityManagerFactory.class));
        assertTrue(entityManager != null && entityManager.isJoinedToTransaction());
        int foreignKeys = entityManager.unwrap(Session.class).doReturningWork(connection -> {
            try (var statement = connection.createStatement();
                 var result = statement.executeQuery("PRAGMA foreign_keys")) {
                if (!result.next()) {
                    throw new java.sql.SQLException("SQLite foreign_keys pragma returned no rows");
                }
                return result.getInt(1);
            }
        });
        assertEquals(1, foreignKeys, "Transactional SQLite connection must enforce foreign keys");
        var query = entityManager.createQuery(jpql, Long.class);
        for (int i = 0; i < parameters.length; i++) {
            query.setParameter(i + 1, parameters[i]);
        }
        return query.getSingleResult();
    }

    private List<Map<String, Object>> environmentRows() {
        return jdbc.queryForList("SELECT * FROM project_environments ORDER BY id");
    }

    private List<Map<String, Object>> draftHeaderRows() {
        return jdbc.queryForList("SELECT * FROM project_estimates ORDER BY id");
    }

    private List<Map<String, Object>> draftWorkpieceRows() {
        return jdbc.queryForList("SELECT * FROM project_estimate_workpieces ORDER BY id");
    }

    private List<Map<String, Object>> draftHardwareRows() {
        return jdbc.queryForList("SELECT * FROM project_estimate_hardwares ORDER BY id");
    }

    private List<String> childIds(String table) {
        return jdbc.queryForList("SELECT id FROM " + table + " ORDER BY id", String.class);
    }

    private record DraftFixture(String projectId, String materialId, String edgeId, String hardwareId) {
    }
}
