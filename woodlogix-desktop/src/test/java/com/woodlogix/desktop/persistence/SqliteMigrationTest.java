package com.woodlogix.desktop.persistence;

import com.woodlogix.desktop.WoodLogixApplication;
import com.woodlogix.desktop.service.CustomerData;
import com.woodlogix.desktop.service.LocalCustomerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteMigrationTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void migratedDatabaseCanBeReopenedWithoutChangingData() {
        Path database = temporaryDirectory.resolve("reopen.db");
        String customerId;
        try (var first = start(database)) {
            customerId = first.getBean(LocalCustomerService.class)
                    .create(new CustomerData("Customer", null, null, null)).id();
            var jdbc = new JdbcTemplate(first.getBean(DataSource.class));
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1' AND success = 1",
                    Integer.class));
        }

        try (var second = start(database)) {
            var jdbc = new JdbcTemplate(second.getBean(DataSource.class));
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM customers WHERE id = ?", Integer.class, customerId));
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1' AND success = 1",
                    Integer.class));
        }
    }

    @Test
    void rejectsNonemptyDatabaseWithoutFlywayHistoryWithoutMigratingIt() throws Exception {
        Path database = temporaryDirectory.resolve("legacy.db");
        String url = "jdbc:sqlite:" + database;
        try (var connection = DriverManager.getConnection(url);
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE legacy_data (id TEXT PRIMARY KEY, value TEXT NOT NULL)");
            statement.execute("INSERT INTO legacy_data VALUES ('id', 'preserve')");
        }

        var failure = assertThrows(Exception.class, () -> {
            try (var ignored = start(database)) {
                throw new AssertionError("A nonempty database must not be baselined automatically");
            }
        });
        assertTrue(causeMessages(failure).contains("non-empty schema"),
                "Startup should explain that a nonempty schema has no Flyway history");

        try (var connection = DriverManager.getConnection(url);
             var statement = connection.createStatement()) {
            try (var data = statement.executeQuery("SELECT id, value FROM legacy_data")) {
                assertTrue(data.next());
                assertEquals("id", data.getString(1));
                assertEquals("preserve", data.getString(2));
                assertFalse(data.next());
            }
            try (var tables = statement.executeQuery(
                    "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' ORDER BY name")) {
                assertTrue(tables.next());
                assertEquals("legacy_data", tables.getString(1));
                assertFalse(tables.next());
            }
        }
    }

    @Test
    void hibernateValidationRejectsSchemaDriftWithoutRecreatingTables() throws Exception {
        Path database = temporaryDirectory.resolve("drift.db");
        try (var ignored = start(database)) {
            assertTrue(ignored.isActive());
        }
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             var statement = connection.createStatement()) {
            statement.execute("ALTER TABLE customers RENAME TO displaced_customers");
        }

        var failure = assertThrows(Exception.class, () -> {
            try (var ignored = start(database)) {
                throw new AssertionError("Hibernate validation must reject the missing table");
            }
        });
        assertTrue(causeMessages(failure).contains("Schema-validation: missing table [customers]"));
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             var statement = connection.createStatement();
             var tables = statement.executeQuery(
                     "SELECT name FROM sqlite_master WHERE type = 'table' AND name IN ('customers', 'displaced_customers')")) {
            assertTrue(tables.next());
            assertEquals("displaced_customers", tables.getString(1));
            assertFalse(tables.next());
        }
    }

    private ConfigurableApplicationContext start(Path database) {
        return new SpringApplicationBuilder(WoodLogixApplication.class)
                .web(WebApplicationType.NONE)
                .run("--spring.main.banner-mode=off",
                        "--logging.level.root=OFF",
                        "--logging.file.name=",
                        "--woodlogix.database.path=" + database);
    }

    private String causeMessages(Throwable failure) {
        var messages = new StringBuilder();
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            messages.append(cause.getMessage()).append('\n');
        }
        return messages.toString();
    }
}
