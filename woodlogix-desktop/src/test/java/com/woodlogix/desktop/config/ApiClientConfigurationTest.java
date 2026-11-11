package com.woodlogix.desktop.config;

import com.woodlogix.desktop.api.client.ApiHttpClient;
import com.woodlogix.desktop.api.client.CustomerApiClient;
import com.woodlogix.desktop.api.client.EnvironmentApiClient;
import com.woodlogix.desktop.api.client.SalesPipelineApiClient;
import com.woodlogix.desktop.api.mapper.EnvironmentMapperImpl;
import com.woodlogix.desktop.api.mapper.SalesPipelineMapperImpl;
import com.woodlogix.desktop.service.RemoteEnvironmentService;
import com.woodlogix.desktop.service.RemoteSalesPipelineService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApiClientConfigurationTest {
    private static final String CONFIG_FILE_PROPERTY = "woodlogix.config.file";
    private final String originalConfigFile = System.getProperty(CONFIG_FILE_PROPERTY);

    @AfterEach
    void restoreConfigFileProperty() {
        if (originalConfigFile == null) {
            System.clearProperty(CONFIG_FILE_PROPERTY);
        } else {
            System.setProperty(CONFIG_FILE_PROPERTY, originalConfigFile);
        }
    }

    @Test
    void createsOneSharedHttpClientAndTypedClients(@TempDir Path temporaryDirectory)
            throws IOException {
        Path config = temporaryDirectory.resolve("woodlogix.properties");
        Files.writeString(config, ApiClientConfiguration.BASE_URL_PROPERTY
                + "=http://127.0.0.1:8081/api/v1\n");
        System.setProperty(CONFIG_FILE_PROPERTY, config.toString());

        ApiClientConfiguration configuration = new ApiClientConfiguration();
        ApiHttpClient httpClient = configuration.apiHttpClient();

        assertInstanceOf(CustomerApiClient.class, configuration.customerApiClient(httpClient));
        assertInstanceOf(EnvironmentApiClient.class, configuration.environmentApiClient(httpClient));
        assertInstanceOf(SalesPipelineApiClient.class,
                configuration.salesPipelineApiClient(httpClient));
        assertInstanceOf(RemoteEnvironmentService.class,
                configuration.remoteEnvironmentService(
                        configuration.environmentApiClient(httpClient),
                        new EnvironmentMapperImpl()));
        assertInstanceOf(RemoteSalesPipelineService.class,
                configuration.remoteSalesPipelineService(
                        configuration.salesPipelineApiClient(httpClient),
                        new SalesPipelineMapperImpl()));
    }

    @Test
    void rejectsInvalidConfiguredBaseUri(@TempDir Path temporaryDirectory) throws IOException {
        Path config = temporaryDirectory.resolve("woodlogix.properties");
        Files.writeString(config, ApiClientConfiguration.BASE_URL_PROPERTY + "=not-a-uri\n");
        System.setProperty(CONFIG_FILE_PROPERTY, config.toString());

        assertThrows(IllegalArgumentException.class,
                () -> new ApiClientConfiguration().apiHttpClient());
    }
}
