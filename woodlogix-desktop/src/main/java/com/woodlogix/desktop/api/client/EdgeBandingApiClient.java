package com.woodlogix.desktop.api.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.woodlogix.desktop.api.dto.EdgeBandingRequest;
import com.woodlogix.desktop.api.dto.EdgeBandingResponse;

import java.util.List;
import java.util.Objects;

public final class EdgeBandingApiClient {
    private static final TypeReference<List<EdgeBandingResponse>> EDGE_BANDING_LIST =
            new TypeReference<>() {
            };
    private static final TypeReference<EdgeBandingResponse> EDGE_BANDING =
            new TypeReference<>() {
            };

    private final ApiHttpClient httpClient;

    public EdgeBandingApiClient(ApiHttpClient httpClient) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
    }

    public List<EdgeBandingResponse> findAll() {
        return httpClient.get("edge-bandings", EDGE_BANDING_LIST);
    }

    public EdgeBandingResponse create(EdgeBandingRequest request) {
        return httpClient.post("edge-bandings", request, EDGE_BANDING);
    }

    public EdgeBandingResponse update(String id, EdgeBandingRequest request) {
        return httpClient.put(ApiUri.path("edge-bandings/{segment}", id), request, EDGE_BANDING);
    }

    public void delete(String id) {
        httpClient.delete(ApiUri.path("edge-bandings/{segment}", id));
    }
}
