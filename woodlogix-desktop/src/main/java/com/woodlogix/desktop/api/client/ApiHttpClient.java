package com.woodlogix.desktop.api.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.woodlogix.desktop.api.dto.ApiErrorResponse;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

public final class ApiHttpClient {
    private static final String JSON_MEDIA_TYPE = "application/json";

    private final HttpClient httpClient;
    private final URI baseUri;
    private final Duration requestTimeout;
    private final ObjectMapper objectMapper;

    public ApiHttpClient(URI baseUri, Duration connectTimeout, Duration requestTimeout) {
        this(baseUri, connectTimeout, requestTimeout, JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build());
    }

    public ApiHttpClient(
            URI baseUri,
            Duration connectTimeout,
            Duration requestTimeout,
            ObjectMapper objectMapper
    ) {
        this.baseUri = normalizeBaseUri(baseUri);
        this.requestTimeout = requirePositive(requestTimeout, "requestTimeout");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(requirePositive(connectTimeout, "connectTimeout"))
                .build();
    }

    public <T> T get(String path, TypeReference<T> responseType) {
        return exchange("GET", path, null, requireResponseType(responseType));
    }

    public <T> T post(String path, Object requestBody, TypeReference<T> responseType) {
        return exchange("POST", path, requestBody, requireResponseType(responseType));
    }

    public void post(String path, Object requestBody) {
        exchange("POST", path, requestBody, null);
    }

    public <T> T put(String path, Object requestBody, TypeReference<T> responseType) {
        return exchange("PUT", path, requestBody, requireResponseType(responseType));
    }

    public void put(String path, Object requestBody) {
        exchange("PUT", path, requestBody, null);
    }

    public <T> T patch(String path, Object requestBody, TypeReference<T> responseType) {
        return exchange("PATCH", path, requestBody, requireResponseType(responseType));
    }

    public void patch(String path, Object requestBody) {
        exchange("PATCH", path, requestBody, null);
    }

    public void delete(String path) {
        exchange("DELETE", path, null, null);
    }

    private <T> T exchange(
            String method,
            String path,
            Object requestBody,
            TypeReference<T> responseType
    ) {
        HttpRequest request = buildRequest(method, path, requestBody);
        HttpResponse<String> response;
        try {
            response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.net.http.HttpTimeoutException exception) {
            throw new ApiTransportException(ApiTransportException.Reason.TIMEOUT, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ApiTransportException(ApiTransportException.Reason.INTERRUPTED, exception);
        } catch (IOException exception) {
            throw new ApiTransportException(
                    ApiTransportException.Reason.CONNECTION_FAILURE, exception);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw httpException(response.statusCode(), response.body());
        }
        if (responseType == null) {
            return null;
        }
        if (response.body().isBlank()) {
            throw new ApiResponseException(response.statusCode(),
                    "Successful HTTP response did not contain the expected JSON body", null);
        }

        try {
            return objectMapper.readValue(response.body(), responseType);
        } catch (JsonProcessingException exception) {
            throw new ApiResponseException(response.statusCode(),
                    "Successful HTTP response contained invalid JSON", exception);
        }
    }

    private HttpRequest buildRequest(String method, String path, Object requestBody) {
        URI requestUri = resolve(path);
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(requestUri)
                .timeout(requestTimeout)
                .header("Accept", JSON_MEDIA_TYPE);

        if (requestBody == null) {
            return requestBuilder.method(method, HttpRequest.BodyPublishers.noBody()).build();
        }

        String json;
        try {
            json = objectMapper.writeValueAsString(requestBody);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Request body could not be serialized as JSON",
                    exception);
        }
        return requestBuilder.header("Content-Type", JSON_MEDIA_TYPE)
                .method(method, HttpRequest.BodyPublishers.ofString(json))
                .build();
    }

    private ApiHttpException httpException(int statusCode, String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return new ApiHttpException(statusCode, ApiHttpException.ErrorBody.EMPTY, null, null);
        }
        try {
            ApiErrorResponse error = objectMapper.readValue(responseBody, ApiErrorResponse.class);
            return new ApiHttpException(
                    statusCode, ApiHttpException.ErrorBody.STRUCTURED, error, null);
        } catch (JsonProcessingException exception) {
            return new ApiHttpException(
                    statusCode, ApiHttpException.ErrorBody.INVALID, null, exception);
        }
    }

    private URI resolve(String path) {
        Objects.requireNonNull(path, "path must not be null");
        if (path.isBlank()) {
            throw new IllegalArgumentException("path must not be blank");
        }

        URI relativeUri = URI.create(path);
        if (relativeUri.isAbsolute()
                || relativeUri.getRawAuthority() != null
                || relativeUri.getFragment() != null) {
            throw new IllegalArgumentException("path must be a relative URI without a fragment");
        }

        String relativePath = path;
        while (relativePath.startsWith("/")) {
            relativePath = relativePath.substring(1);
        }
        return baseUri.resolve(relativePath);
    }

    private static URI normalizeBaseUri(URI baseUri) {
        Objects.requireNonNull(baseUri, "baseUri must not be null");
        String scheme = baseUri.getScheme();
        if (!baseUri.isAbsolute()
                || baseUri.getHost() == null
                || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                || baseUri.getRawUserInfo() != null
                || baseUri.getRawQuery() != null
                || baseUri.getRawFragment() != null) {
            throw new IllegalArgumentException(
                    "baseUri must be an absolute HTTP(S) URI without credentials, query, or fragment");
        }

        String normalized = baseUri.toString();
        return URI.create(normalized.endsWith("/") ? normalized : normalized + "/");
    }

    private static Duration requirePositive(Duration duration, String name) {
        Objects.requireNonNull(duration, name + " must not be null");
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return duration;
    }

    private static <T> TypeReference<T> requireResponseType(TypeReference<T> responseType) {
        return Objects.requireNonNull(
                responseType, "responseType must not be null for response-bearing requests");
    }
}
