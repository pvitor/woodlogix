package com.woodlogix.desktop.api.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.woodlogix.core.enums.SalesStage;
import com.woodlogix.desktop.api.dto.ApiErrorResponse;
import com.woodlogix.desktop.api.dto.ApiFieldErrorResponse;
import com.woodlogix.desktop.api.dto.ChangeSalesStageRequest;
import com.woodlogix.desktop.api.dto.MaterialRequest;
import com.woodlogix.desktop.api.dto.MaterialResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiHttpClientTest {
    private static final String MATERIAL_JSON = """
            {
              "id": "material-1",
              "name": "MDF",
              "color": "White",
              "thicknessMm": 18.00,
              "lengthMm": 2750.00,
              "widthMm": 1840.00,
              "pricePerSheet": 120.50,
              "futureField": "ignored"
            }
            """;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void exchangesJsonForSupportedMethodsAndPreservesDecimalsAndEnums() throws Exception {
        Map<String, String> receivedBodies = new ConcurrentHashMap<>();
        serverClient(exchange -> {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            if ("GET".equals(method)) {
                respond(exchange, 200, MATERIAL_JSON);
                return;
            }
            if ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method)) {
                receivedBodies.put(method, new String(
                        exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                respond(exchange, 200, "PATCH".equals(method)
                        ? "{\"stage\":\"QUOTE_SENT\"}" : MATERIAL_JSON);
                return;
            }
            if ("DELETE".equals(method)) {
                respond(exchange, 204, "");
                return;
            }
            respond(exchange, 405, "");
        }, Duration.ofSeconds(3));

        MaterialRequest materialRequest = new MaterialRequest("MDF", "White",
                new BigDecimal("18.00"), new BigDecimal("2750.00"),
                new BigDecimal("1840.00"), new BigDecimal("120.50"));
        MaterialResponse read = client().get("materials",
                new TypeReference<>() { });
        MaterialResponse created = client().post("/materials", materialRequest,
                new TypeReference<>() { });
        MaterialResponse updated = client().put("materials/material-1", materialRequest,
                new TypeReference<>() { });
        ChangeSalesStageRequest stage = client().patch("projects/project-1/stage",
                new ChangeSalesStageRequest(SalesStage.QUOTE_SENT), new TypeReference<>() { });
        client().delete("materials/material-1");

        assertEquals(new BigDecimal("120.50"), read.pricePerSheet());
        assertEquals(new BigDecimal("120.50"), created.pricePerSheet());
        assertEquals(new BigDecimal("120.50"), updated.pricePerSheet());
        assertEquals(SalesStage.QUOTE_SENT, stage.stage());
        assertTrue(receivedBodies.get("POST").contains("\"pricePerSheet\":120.50"));
        assertTrue(receivedBodies.get("PUT").contains("\"pricePerSheet\":120.50"));
        assertTrue(receivedBodies.get("PATCH").contains("\"stage\":\"QUOTE_SENT\""));
    }

    @Test
    void exposesStructuredHttpErrorFields() throws Exception {
        ApiErrorResponse error = new ApiErrorResponse("VALIDATION_ERROR",
                "error.customer-name",
                List.of(new ApiFieldErrorResponse("fullName", "NOT_BLANK",
                        "error.customer-name")),
                "diagnostic-17");
        serverClient(exchange -> respond(exchange, 422, objectMapper.writeValueAsString(error)),
                Duration.ofSeconds(3));

        ApiHttpException failure = assertThrows(ApiHttpException.class,
                () -> client().get("errors", new TypeReference<>() { }));

        assertEquals(422, failure.statusCode());
        assertEquals(ApiHttpException.ErrorBody.STRUCTURED, failure.errorBody());
        assertEquals("VALIDATION_ERROR", failure.code());
        assertEquals("error.customer-name", failure.messageKey());
        assertEquals(List.of(new ApiFieldErrorResponse("fullName", "NOT_BLANK",
                "error.customer-name")), failure.fieldErrors());
        assertEquals("diagnostic-17", failure.diagnosticId());
        assertEquals("error.customer-name", failure.getMessage());
    }

    @Test
    void distinguishesHttpErrorsWithoutBodyAndWithInvalidBody() throws Exception {
        serverClient(exchange -> {
            if (exchange.getRequestURI().getPath().endsWith("/empty")) {
                respond(exchange, 409, "");
            } else {
                respond(exchange, 400, "{not-json");
            }
        }, Duration.ofSeconds(3));

        ApiHttpException empty = assertThrows(ApiHttpException.class,
                () -> client().get("empty", new TypeReference<>() { }));
        ApiHttpException invalid = assertThrows(ApiHttpException.class,
                () -> client().get("invalid", new TypeReference<>() { }));

        assertEquals(409, empty.statusCode());
        assertEquals(ApiHttpException.ErrorBody.EMPTY, empty.errorBody());
        assertEquals(400, invalid.statusCode());
        assertEquals(ApiHttpException.ErrorBody.INVALID, invalid.errorBody());
        assertNotNull(invalid.getCause());
    }

    @Test
    void supportsSuccessfulResponsesWithoutContent() throws Exception {
        serverClient(exchange -> {
            exchange.getRequestBody().readAllBytes();
            respond(exchange, 204, "");
        }, Duration.ofSeconds(3));

        client().post("materials", new MaterialRequest("MDF", "White",
                new BigDecimal("18.00"), new BigDecimal("2750.00"),
                new BigDecimal("1840.00"), new BigDecimal("120.50")));
        client().put("materials/material-1", new MaterialRequest("MDF", "White",
                new BigDecimal("18.00"), new BigDecimal("2750.00"),
                new BigDecimal("1840.00"), new BigDecimal("120.50")));
        client().patch("projects/project-1/stage",
                new ChangeSalesStageRequest(SalesStage.QUOTE_SENT));
        client().delete("materials/material-1");
    }

    @Test
    void rejectsNullResponseTypesForResponseBearingMethods() {
        ApiHttpClient client = new ApiHttpClient(
                URI.create("http://127.0.0.1:1/api/"),
                Duration.ofSeconds(1),
                Duration.ofSeconds(1));

        assertNullResponseType(() -> client.get("materials", null));
        assertNullResponseType(() -> client.post("materials", null, null));
        assertNullResponseType(() -> client.put("materials/material-1", null, null));
        assertNullResponseType(() -> client.patch("projects/project-1/stage", null, null));
    }

    @Test
    void reportsInvalidJsonInSuccessfulResponseWithStatusAndCause() throws Exception {
        serverClient(exchange -> respond(exchange, 200, "{not-json"),
                Duration.ofSeconds(3));

        ApiResponseException failure = assertThrows(ApiResponseException.class,
                () -> client().get("invalid-response", new TypeReference<>() { }));

        assertEquals(200, failure.statusCode());
        assertNotNull(failure.getCause());
    }

    @Test
    void resolvesPathsAgainstBaseUriWithoutTrailingSlash() throws Exception {
        AtomicReference<String> requestPath = new AtomicReference<>();
        AtomicReference<String> hostHeader = new AtomicReference<>();
        serverClient(exchange -> {
            requestPath.set(exchange.getRequestURI().getRawPath());
            hostHeader.set(exchange.getRequestHeaders().getFirst("Host"));
            respond(exchange, 200, MATERIAL_JSON);
        }, Duration.ofSeconds(3));
        int port = server.getAddress().getPort();
        ApiHttpClient client = new ApiHttpClient(
                URI.create("http://127.0.0.1:" + port + "/api"),
                Duration.ofSeconds(1),
                Duration.ofSeconds(3));

        client.get("materials", new TypeReference<>() { });

        assertEquals("/api/materials", requestPath.get());
        assertEquals("127.0.0.1:" + port, hostHeader.get());
    }

    @Test
    void reportsRequestTimeoutSeparately() throws Exception {
        CountDownLatch requestReceived = new CountDownLatch(1);
        CountDownLatch allowResponse = new CountDownLatch(1);
        serverClient(exchange -> {
            requestReceived.countDown();
            awaitResponse(allowResponse);
            respondIfConnected(exchange, 200, MATERIAL_JSON);
        }, Duration.ofMillis(100));

        try {
            ApiTransportException failure = assertThrows(ApiTransportException.class,
                    () -> client().get("slow", new TypeReference<>() { }));

            assertTrue(requestReceived.await(2, TimeUnit.SECONDS));
            assertEquals(ApiTransportException.Reason.TIMEOUT, failure.reason());
        } finally {
            allowResponse.countDown();
        }
    }

    @Test
    void reportsUnavailableServerAsConnectionFailure() throws Exception {
        ApiHttpClient client = serverClient(exchange -> respond(exchange, 200, MATERIAL_JSON),
                Duration.ofSeconds(3));
        int port = server.getAddress().getPort();
        server.stop(0);
        server = null;
        ApiHttpClient unavailableClient = new ApiHttpClient(
                URI.create("http://127.0.0.1:" + port + "/api/"),
                Duration.ofSeconds(1),
                Duration.ofSeconds(1));

        ApiTransportException failure = assertThrows(ApiTransportException.class,
                () -> unavailableClient.get("materials", new TypeReference<>() { }));

        assertEquals(ApiTransportException.Reason.CONNECTION_FAILURE, failure.reason());
    }

    @Test
    void preservesInterruptStatusWhenRequestIsInterrupted() throws Exception {
        CountDownLatch requestReceived = new CountDownLatch(1);
        CountDownLatch allowResponse = new CountDownLatch(1);
        ApiHttpClient client = serverClient(exchange -> {
            requestReceived.countDown();
            awaitResponse(allowResponse);
            respondIfConnected(exchange, 200, MATERIAL_JSON);
        }, Duration.ofSeconds(5));
        AtomicReference<ApiTransportException> failure = new AtomicReference<>();
        AtomicBoolean interruptRestored = new AtomicBoolean();
        Thread requestThread = new Thread(() -> {
            try {
                client.get("slow", new TypeReference<>() { });
            } catch (ApiTransportException exception) {
                failure.set(exception);
                interruptRestored.set(Thread.currentThread().isInterrupted());
            }
        });

        requestThread.start();
        try {
            assertTrue(requestReceived.await(2, TimeUnit.SECONDS));
            requestThread.interrupt();
            requestThread.join(2_000);

            assertFalse(requestThread.isAlive());
            assertNotNull(failure.get());
            assertEquals(ApiTransportException.Reason.INTERRUPTED, failure.get().reason());
            assertTrue(interruptRestored.get());
        } finally {
            allowResponse.countDown();
            requestThread.join(2_000);
        }
    }

    private ApiHttpClient serverClient(
            com.sun.net.httpserver.HttpHandler handler,
            Duration requestTimeout
    ) throws IOException {
        server = HttpServer.create(new InetSocketAddress(
                InetAddress.getByName("127.0.0.1"), 0), 0);
        server.createContext("/api/", handler);
        server.start();
        return client(requestTimeout);
    }

    private ApiHttpClient client() {
        return client(Duration.ofSeconds(3));
    }

    private ApiHttpClient client(Duration requestTimeout) {
        return new ApiHttpClient(
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api/"),
                Duration.ofSeconds(1),
                requestTimeout);
    }

    private static void assertNullResponseType(Runnable request) {
        NullPointerException failure = assertThrows(NullPointerException.class, request::run);

        assertEquals("responseType must not be null for response-bearing requests",
                failure.getMessage());
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        try (exchange) {
            byte[] content = body.getBytes(StandardCharsets.UTF_8);
            if (content.length == 0) {
                exchange.sendResponseHeaders(status, -1);
                return;
            }
            exchange.sendResponseHeaders(status, content.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(content);
            }
        }
    }

    private static void respondIfConnected(HttpExchange exchange, int status, String body) {
        try {
            respond(exchange, status, body);
        } catch (IOException ignored) {
            exchange.close();
        }
    }

    private static void awaitResponse(CountDownLatch allowResponse) {
        try {
            allowResponse.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
