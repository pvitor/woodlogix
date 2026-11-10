package com.woodlogix.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
