package com.woodlogix.desktop.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class ConfigLoader {
    private static final String EXTERNAL_CONFIG_ENV = "WOODLOGIX_CONFIG_FILE";
    private static final String EXTERNAL_CONFIG_PROPERTY = "woodlogix.config.file";

    private ConfigLoader() {
    }

    public static Properties load() {
        Properties properties = new Properties();
        String externalConfig = System.getProperty(EXTERNAL_CONFIG_PROPERTY);
        if (externalConfig == null || externalConfig.isBlank()) {
            externalConfig = System.getenv(EXTERNAL_CONFIG_ENV);
        }

        try (InputStream applicationInput =
                     ConfigLoader.class.getResourceAsStream("/application.properties");
             InputStream configInput =
                     ConfigLoader.class.getResourceAsStream("/config.properties")) {
            if (applicationInput == null) {
                throw new IllegalStateException("application.properties was not found");
            }
            if (configInput == null) {
                throw new IllegalStateException("config.properties was not found");
            }
            properties.load(applicationInput);
            properties.load(configInput);
            if (externalConfig != null && !externalConfig.isBlank()) {
                try (InputStream externalInput = Files.newInputStream(Path.of(externalConfig))) {
                    properties.load(externalInput);
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load WoodLogix configuration", exception);
        }

        String databasePath = System.getProperty("woodlogix.database.path");
        if (databasePath == null || databasePath.isBlank()) {
            databasePath = System.getenv("WOODLOGIX_DATABASE_PATH");
        }
        if (databasePath != null && !databasePath.isBlank()) {
            properties.setProperty("woodlogix.database.path", databasePath);
        }

        return properties;
    }
}
