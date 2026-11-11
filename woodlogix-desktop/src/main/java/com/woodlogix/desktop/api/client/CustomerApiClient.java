package com.woodlogix.desktop.api.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.woodlogix.desktop.api.dto.CustomerRequest;
import com.woodlogix.desktop.api.dto.CustomerResponse;

import java.util.List;
import java.util.Objects;

public final class CustomerApiClient {
    private static final TypeReference<List<CustomerResponse>> CUSTOMER_LIST =
            new TypeReference<>() {
            };
    private static final TypeReference<CustomerResponse> CUSTOMER =
            new TypeReference<>() {
            };

    private final ApiHttpClient httpClient;

    public CustomerApiClient(ApiHttpClient httpClient) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
    }

    public List<CustomerResponse> findAll() {
        return httpClient.get("customers", CUSTOMER_LIST);
    }

    public List<CustomerResponse> search(String text) {
        return httpClient.get(ApiUri.query("customers", "search", text), CUSTOMER_LIST);
    }

    public List<CustomerResponse> findByName(String name) {
        return httpClient.get(ApiUri.query("customers", "name", name), CUSTOMER_LIST);
    }

    public CustomerResponse create(CustomerRequest request) {
        return httpClient.post("customers", request, CUSTOMER);
    }

    public CustomerResponse update(String id, CustomerRequest request) {
        return httpClient.put(ApiUri.path("customers/{segment}", id), request, CUSTOMER);
    }

    public void delete(String id) {
        httpClient.delete(ApiUri.path("customers/{segment}", id));
    }
}
