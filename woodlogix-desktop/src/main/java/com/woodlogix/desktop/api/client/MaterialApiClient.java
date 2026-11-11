package com.woodlogix.desktop.api.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.woodlogix.desktop.api.dto.MaterialRequest;
import com.woodlogix.desktop.api.dto.MaterialResponse;

import java.util.List;
import java.util.Objects;

public final class MaterialApiClient {
    private static final TypeReference<List<MaterialResponse>> MATERIAL_LIST =
            new TypeReference<>() {
            };
    private static final TypeReference<MaterialResponse> MATERIAL =
            new TypeReference<>() {
            };

    private final ApiHttpClient httpClient;

    public MaterialApiClient(ApiHttpClient httpClient) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
    }

    public List<MaterialResponse> findAll() {
        return httpClient.get("materials", MATERIAL_LIST);
    }

    public MaterialResponse create(MaterialRequest request) {
        return httpClient.post("materials", request, MATERIAL);
    }

    public MaterialResponse update(String id, MaterialRequest request) {
        return httpClient.put(ApiUri.path("materials/{segment}", id), request, MATERIAL);
    }

    public void delete(String id) {
        httpClient.delete(ApiUri.path("materials/{segment}", id));
    }
}
