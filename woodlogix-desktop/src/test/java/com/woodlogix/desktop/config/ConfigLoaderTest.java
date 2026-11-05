package com.woodlogix.desktop.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigLoaderTest {
    private static final String CONFIG_FILE_PROPERTY = "woodlogix.config.file";
    private final String originalConfigFile = System.getProperty(CONFIG_FILE_PROPERTY);

    @TempDir
    Path temporaryDirectory;

    @AfterEach
    void restoreConfigFileProperty() {
        if (originalConfigFile == null) {
            System.clearProperty(CONFIG_FILE_PROPERTY);
        } else {
            System.setProperty(CONFIG_FILE_PROPERTY, originalConfigFile);
        }
    }

    @Test
    void loadsExternalOverridesAndKeepsApplicationDefaults() throws IOException {
        Path externalConfig = temporaryDirectory.resolve("woodlogix.properties");
        Files.writeString(externalConfig, "woodlogix.database.path=custom/woodlogix.db\n");
        System.setProperty(CONFIG_FILE_PROPERTY, externalConfig.toString());

        Properties properties = ConfigLoader.load();

        assertEquals("custom/woodlogix.db", properties.getProperty("woodlogix.database.path"));
        assertEquals("true", properties.getProperty("woodlogix.database.foreign-keys"));
    }
}
