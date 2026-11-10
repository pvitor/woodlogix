package com.woodlogix.server;

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

class WoodLogixServerApplicationTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void startsWebServerAndMigratesAnIsolatedSqliteDatabase() {
        Path database = temporaryDirectory.resolve("server.db");
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(
                WoodLogixServerApplication.class)
                .web(WebApplicationType.SERVLET)
                .run("--server.port=0",
                        "--spring.main.banner-mode=off",
                        "--logging.level.root=ERROR",
                        "--logging.file.name=",
                        "--woodlogix.database.path=" + database)) {
            Integer serverPort = context.getEnvironment()
                    .getProperty("local.server.port", Integer.class);
            assertTrue(serverPort != null && serverPort > 0);

            JdbcTemplate jdbc = new JdbcTemplate(context.getBean(DataSource.class));
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM flyway_schema_history "
                            + "WHERE version = '1' AND success = 1",
                    Integer.class));
        }
    }

    @Test
    void rejectsMissingDatabasePathClearly() {
        Exception failure = assertThrows(Exception.class, () -> {
            try (ConfigurableApplicationContext ignored = new SpringApplicationBuilder(
                    WoodLogixServerApplication.class)
                    .web(WebApplicationType.NONE)
                    .properties("spring.flyway.enabled=false")
                    .run("--spring.main.banner-mode=off",
                            "--logging.level.root=ERROR",
                            "--logging.file.name=")) {
                throw new AssertionError("The Server must require woodlogix.database.path");
            }
        });

        assertTrue(causeMessages(failure)
                .contains("woodlogix.database.path must be configured"));
    }

    @Test
    void rejectsNonEmptyDatabaseWithoutFlywayHistoryWithoutBaseline() throws Exception {
        Path database = temporaryDirectory.resolve("legacy.db");
        String url = "jdbc:sqlite:" + database;
        try (var connection = DriverManager.getConnection(url);
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE legacy_data (id TEXT PRIMARY KEY, value TEXT NOT NULL)");
            statement.execute("INSERT INTO legacy_data VALUES ('id', 'preserve')");
        }

        Exception failure = assertThrows(Exception.class, () -> {
            try (ConfigurableApplicationContext ignored = new SpringApplicationBuilder(
                    WoodLogixServerApplication.class)
                    .web(WebApplicationType.NONE)
                    .run("--spring.main.banner-mode=off",
                            "--logging.level.root=ERROR",
                            "--logging.file.name=",
                            "--woodlogix.database.path=" + database)) {
                throw new AssertionError("A nonempty database must not be baselined automatically");
            }
        });

        assertTrue(causeMessages(failure).contains("non-empty schema"));
        try (var connection = DriverManager.getConnection(url);
             var statement = connection.createStatement();
             var data = statement.executeQuery("SELECT id, value FROM legacy_data")) {
            assertTrue(data.next());
            assertEquals("id", data.getString(1));
            assertEquals("preserve", data.getString(2));
            assertFalse(data.next());
        }
    }

    private String causeMessages(Throwable failure) {
        var messages = new StringBuilder();
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            messages.append(cause.getMessage()).append('\n');
        }
        return messages.toString();
    }
}
