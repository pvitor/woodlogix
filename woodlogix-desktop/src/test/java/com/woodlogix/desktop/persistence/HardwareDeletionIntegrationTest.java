package com.woodlogix.desktop.persistence;

import com.woodlogix.application.hardware.DeleteHardware;
import com.woodlogix.application.hardware.HardwareDeletionPort;
import com.woodlogix.desktop.WoodLogixApplication;
import com.woodlogix.desktop.service.EstimateDraftData;
import com.woodlogix.desktop.service.EstimateDraftService;
import com.woodlogix.desktop.service.HardwareData;
import com.woodlogix.desktop.service.HardwareService;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
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
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HardwareDeletionIntegrationTest {
    @TempDir
    Path temporaryDirectory;
    private ConfigurableApplicationContext context;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        context = new SpringApplicationBuilder(WoodLogixApplication.class)
                .web(WebApplicationType.NONE)
                .run("--spring.main.banner-mode=off", "--logging.level.root=ERROR",
                        "--logging.file.name=", "--woodlogix.database.path=" + temporaryDirectory.resolve("hardware.db"));
        jdbc = new JdbcTemplate(context.getBean(DataSource.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1' AND success = 1", Integer.class));
    }

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void deletesUnreferencedHardware() {
        var id = createHardware();
        context.getBean(HardwareService.class).delete(id);
        assertTrue(rows("hardwares").isEmpty());
    }

    @Test
    void rejectsMissingHardware() {
        var before = rows("hardwares");
        var failure = assertThrows(EntityNotFoundException.class,
                () -> context.getBean(HardwareService.class).delete("missing"));
        assertEquals("error.hardware-not-found", failure.getMessage());
        assertEquals(before, rows("hardwares"));
    }

    @Test
    void rejectsReferencedHardwareAndPreservesPhysicalRows() {
        var id = createHardware();
        var zero = BigDecimal.ZERO;
        context.getBean(EstimateDraftService.class).saveActiveDraft(new EstimateDraftData(
                null, zero, zero, zero, zero, zero, List.of(),
                List.of(new EstimateDraftData.EstimateDraftHardwareData(id, 2))));
        var hardwareBefore = rows("hardwares");
        var draftBefore = rows("project_estimates");
        var referencesBefore = rows("project_estimate_hardwares");
        var failure = assertThrows(IllegalStateException.class,
                () -> context.getBean(HardwareService.class).delete(id));
        assertEquals("error.hardware-has-related-records", failure.getMessage());
        assertEquals(hardwareBefore, rows("hardwares"));
        assertEquals(draftBefore, rows("project_estimates"));
        assertEquals(referencesBefore, rows("project_estimate_hardwares"));
    }

    @Test
    void translatesRealForeignKeyFailureWhenReferenceCheckMissesIt() {
        var id = createHardware();
        var zero = BigDecimal.ZERO;
        context.getBean(EstimateDraftService.class).saveActiveDraft(new EstimateDraftData(
                null, zero, zero, zero, zero, zero, List.of(),
                List.of(new EstimateDraftData.EstimateDraftHardwareData(id, 2))));
        var hardwareBefore = rows("hardwares");
        var referencesBefore = rows("project_estimate_hardwares");
        var real = context.getBean(HardwareDeletionPort.class);
        var useCase = context.getBean(DeleteHardware.class);
        HardwareDeletionPort intercepting = new HardwareDeletionPort() {
            public boolean exists(String value) {
                assertTransactionAndForeignKeys();
                return real.exists(value);
            }
            public boolean isReferencedByDraft(String value) {
                assertTransactionAndForeignKeys();
                return false;
            }
            public void deleteAndFlush(String value) {
                assertTransactionAndForeignKeys();
                real.deleteAndFlush(value);
            }
        };
        ReflectionTestUtils.setField(useCase, "hardware", intercepting);
        try {
            var failure = assertThrows(IllegalStateException.class,
                    () -> context.getBean(HardwareService.class).delete(id));
            assertEquals("error.hardware-has-related-records", failure.getMessage());
            assertTrue(SqliteIntegrityErrors.isForeignKeyViolation(failure.getCause()));
        } finally {
            ReflectionTestUtils.setField(useCase, "hardware", real);
        }
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertEquals(hardwareBefore, rows("hardwares"));
        assertEquals(referencesBefore, rows("project_estimate_hardwares"));
    }

    @Test
    void rollsBackRealFlushedDeletion() {
        var id = createHardware();
        var before = rows("hardwares");
        var real = context.getBean(HardwareDeletionPort.class);
        var useCase = context.getBean(DeleteHardware.class);
        var observed = new AtomicBoolean();
        var injected = new IllegalStateException("injected failure after flush");
        HardwareDeletionPort intercepting = new HardwareDeletionPort() {
            public boolean exists(String value) {
                assertTransactionAndForeignKeys();
                return real.exists(value);
            }
            public boolean isReferencedByDraft(String value) {
                assertTransactionAndForeignKeys();
                return real.isReferencedByDraft(value);
            }
            public void deleteAndFlush(String value) {
                assertTransactionAndForeignKeys();
                real.deleteAndFlush(value);
                var entityManager = EntityManagerFactoryUtils.getTransactionalEntityManager(
                        context.getBean(EntityManagerFactory.class));
                assertNotNull(entityManager);
                long count = entityManager.unwrap(Session.class).doReturningWork(connection -> {
                    try (var statement = connection.prepareStatement("SELECT COUNT(*) FROM hardwares WHERE id = ?")) {
                        statement.setString(1, value);
                        try (var result = statement.executeQuery()) {
                            assertTrue(result.next());
                            return result.getLong(1);
                        }
                    }
                });
                assertEquals(0, count);
                observed.set(true);
                throw injected;
            }
        };
        ReflectionTestUtils.setField(useCase, "hardware", intercepting);
        try {
            assertSame(injected, assertThrows(IllegalStateException.class,
                    () -> context.getBean(HardwareService.class).delete(id)));
        } finally {
            ReflectionTestUtils.setField(useCase, "hardware", real);
        }
        assertTrue(observed.get());
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertEquals(before, rows("hardwares"));
    }

    @Test
    void participatesInExistingTransaction() {
        var id = createHardware();
        var before = rows("hardwares");
        var injected = new IllegalStateException("outer transaction failure");
        var transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        var failure = assertThrows(IllegalStateException.class, () ->
                transaction.executeWithoutResult(status -> {
                    context.getBean(HardwareService.class).delete(id);
                    assertEquals(0, jdbc.queryForObject(
                            "SELECT COUNT(*) FROM hardwares WHERE id = ?", Integer.class, id));
                    throw injected;
                }));
        assertSame(injected, failure);
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertEquals(before, rows("hardwares"));
    }

    @Test
    void propagatesUnrecognizedIntegrityFailure() {
        var id = createHardware();
        var before = rows("hardwares");
        var real = context.getBean(HardwareDeletionPort.class);
        var useCase = context.getBean(DeleteHardware.class);
        var injected = new DataIntegrityViolationException("unrelated integrity failure");
        HardwareDeletionPort intercepting = new HardwareDeletionPort() {
            public boolean exists(String value) {
                return real.exists(value);
            }
            public boolean isReferencedByDraft(String value) {
                return real.isReferencedByDraft(value);
            }
            public void deleteAndFlush(String value) {
                real.deleteAndFlush(value);
                throw injected;
            }
        };
        ReflectionTestUtils.setField(useCase, "hardware", intercepting);
        try {
            assertSame(injected, assertThrows(DataIntegrityViolationException.class,
                    () -> context.getBean(HardwareService.class).delete(id)));
        } finally {
            ReflectionTestUtils.setField(useCase, "hardware", real);
        }
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertEquals(before, rows("hardwares"));
    }

    private String createHardware() {
        return context.getBean(HardwareService.class)
                .create(new HardwareData("Hinge", "UNIT", new BigDecimal("14.00"))).getId();
    }

    private List<Map<String, Object>> rows(String table) {
        return jdbc.queryForList("SELECT * FROM " + table + " ORDER BY id");
    }

    private void assertTransactionAndForeignKeys() {
        assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
        var entityManager = EntityManagerFactoryUtils.getTransactionalEntityManager(
                context.getBean(EntityManagerFactory.class));
        assertNotNull(entityManager);
        assertTrue(entityManager.isJoinedToTransaction());
        int foreignKeys = entityManager.unwrap(Session.class).doReturningWork(connection -> {
            try (var statement = connection.createStatement();
                 var result = statement.executeQuery("PRAGMA foreign_keys")) {
                assertTrue(result.next());
                return result.getInt(1);
            }
        });
        assertEquals(1, foreignKeys);
    }
}
