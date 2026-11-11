package com.woodlogix.server.config;

import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
public class ServerDatabaseConfiguration {
    @Bean
    public DataSource dataSource(Environment environment) throws IOException {
        String databasePath = environment.getProperty("woodlogix.database.path");
        if (databasePath == null || databasePath.isBlank()) {
            throw new IllegalStateException("woodlogix.database.path must be configured");
        }

        Path path = Path.of(databasePath).toAbsolutePath().normalize();
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        SQLiteConfig configuration = new SQLiteConfig();
        configuration.enforceForeignKeys(true);
        configuration.setBusyTimeout(5000);
        SQLiteDataSource dataSource = new SQLiteDataSource(configuration);
        dataSource.setUrl("jdbc:sqlite:" + path);
        return dataSource;
    }
}
