package com.woodlogix.desktop.config;

import com.woodlogix.desktop.api.client.ApiHttpClient;
import com.woodlogix.desktop.api.client.CustomerApiClient;
import com.woodlogix.desktop.api.client.EdgeBandingApiClient;
import com.woodlogix.desktop.api.client.EnvironmentApiClient;
import com.woodlogix.desktop.api.client.HardwareApiClient;
import com.woodlogix.desktop.api.client.MaterialApiClient;
import com.woodlogix.desktop.api.client.SalesPipelineApiClient;
import com.woodlogix.desktop.api.mapper.EnvironmentMapper;
import com.woodlogix.desktop.api.mapper.SalesPipelineMapper;
import com.woodlogix.desktop.service.RemoteEnvironmentService;
import com.woodlogix.desktop.service.RemoteSalesPipelineService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.time.Duration;
import java.util.Properties;

@Configuration
public class ApiClientConfiguration {
    static final String BASE_URL_PROPERTY = "woodlogix.api.base-url";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    @Bean
    public ApiHttpClient apiHttpClient() {
        Properties properties = ConfigLoader.load();
        String baseUrl = properties.getProperty(BASE_URL_PROPERTY);
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException(BASE_URL_PROPERTY + " must be configured");
        }
        return new ApiHttpClient(URI.create(baseUrl), CONNECT_TIMEOUT, REQUEST_TIMEOUT);
    }

    @Bean
    public CustomerApiClient customerApiClient(ApiHttpClient apiHttpClient) {
        return new CustomerApiClient(apiHttpClient);
    }

    @Bean
    public MaterialApiClient materialApiClient(ApiHttpClient apiHttpClient) {
        return new MaterialApiClient(apiHttpClient);
    }

    @Bean
    public EdgeBandingApiClient edgeBandingApiClient(ApiHttpClient apiHttpClient) {
        return new EdgeBandingApiClient(apiHttpClient);
    }

    @Bean
    public HardwareApiClient hardwareApiClient(ApiHttpClient apiHttpClient) {
        return new HardwareApiClient(apiHttpClient);
    }

    @Bean
    public EnvironmentApiClient environmentApiClient(ApiHttpClient apiHttpClient) {
        return new EnvironmentApiClient(apiHttpClient);
    }

    @Bean
    public SalesPipelineApiClient salesPipelineApiClient(ApiHttpClient apiHttpClient) {
        return new SalesPipelineApiClient(apiHttpClient);
    }

    @Bean
    public RemoteEnvironmentService remoteEnvironmentService(
            EnvironmentApiClient environmentApiClient,
            EnvironmentMapper environmentMapper
    ) {
        return new RemoteEnvironmentService(environmentApiClient, environmentMapper);
    }

    @Bean
    public RemoteSalesPipelineService remoteSalesPipelineService(
            SalesPipelineApiClient salesPipelineApiClient,
            SalesPipelineMapper salesPipelineMapper
    ) {
        return new RemoteSalesPipelineService(salesPipelineApiClient, salesPipelineMapper);
    }
}
