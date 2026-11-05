package com.woodlogix.desktop.config;

import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

@Configuration
public class DatabaseConfiguration {
    @Bean
    public DataSource dataSource(ConfigurableEnvironment environment) throws IOException {
        Properties properties = ConfigLoader.load();
        String databasePath = commandLineValue(environment, "woodlogix.database.path");
        if (databasePath == null || databasePath.isBlank()) {
            databasePath = properties.getProperty("woodlogix.database.path");
        }
        if (databasePath == null || databasePath.isBlank()) {
            throw new IllegalStateException("woodlogix.database.path must be configured");
        }

        Path path = Path.of(databasePath).toAbsolutePath().normalize();
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        String foreignKeysProperty = properties.getProperty("woodlogix.database.foreign-keys");
        if (foreignKeysProperty == null || foreignKeysProperty.isBlank()) {
            throw new IllegalStateException(
                    "woodlogix.database.foreign-keys must be configured"
            );
        }
        if (!"true".equalsIgnoreCase(foreignKeysProperty)
                && !"false".equalsIgnoreCase(foreignKeysProperty)) {
            throw new IllegalStateException(
                    "woodlogix.database.foreign-keys must be true or false"
            );
        }
        boolean foreignKeysEnabled = Boolean.parseBoolean(foreignKeysProperty);
        SQLiteConfig configuration = new SQLiteConfig();
        configuration.enforceForeignKeys(foreignKeysEnabled);
        SQLiteDataSource dataSource = new SQLiteDataSource(configuration);
        dataSource.setUrl("jdbc:sqlite:" + path);
        return dataSource;
    }

    private static String commandLineValue(
            ConfigurableEnvironment environment, String propertyName
    ) {
        PropertySource<?> commandLine = environment.getPropertySources().get("commandLineArgs");
        if (commandLine == null) {
            return null;
        }
        Object value = commandLine.getProperty(propertyName);
        return value == null ? null : value.toString();
    }
}
