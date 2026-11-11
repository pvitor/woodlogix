package com.woodlogix.desktop.api.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.woodlogix.desktop.api.dto.HardwareRequest;
import com.woodlogix.desktop.api.dto.HardwareResponse;

import java.util.List;
import java.util.Objects;

public final class HardwareApiClient {
    private static final TypeReference<List<HardwareResponse>> HARDWARE_LIST =
            new TypeReference<>() {
            };
    private static final TypeReference<HardwareResponse> HARDWARE =
            new TypeReference<>() {
            };

    private final ApiHttpClient httpClient;

    public HardwareApiClient(ApiHttpClient httpClient) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
    }

    public List<HardwareResponse> findAll() {
        return httpClient.get("hardwares", HARDWARE_LIST);
    }

    public HardwareResponse create(HardwareRequest request) {
        return httpClient.post("hardwares", request, HARDWARE);
    }

    public HardwareResponse update(String id, HardwareRequest request) {
        return httpClient.put(ApiUri.path("hardwares/{segment}", id), request, HARDWARE);
    }

    public void delete(String id) {
        httpClient.delete(ApiUri.path("hardwares/{segment}", id));
    }
}
