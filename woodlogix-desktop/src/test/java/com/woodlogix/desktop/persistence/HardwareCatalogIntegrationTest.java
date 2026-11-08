package com.woodlogix.desktop.persistence;

import com.woodlogix.application.hardware.HardwareCatalog;
import com.woodlogix.application.hardware.HardwareCatalogPort;
import com.woodlogix.application.hardware.HardwareInput;
import com.woodlogix.application.hardware.HardwareRow;
import com.woodlogix.desktop.WoodLogixApplication;
import com.woodlogix.desktop.service.HardwareData;
import com.woodlogix.desktop.service.HardwareService;
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
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HardwareCatalogIntegrationTest {
    @TempDir
    Path temporaryDirectory;
    private ConfigurableApplicationContext context;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        context = new SpringApplicationBuilder(WoodLogixApplication.class)
                .web(WebApplicationType.NONE)
                .run("--spring.main.banner-mode=off", "--logging.level.root=ERROR",
                        "--logging.file.name=", "--woodlogix.database.path=" + temporaryDirectory.resolve("catalog.db"));
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
    void createsUpdatesAndListsPersistedValuesInNameOrder() {
        var service = context.getBean(HardwareService.class);
        var z = service.create(new HardwareData("Z hinge", "PAIR", new BigDecimal("12345.67")));
        var a = service.create(new HardwareData("A screw", "UNIT", BigDecimal.ZERO));
        assertNotNull(z.id());
        assertEquals("Z hinge", z.name());
        assertEquals("PAIR", z.unit());
        assertEquals(0, new BigDecimal("12345.67").compareTo(z.unitPrice()));
        var updated = service.update(z.id(), new HardwareData("B hinge", "BOX", new BigDecimal("99.91")));
        assertEquals(z.id(), updated.id());
        assertEquals("B hinge", updated.name());
        assertEquals("BOX", updated.unit());
        assertEquals(0, new BigDecimal("99.91").compareTo(updated.unitPrice()));
        var listed = service.findAll();
        assertEquals(List.of(a.id(), z.id()), listed.stream().map(HardwareRow::id).toList());
        assertEquals(List.of("A screw", "B hinge"), listed.stream().map(HardwareRow::name).toList());
        assertEquals("BOX", listed.get(1).unit());
        assertEquals(0, new BigDecimal("99.91").compareTo(listed.get(1).unitPrice()));
        assertEquals(0, BigDecimal.ZERO.compareTo(listed.get(0).unitPrice()));
        assertEquals("B hinge", jdbc.queryForObject("SELECT name FROM hardwares WHERE id = ?", String.class, z.id()));
    }

    @Test
    void validatesFacadeAndRejectsMissingUpdateWithoutWrites() {
        var service = context.getBean(HardwareService.class);
        var invalid = List.of(new HardwareData("", "UNIT", BigDecimal.ZERO),
                new HardwareData("H", " ", BigDecimal.ZERO),
                new HardwareData("H", "UNIT", new BigDecimal("-0.01")));
        for (var input : invalid) {
            assertThrows(ConstraintViolationException.class, () -> service.create(input));
        }
        var failure = assertThrows(EntityNotFoundException.class,
                () -> service.update("missing", new HardwareData("H", "UNIT", BigDecimal.ZERO)));
        assertEquals("error.hardware-not-found", failure.getMessage());
        assertTrue(rows().isEmpty());
    }

    @Test
    void rollsBackWhenPersistenceFailsAfterRealFlush() {
        var service = context.getBean(HardwareService.class);
        var original = service.create(new HardwareData("Original", "UNIT", new BigDecimal("12.34")));
        var before = rows();
        var catalog = context.getBean(HardwareCatalog.class);
        var real = context.getBean(HardwareCatalogPort.class);
        var injected = new DataIntegrityViolationException("injected failure after flush");
        var observed = new AtomicBoolean();
        HardwareCatalogPort intercepted = new HardwareCatalogPort() {
            public HardwareRow create(HardwareInput input) {
                return real.create(input);
            }
            public Optional<HardwareRow> update(String id, HardwareInput input) {
                var result = real.update(id, input);
                assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                var entityManager = EntityManagerFactoryUtils.getTransactionalEntityManager(
                        context.getBean(EntityManagerFactory.class));
                assertNotNull(entityManager);
                assertTrue(entityManager.isJoinedToTransaction());
                String name = entityManager.unwrap(Session.class).doReturningWork(connection -> {
                    try (var statement = connection.prepareStatement("SELECT name FROM hardwares WHERE id = ?")) {
                        statement.setString(1, id);
                        try (var rs = statement.executeQuery()) {
                            assertTrue(rs.next());
                            return rs.getString(1);
                        }
                    }
                });
                assertEquals("Modified", name);
                observed.set(true);
                throw injected;
            }
            public List<HardwareRow> findAllByName() {
                return real.findAllByName();
            }
        };
        ReflectionTestUtils.setField(catalog, "hardware", intercepted);
        try {
            assertSame(injected, assertThrows(DataIntegrityViolationException.class,
                    () -> service.update(original.id(), new HardwareData("Modified", "BOX", BigDecimal.ONE))));
        } finally {
            ReflectionTestUtils.setField(catalog, "hardware", real);
        }
        assertTrue(observed.get());
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertEquals(before, rows());
    }

    private List<Map<String, Object>> rows() {
        return jdbc.queryForList("SELECT * FROM hardwares ORDER BY id");
    }
}
