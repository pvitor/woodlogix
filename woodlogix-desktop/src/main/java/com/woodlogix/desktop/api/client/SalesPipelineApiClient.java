package com.woodlogix.desktop.api.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.woodlogix.desktop.api.dto.ChangeSalesStageRequest;
import com.woodlogix.desktop.api.dto.PipelineResponse;
import com.woodlogix.desktop.api.dto.SalesProjectRequest;
import com.woodlogix.desktop.api.dto.SalesProjectResponse;

import java.util.Objects;

public final class SalesPipelineApiClient {
    private static final TypeReference<PipelineResponse> PIPELINE =
            new TypeReference<>() {
            };
    private static final TypeReference<SalesProjectResponse> PROJECT =
            new TypeReference<>() {
            };

    private final ApiHttpClient httpClient;

    public SalesPipelineApiClient(ApiHttpClient httpClient) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
    }

    public PipelineResponse findPipeline() {
        return httpClient.get("sales-pipeline", PIPELINE);
    }

    public SalesProjectResponse create(SalesProjectRequest request) {
        return httpClient.post("sales-projects", request, PROJECT);
    }

    public SalesProjectResponse findById(String id) {
        return httpClient.get(ApiUri.path("sales-projects/{segment}", id), PROJECT);
    }

    public void move(String id, ChangeSalesStageRequest request) {
        httpClient.put(ApiUri.path("sales-projects/{segment}/stage", id), request);
    }
}
