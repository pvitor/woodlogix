package com.woodlogix.desktop.persistence;

import com.woodlogix.application.edgebanding.EdgeBandingCatalog;
import com.woodlogix.application.edgebanding.EdgeBandingCatalogPort;
import com.woodlogix.application.edgebanding.EdgeBandingInput;
import com.woodlogix.application.edgebanding.EdgeBandingRow;
import com.woodlogix.desktop.WoodLogixApplication;
import com.woodlogix.desktop.service.CustomerData;
import com.woodlogix.desktop.service.LocalCustomerService;
import com.woodlogix.desktop.service.EdgeBandingData;
import com.woodlogix.desktop.service.EdgeBandingService;
import com.woodlogix.desktop.service.EstimateDraftData;
import com.woodlogix.desktop.service.EstimateDraftService;
import com.woodlogix.desktop.service.MaterialData;
import com.woodlogix.desktop.service.MaterialService;
import com.woodlogix.desktop.service.ProjectWorkpieceData;
import com.woodlogix.desktop.service.ProjectWorkpieceService;
import com.woodlogix.desktop.service.SalesPipelineService;
import com.woodlogix.desktop.service.SalesProjectData;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class EdgeBandingCatalogIntegrationTest {
    @TempDir Path temporaryDirectory;
    private ConfigurableApplicationContext context;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        context = new SpringApplicationBuilder(WoodLogixApplication.class)
                .web(WebApplicationType.NONE).run("--spring.main.banner-mode=off", "--logging.level.root=ERROR",
                        "--logging.file.name=", "--woodlogix.database.path=" + temporaryDirectory.resolve("edges.db"));
        jdbc = new JdbcTemplate(context.getBean(DataSource.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1' AND success = 1", Integer.class));
    }

    @AfterEach
    void tearDown() {
        if (context != null) context.close();
    }

    @Test
    void persistsUpdatesAndOrdersAllFields() {
        var service = context.getBean(EdgeBandingService.class);
        var d = service.create(data("B", "10", "12.34"));
        var c = service.create(data("A", "30", "0"));
        var b = service.create(data("A", "20", "1.25"));
        var a = service.create(data("A", "10", "999.99"));
        assertNotNull(a.id());
        var updated = service.update(d.id(), data("B", "22.50", "34.56"));
        assertEquals(d.id(), updated.id());
        assertEquals(0, new BigDecimal("34.56").compareTo(updated.pricePerMeter()));
        var rows = service.findAll();
        assertEquals(List.of(a.id(), b.id(), c.id(), d.id()), rows.stream().map(EdgeBandingRow::id).toList());
        assertEquals(0, new BigDecimal("999.99").compareTo(rows.get(0).pricePerMeter()));
        assertEquals(0, BigDecimal.ZERO.compareTo(rows.get(2).pricePerMeter()));
        assertEquals(0, new BigDecimal("22.50").compareTo(rows.get(3).widthMm()));
        assertEquals(0, new BigDecimal("34.56").compareTo(rows.get(3).pricePerMeter()));
        assertEquals("B", rows.get(3).name());
    }

    @Test
    void validatesFacadeAndMissingId() {
        var service = context.getBean(EdgeBandingService.class);
        var invalid = List.of(data("", "10", "0"), data(" ", "10", "0"), data("N", "0", "0"),
                data("N", "-1", "0"), data("N", "10", "-1"),
                new EdgeBandingData("N", null, BigDecimal.ZERO), new EdgeBandingData("N", BigDecimal.TEN, null));
        for (var value : invalid) assertThrows(ConstraintViolationException.class, () -> service.create(value));
        assertEquals("error.edge-banding-not-found", assertThrows(EntityNotFoundException.class,
                () -> service.update("missing", data("N", "10", "0"))).getMessage());
        assertEquals("error.edge-banding-not-found", assertThrows(EntityNotFoundException.class,
                () -> service.delete("missing")).getMessage());
        assertTrue(rows("edge_bandings").isEmpty());
    }

    @Test
    void rejectsDeletionReferencedByProjectWorkpieceBeforeDeleting() {
        var service = context.getBean(EdgeBandingService.class);
        var edge = service.create(data("PVC", "22", "3"));
        createProjectWorkpiece(edge.id());
        var before = rows("edge_bandings");
        var failure = assertThrows(IllegalStateException.class, () -> service.delete(edge.id()));
        assertEquals("error.edge-banding-has-related-records", failure.getMessage());
        assertNull(failure.getCause());
        assertEquals(before, rows("edge_bandings"));
    }

    @Test
    void rejectsDeletionReferencedByDraftWorkpieceBeforeDeleting() {
        var service = context.getBean(EdgeBandingService.class);
        var edge = service.create(data("PVC", "22", "3"));
        saveDraftWorkpiece(edge.id());
        var before = rows("edge_bandings");
        var failure = assertThrows(IllegalStateException.class, () -> service.delete(edge.id()));
        assertEquals("error.edge-banding-has-related-records", failure.getMessage());
        assertEquals(before, rows("edge_bandings"));
    }

    @Test
    void physicalForeignKeyBlocksDeletionReferencedByProjectWorkpiece() {
        var edge = context.getBean(EdgeBandingService.class).create(data("PVC", "22", "3"));
        createProjectWorkpiece(edge.id());
        assertForeignKeyTranslation(edge.id(), "project_workpieces");
    }

    @Test
    void physicalForeignKeyBlocksDeletionReferencedByDraftWorkpiece() {
        var edge = context.getBean(EdgeBandingService.class).create(data("PVC", "22", "3"));
        saveDraftWorkpiece(edge.id());
        assertForeignKeyTranslation(edge.id(), "project_estimate_workpieces");
    }

    private void assertForeignKeyTranslation(String edgeId, String referencingTable) {
        var service = context.getBean(EdgeBandingService.class);
        var before = rows("edge_bandings");
        var references = rows(referencingTable);
        var catalog = context.getBean(EdgeBandingCatalog.class);
        var real = context.getBean(EdgeBandingCatalogPort.class);
        var intercept = mock(EdgeBandingCatalogPort.class, delegatesTo(real));
        doReturn(false).when(intercept).isReferencedByProjectWorkpiece(anyString());
        doReturn(false).when(intercept).isReferencedByDraftWorkpiece(anyString());
        var observed = new AtomicBoolean();
        doAnswer(invocation -> {
            assertTransactionAndForeignKeys();
            observed.set(true);
            real.deleteAndFlush(invocation.getArgument(0));
            return null;
        }).when(intercept).deleteAndFlush(anyString());
        ReflectionTestUtils.setField(catalog, "edgeBandings", intercept);
        try {
            var failure = assertThrows(IllegalStateException.class, () -> service.delete(edgeId));
            assertEquals("error.edge-banding-has-related-records", failure.getMessage());
            assertTrue(SqliteIntegrityErrors.isForeignKeyViolation(failure.getCause()));
        } finally {
            ReflectionTestUtils.setField(catalog, "edgeBandings", real);
        }
        assertTrue(observed.get());
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertEquals(before, rows("edge_bandings"));
        assertEquals(references, rows(referencingTable));
    }

    @Test
    void propagatesNonForeignKeyFailureAndRollsBackRealFlushedWrite() {
        var service = context.getBean(EdgeBandingService.class);
        var edge = service.create(data("Original", "10", "10"));
        var before = rows("edge_bandings");
        var catalog = context.getBean(EdgeBandingCatalog.class);
        var real = context.getBean(EdgeBandingCatalogPort.class);
        var intercept = mock(EdgeBandingCatalogPort.class, delegatesTo(real));
        var observed = new AtomicBoolean();
        doAnswer(invocation -> {
            Optional<EdgeBandingRow> updated = real.update(invocation.getArgument(0), invocation.getArgument(1));
            assertTrue(updated.isPresent());
            assertTransactionAndForeignKeys();
            assertEquals("Changed", jdbc.queryForObject("SELECT name FROM edge_bandings WHERE id = ?",
                    String.class, edge.id()));
            observed.set(true);
            jdbc.update("INSERT INTO edge_bandings (id) VALUES (?)", "invalid");
            throw new AssertionError("Expected SQLite constraint failure");
        }).when(intercept).update(anyString(), any(EdgeBandingInput.class));
        ReflectionTestUtils.setField(catalog, "edgeBandings", intercept);
        try {
            var failure = assertThrows(UncategorizedSQLException.class,
                    () -> service.update(edge.id(), data("Changed", "10", "10")));
            assertFalse(SqliteIntegrityErrors.isForeignKeyViolation(failure));
        } finally {
            ReflectionTestUtils.setField(catalog, "edgeBandings", real);
        }
        assertTrue(observed.get());
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertEquals(before, rows("edge_bandings"));
    }

    @Test
    void rollsBackEarlierFlushedUpdateWhenDeleteFlushViolatesForeignKey() {
        var service = context.getBean(EdgeBandingService.class);
        var edge = service.create(data("Original", "10", "10"));
        createProjectWorkpiece(edge.id());
        var before = rows("edge_bandings");
        var references = rows("project_workpieces");
        var catalog = context.getBean(EdgeBandingCatalog.class);
        var real = context.getBean(EdgeBandingCatalogPort.class);
        var intercept = mock(EdgeBandingCatalogPort.class, delegatesTo(real));
        doReturn(false).when(intercept).isReferencedByProjectWorkpiece(anyString());
        var observed = new AtomicBoolean();
        doAnswer(invocation -> {
            assertTransactionAndForeignKeys();
            assertEquals("Changed", jdbc.queryForObject("SELECT name FROM edge_bandings WHERE id = ?",
                    String.class, edge.id()));
            observed.set(true);
            real.deleteAndFlush(invocation.getArgument(0));
            return null;
        }).when(intercept).deleteAndFlush(anyString());
        ReflectionTestUtils.setField(catalog, "edgeBandings", intercept);
        try {
            var failure = assertThrows(IllegalStateException.class, () ->
                    new TransactionTemplate(context.getBean(PlatformTransactionManager.class))
                            .executeWithoutResult(status -> {
                                service.update(edge.id(), data("Changed", "10", "10"));
                                service.delete(edge.id());
                            }));
            assertEquals("error.edge-banding-has-related-records", failure.getMessage());
            assertTrue(SqliteIntegrityErrors.isForeignKeyViolation(failure));
        } finally {
            ReflectionTestUtils.setField(catalog, "edgeBandings", real);
        }
        assertTrue(observed.get());
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertEquals(before, rows("edge_bandings"));
        assertEquals(references, rows("project_workpieces"));
    }

    @Test
    void propagatesNonForeignKeyIntegrityErrorOnDelete() {
        var service = context.getBean(EdgeBandingService.class);
        var edge = service.create(data("Original", "10", "10"));
        var before = rows("edge_bandings");
        var catalog = context.getBean(EdgeBandingCatalog.class);
        var real = context.getBean(EdgeBandingCatalogPort.class);
        var intercept = mock(EdgeBandingCatalogPort.class, delegatesTo(real));
        var failure = new DataIntegrityViolationException("unrelated integrity error");
        doAnswer(invocation -> {
            real.deleteAndFlush(invocation.getArgument(0));
            throw failure;
        }).when(intercept).deleteAndFlush(anyString());
        ReflectionTestUtils.setField(catalog, "edgeBandings", intercept);
        try {
            assertSame(failure, assertThrows(DataIntegrityViolationException.class,
                    () -> service.delete(edge.id())));
        } finally {
            ReflectionTestUtils.setField(catalog, "edgeBandings", real);
        }
        assertEquals(before, rows("edge_bandings"));
    }

    private void createProjectWorkpiece(String edgeId) {
        var project = createProject();
        var material = createMaterial();
        context.getBean(ProjectWorkpieceService.class).create(new ProjectWorkpieceData(
                project, "Panel", BigDecimal.TEN, BigDecimal.TEN, 1, material, edgeId,
                true, false, false, false));
    }

    private void saveDraftWorkpiece(String edgeId) {
        var material = createMaterial();
        var piece = new EstimateDraftData.EstimateDraftWorkpieceData(null, "Panel", BigDecimal.TEN,
                BigDecimal.TEN, 1, material, edgeId, true, false, false, false);
        var zero = BigDecimal.ZERO;
        context.getBean(EstimateDraftService.class).saveActiveDraft(new EstimateDraftData(
                null, zero, zero, zero, zero, zero, List.of(piece), List.of()));
    }

    private String createProject() {
        var customer = context.getBean(LocalCustomerService.class)
                .create(new CustomerData("Customer", null, null, null));
        return context.getBean(SalesPipelineService.class).create(
                new SalesProjectData(customer.id(), null, "Project", new BigDecimal("1200"))).id();
    }

    private String createMaterial() {
        return context.getBean(MaterialService.class).create(new MaterialData(
                "MDF", "White", new BigDecimal("18"), new BigDecimal("2750"),
                new BigDecimal("1840"), new BigDecimal("200"))).id();
    }

    private void assertTransactionAndForeignKeys() {
        assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
        var em = EntityManagerFactoryUtils.getTransactionalEntityManager(context.getBean(EntityManagerFactory.class));
        assertNotNull(em);
        assertTrue(em.isJoinedToTransaction());
        int enabled = em.unwrap(Session.class).doReturningWork(connection -> {
            try (var statement = connection.createStatement(); var result = statement.executeQuery("PRAGMA foreign_keys")) {
                assertTrue(result.next());
                return result.getInt(1);
            }
        });
        assertEquals(1, enabled);
    }

    private EdgeBandingData data(String name, String width, String price) {
        return new EdgeBandingData(name, new BigDecimal(width), new BigDecimal(price));
    }

    private List<Map<String, Object>> rows(String table) {
        return jdbc.queryForList("SELECT * FROM " + table + " ORDER BY id");
    }
}
