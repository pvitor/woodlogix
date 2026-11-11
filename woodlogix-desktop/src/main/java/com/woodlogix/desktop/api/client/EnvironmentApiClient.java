package com.woodlogix.desktop.api.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.woodlogix.desktop.api.dto.EnvironmentAddRequest;
import com.woodlogix.desktop.api.dto.EnvironmentReplaceRequest;
import com.woodlogix.desktop.api.dto.EnvironmentResponse;

import java.util.List;
import java.util.Objects;

public final class EnvironmentApiClient {
    private static final TypeReference<List<EnvironmentResponse>> ENVIRONMENT_LIST =
            new TypeReference<>() {
            };

    private final ApiHttpClient httpClient;

    public EnvironmentApiClient(ApiHttpClient httpClient) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
    }

    public List<EnvironmentResponse> findAll() {
        return httpClient.get("environments", ENVIRONMENT_LIST);
    }

    public List<EnvironmentResponse> findByCustomer(String customerId) {
        return httpClient.get(
                ApiUri.query("environments", "customerId", customerId),
                ENVIRONMENT_LIST);
    }

    public List<EnvironmentResponse> createForCustomer(
            String customerId,
            EnvironmentAddRequest request
    ) {
        return httpClient.post(
                ApiUri.path("customers/{segment}/environments", customerId),
                request,
                ENVIRONMENT_LIST);
    }

    public List<EnvironmentResponse> replaceCustomerEnvironments(
            String customerId,
            EnvironmentReplaceRequest request
    ) {
        return httpClient.put(
                ApiUri.path("customers/{segment}/environments", customerId),
                request,
                ENVIRONMENT_LIST);
    }

    public void delete(String id) {
        httpClient.delete(ApiUri.path("environments/{segment}", id));
    }

    public void deleteAllByCustomer(String customerId) {
        httpClient.delete(ApiUri.path("customers/{segment}/environments", customerId));
    }
}
