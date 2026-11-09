package com.woodlogix.desktop.persistence;

import com.woodlogix.application.material.MaterialCatalog;
import com.woodlogix.application.material.MaterialCatalogPort;
import com.woodlogix.application.material.MaterialInput;
import com.woodlogix.application.material.MaterialRow;
import com.woodlogix.desktop.WoodLogixApplication;
import com.woodlogix.desktop.service.EstimateDraftData;
import com.woodlogix.desktop.service.EstimateDraftService;
import com.woodlogix.desktop.service.MaterialData;
import com.woodlogix.desktop.service.MaterialService;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.PlatformTransactionManager;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class MaterialCatalogIntegrationTest {
    @TempDir Path temporaryDirectory;
    private ConfigurableApplicationContext context;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        context = new SpringApplicationBuilder(WoodLogixApplication.class)
                .web(WebApplicationType.NONE).run("--spring.main.banner-mode=off", "--logging.level.root=ERROR",
                        "--logging.file.name=", "--woodlogix.database.path=" + temporaryDirectory.resolve("materials.db"));
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
        var service = context.getBean(MaterialService.class);
        var d = service.create(data("B", "A", "1", "12.34"));
        var c = service.create(data("A", "B", "1", "0"));
        var b = service.create(data("A", "A", "2", "1.25"));
        var a = service.create(data("A", "A", "1", "999.99"));
        assertNotNull(a.id());
        var updated = service.update(d.id(), data("B", "A", "1.25", "34.56"));
        assertEquals(d.id(), updated.id());
        assertEquals(0, new BigDecimal("34.56").compareTo(updated.pricePerSheet()));
        var rows = service.findAll();
        assertEquals(List.of(a.id(), b.id(), c.id(), d.id()), rows.stream().map(MaterialRow::id).toList());
        assertEquals(0, new BigDecimal("999.99").compareTo(rows.get(0).pricePerSheet()));
        assertEquals(0, new BigDecimal("1.25").compareTo(rows.get(3).thicknessMm()));
        assertEquals(0, new BigDecimal("34.56").compareTo(rows.get(3).pricePerSheet()));
        assertEquals(0, new BigDecimal("2750.25").compareTo(rows.get(3).lengthMm()));
        assertEquals(0, new BigDecimal("1840.50").compareTo(rows.get(3).widthMm()));
    }

    @Test
    void validatesFacadeAndMissingId() {
        var service = context.getBean(MaterialService.class);
        var invalid = List.of(data("", "C", "1", "0"), data("N", " ", "1", "0"),
                data("N", "C", "0", "0"), data("N", "C", "1", "-1"),
                new MaterialData("N", "C", BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ZERO),
                new MaterialData("N", "C", BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO),
                new MaterialData("N", "C", null, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO));
        for (var value : invalid) assertThrows(ConstraintViolationException.class, () -> service.create(value));
        assertEquals("error.material-not-found", assertThrows(EntityNotFoundException.class,
                () -> service.update("missing", data("N", "C", "1", "0"))).getMessage());
        assertTrue(rows("materials").isEmpty());
    }

    @Test
    void translatesRealForeignKeyAndPreservesRows() {
        var service = context.getBean(MaterialService.class);
        var material = service.create(data("MDF", "White", "18", "200"));
        var piece = new EstimateDraftData.EstimateDraftWorkpieceData(null, "Panel", BigDecimal.TEN,
                BigDecimal.TEN, 1, material.id(), null, false, false, false, false);
        var zero = BigDecimal.ZERO;
        context.getBean(EstimateDraftService.class).saveActiveDraft(new EstimateDraftData(
                null, zero, zero, zero, zero, zero, List.of(piece), List.of()));
        var before = rows("materials");
        var references = rows("project_estimate_workpieces");
        var catalog = context.getBean(MaterialCatalog.class);
        var real = context.getBean(MaterialCatalogPort.class);
        var intercept = mock(MaterialCatalogPort.class, delegatesTo(real));
        doReturn(false).when(intercept).isReferencedByDraftWorkpiece(anyString());
        doAnswer(invocation -> {
            assertTransactionAndForeignKeys();
            real.deleteAndFlush(invocation.getArgument(0));
            return null;
        }).when(intercept).deleteAndFlush(anyString());
        ReflectionTestUtils.setField(catalog, "materials", intercept);
        try {
            var failure = assertThrows(IllegalStateException.class, () -> service.delete(material.id()));
            assertEquals("error.material-has-related-records", failure.getMessage());
            assertTrue(SqliteIntegrityErrors.isForeignKeyViolation(failure.getCause()));
        } finally {
            ReflectionTestUtils.setField(catalog, "materials", real);
        }
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertEquals(before, rows("materials"));
        assertEquals(references, rows("project_estimate_workpieces"));
    }

    @Test
    void propagatesNonForeignKeyFailureAndRollsBackRealFlushedWrite() {
        var service = context.getBean(MaterialService.class);
        var material = service.create(data("Original", "C", "1", "10"));
        var before = rows("materials");
        var catalog = context.getBean(MaterialCatalog.class);
        var real = context.getBean(MaterialCatalogPort.class);
        var intercept = mock(MaterialCatalogPort.class, delegatesTo(real));
        var observed = new AtomicBoolean();
        doAnswer(invocation -> {
            Optional<MaterialRow> updated = real.update(invocation.getArgument(0), invocation.getArgument(1));
            assertTrue(updated.isPresent());
            assertTransactionAndForeignKeys();
            assertEquals("Changed", jdbc.queryForObject("SELECT name FROM materials WHERE id = ?",
                    String.class, material.id()));
            observed.set(true);
            jdbc.update("INSERT INTO materials (id) VALUES (?)", "invalid");
            throw new AssertionError("Expected SQLite constraint failure");
        }).when(intercept).update(anyString(), any(MaterialInput.class));
        ReflectionTestUtils.setField(catalog, "materials", intercept);
        try {
            var failure = assertThrows(UncategorizedSQLException.class,
                    () -> service.update(material.id(), data("Changed", "C", "1", "10")));
            assertFalse(SqliteIntegrityErrors.isForeignKeyViolation(failure));
        } finally {
            ReflectionTestUtils.setField(catalog, "materials", real);
        }
        assertTrue(observed.get());
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertEquals(before, rows("materials"));
    }

    @Test
    void rollsBackEarlierFlushedUpdateWhenDeleteFlushViolatesForeignKey() {
        var service = context.getBean(MaterialService.class);
        var material = service.create(data("Original", "C", "1", "10"));
        var piece = new EstimateDraftData.EstimateDraftWorkpieceData(null, "Panel", BigDecimal.TEN,
                BigDecimal.TEN, 1, material.id(), null, false, false, false, false);
        var zero = BigDecimal.ZERO;
        context.getBean(EstimateDraftService.class).saveActiveDraft(new EstimateDraftData(
                null, zero, zero, zero, zero, zero, List.of(piece), List.of()));
        var before = rows("materials");
        var references = rows("project_estimate_workpieces");
        var catalog = context.getBean(MaterialCatalog.class);
        var real = context.getBean(MaterialCatalogPort.class);
        var intercept = mock(MaterialCatalogPort.class, delegatesTo(real));
        doReturn(false).when(intercept).isReferencedByDraftWorkpiece(anyString());
        var observed = new AtomicBoolean();
        doAnswer(invocation -> {
            assertTransactionAndForeignKeys();
            assertEquals("Changed", jdbc.queryForObject("SELECT name FROM materials WHERE id = ?",
                    String.class, material.id()));
            observed.set(true);
            real.deleteAndFlush(invocation.getArgument(0));
            return null;
        }).when(intercept).deleteAndFlush(anyString());
        ReflectionTestUtils.setField(catalog, "materials", intercept);
        try {
            var failure = assertThrows(IllegalStateException.class, () ->
                    new TransactionTemplate(context.getBean(PlatformTransactionManager.class))
                            .executeWithoutResult(status -> {
                                service.update(material.id(), data("Changed", "C", "1", "10"));
                                service.delete(material.id());
                            }));
            assertEquals("error.material-has-related-records", failure.getMessage());
            assertTrue(SqliteIntegrityErrors.isForeignKeyViolation(failure));
        } finally {
            ReflectionTestUtils.setField(catalog, "materials", real);
        }
        assertTrue(observed.get());
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertEquals(before, rows("materials"));
        assertEquals(references, rows("project_estimate_workpieces"));
    }

    @Test
    void propagatesNonForeignKeyIntegrityErrorOnDelete() {
        var service = context.getBean(MaterialService.class);
        var material = service.create(data("Original", "C", "1", "10"));
        var before = rows("materials");
        var catalog = context.getBean(MaterialCatalog.class);
        var real = context.getBean(MaterialCatalogPort.class);
        var intercept = mock(MaterialCatalogPort.class, delegatesTo(real));
        var failure = new DataIntegrityViolationException("unrelated integrity error");
        doAnswer(invocation -> {
            real.deleteAndFlush(invocation.getArgument(0));
            throw failure;
        }).when(intercept).deleteAndFlush(anyString());
        ReflectionTestUtils.setField(catalog, "materials", intercept);
        try {
            assertSame(failure, assertThrows(DataIntegrityViolationException.class,
                    () -> service.delete(material.id())));
        } finally {
            ReflectionTestUtils.setField(catalog, "materials", real);
        }
        assertEquals(before, rows("materials"));
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

    private MaterialData data(String name, String color, String thickness, String price) {
        return new MaterialData(name, color, new BigDecimal(thickness), new BigDecimal("2750.25"),
                new BigDecimal("1840.50"), new BigDecimal(price));
    }

    private List<Map<String, Object>> rows(String table) {
        return jdbc.queryForList("SELECT * FROM " + table + " ORDER BY id");
    }
}
