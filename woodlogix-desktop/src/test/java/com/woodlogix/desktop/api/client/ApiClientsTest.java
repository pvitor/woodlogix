package com.woodlogix.desktop.api.client;

import com.woodlogix.core.enums.RoomType;
import com.woodlogix.core.enums.SalesStage;
import com.woodlogix.desktop.api.dto.ChangeSalesStageRequest;
import com.woodlogix.desktop.api.dto.CustomerRequest;
import com.woodlogix.desktop.api.dto.EdgeBandingRequest;
import com.woodlogix.desktop.api.dto.EdgeBandingResponse;
import com.woodlogix.desktop.api.dto.EnvironmentAddRequest;
import com.woodlogix.desktop.api.dto.EnvironmentReplaceRequest;
import com.woodlogix.desktop.api.dto.HardwareRequest;
import com.woodlogix.desktop.api.dto.HardwareResponse;
import com.woodlogix.desktop.api.dto.MaterialRequest;
import com.woodlogix.desktop.api.dto.MaterialResponse;
import com.woodlogix.desktop.api.dto.SalesProjectRequest;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiClientsTest {
    private HttpServer server;
    private List<String> requests;
    private List<String> requestBodies;
    private CustomerApiClient customerClient;
    private MaterialApiClient materialClient;
    private EdgeBandingApiClient edgeBandingClient;
    private HardwareApiClient hardwareClient;
    private EnvironmentApiClient environmentClient;
    private SalesPipelineApiClient salesPipelineClient;
    private boolean failCatalogRequests;

    @BeforeEach
    void startServer() throws IOException {
        requests = new ArrayList<>();
        requestBodies = new ArrayList<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI());
            if (exchange.getRequestMethod().equals("POST")
                    || exchange.getRequestMethod().equals("PUT")) {
                requestBodies.add(new String(
                        exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            }
            String path = exchange.getRequestURI().getPath();
            if (failCatalogRequests && path.startsWith("/api/v1/materials")) {
                exchange.sendResponseHeaders(422, 0);
                exchange.close();
                return;
            }
            String body = path.equals("/api/v1/sales-pipeline")
                    ? "{\"stages\":[]}"
                    : path.contains("/sales-projects/")
                    ? "{\"id\":\"project-id\",\"customerId\":\"customer-id\","
                    + "\"environmentId\":\"environment-id\",\"projectName\":\"Project\","
                    + "\"totalValue\":12.34,\"stage\":\"CONTACT\",\"createdAt\":\"2026-01-01T00:00:00Z\"}"
                    : path.equals("/api/v1/sales-projects")
                    ? "{\"id\":\"project-id\",\"customerId\":\"customer-id\","
                    + "\"environmentId\":\"environment-id\",\"projectName\":\"Project\","
                    + "\"totalValue\":12.34,\"stage\":\"CONTACT\",\"createdAt\":\"2026-01-01T00:00:00Z\"}"
                    : path.equals("/api/v1/customers")
                    && exchange.getRequestMethod().equals("POST")
                    ? "{\"id\":\"customer-id\",\"fullName\":\"Customer\",\"phone\":\"\","
                    + "\"email\":\"\",\"address\":\"\"}"
                    : path.equals("/api/v1/customers/updated-id")
                    ? "{\"id\":\"updated-id\",\"fullName\":\"Customer\",\"phone\":\"\","
                    + "\"email\":\"\",\"address\":\"\"}"
                    : path.startsWith("/api/v1/materials")
                    ? exchange.getRequestMethod().equals("GET")
                    ? "[{\"id\":\"material-id\",\"name\":\"MDF\",\"color\":\"White\","
                    + "\"thicknessMm\":18.00,\"lengthMm\":2750.00,\"widthMm\":1840.00,"
                    + "\"pricePerSheet\":120.50}]"
                    : "{\"id\":\"material-id\",\"name\":\"MDF\",\"color\":\"White\","
                    + "\"thicknessMm\":18.00,\"lengthMm\":2750.00,\"widthMm\":1840.00,"
                    + "\"pricePerSheet\":120.50}"
                    : path.startsWith("/api/v1/edge-bandings")
                    ? exchange.getRequestMethod().equals("GET")
                    ? "[{\"id\":\"edge-id\",\"name\":\"PVC\",\"widthMm\":22.00,"
                    + "\"pricePerMeter\":2.50}]"
                    : "{\"id\":\"edge-id\",\"name\":\"PVC\",\"widthMm\":22.00,"
                    + "\"pricePerMeter\":2.50}"
                    : path.startsWith("/api/v1/hardwares")
                    ? exchange.getRequestMethod().equals("GET")
                    ? "[{\"id\":\"hardware-id\",\"name\":\"Hinge\",\"unit\":\"PAIR\","
                    + "\"unitPrice\":12.34}]"
                    : "{\"id\":\"hardware-id\",\"name\":\"Hinge\",\"unit\":\"PAIR\","
                    + "\"unitPrice\":12.34}"
                    : "[]";
            int status = exchange.getRequestMethod().equals("DELETE")
                    || (exchange.getRequestMethod().equals("PUT")
                    && path.endsWith("/stage")) ? 204 : 200;
            if (exchange.getRequestMethod().equals("POST")
                    && path.equals("/api/v1/customers")) {
                status = 201;
            }
            byte[] response = status == 204 ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, response.length);
            try (var output = exchange.getResponseBody()) {
                output.write(response);
            }
        });
        server.start();
        ApiHttpClient httpClient = new ApiHttpClient(
                java.net.URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1"),
                java.time.Duration.ofSeconds(2),
                java.time.Duration.ofSeconds(2));
        customerClient = new CustomerApiClient(httpClient);
        materialClient = new MaterialApiClient(httpClient);
        edgeBandingClient = new EdgeBandingApiClient(httpClient);
        hardwareClient = new HardwareApiClient(httpClient);
        environmentClient = new EnvironmentApiClient(httpClient);
        salesPipelineClient = new SalesPipelineApiClient(httpClient);
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void usesExactCustomerEndpointsAndEncodesSearchText() {
        customerClient.findAll();
        customerClient.search("João & Silva/1");
        customerClient.findByName("Customer Name");
        customerClient.create(new CustomerRequest("Customer", "", "", ""));
        customerClient.update("updated-id", new CustomerRequest("Customer", "", "", ""));
        customerClient.delete("customer/id");

        assertEquals(List.of(
                "GET /api/v1/customers",
                "GET /api/v1/customers?search=Jo%C3%A3o%20%26%20Silva%2F1",
                "GET /api/v1/customers?name=Customer%20Name",
                "POST /api/v1/customers",
                "PUT /api/v1/customers/updated-id",
                "DELETE /api/v1/customers/customer%2Fid"
        ), requests);
    }

    @Test
    void usesExactEnvironmentEndpoints() {
        environmentClient.findAll();
        environmentClient.findByCustomer("customer/id");
        environmentClient.createForCustomer(
                "customer/id", new EnvironmentAddRequest(List.of(RoomType.KITCHEN), null));
        environmentClient.replaceCustomerEnvironments(
                "customer/id", new EnvironmentReplaceRequest(List.of("KITCHEN")));
        environmentClient.delete("environment/id");
        environmentClient.deleteAllByCustomer("customer/id");

        assertEquals(List.of(
                "GET /api/v1/environments",
                "GET /api/v1/environments?customerId=customer%2Fid",
                "POST /api/v1/customers/customer%2Fid/environments",
                "PUT /api/v1/customers/customer%2Fid/environments",
                "DELETE /api/v1/environments/environment%2Fid",
                "DELETE /api/v1/customers/customer%2Fid/environments"
        ), requests);
    }

    @Test
    void usesExactCatalogEndpointsPreservesDecimalsAndAcceptsNoContent() {
        List<MaterialResponse> materials = materialClient.findAll();
        MaterialRequest materialRequest = new MaterialRequest(
                "MDF", "White", new BigDecimal("18.00"), new BigDecimal("2750.00"),
                new BigDecimal("1840.00"), new BigDecimal("120.50"));
        MaterialResponse material = materialClient.create(materialRequest);
        MaterialResponse updatedMaterial = materialClient.update(
                "material/id", materialRequest);
        materialClient.delete("material/id");

        List<EdgeBandingResponse> edgeBandings = edgeBandingClient.findAll();
        EdgeBandingRequest edgeBandingRequest = new EdgeBandingRequest(
                "PVC", new BigDecimal("22.00"), new BigDecimal("2.50"));
        EdgeBandingResponse edgeBanding = edgeBandingClient.create(edgeBandingRequest);
        EdgeBandingResponse updatedEdgeBanding = edgeBandingClient.update(
                "edge/id", edgeBandingRequest);
        edgeBandingClient.delete("edge/id");

        List<HardwareResponse> hardwares = hardwareClient.findAll();
        HardwareRequest hardwareRequest = new HardwareRequest(
                "Hinge", "PAIR", new BigDecimal("12.34"));
        HardwareResponse hardware = hardwareClient.create(hardwareRequest);
        HardwareResponse updatedHardware = hardwareClient.update(
                "hardware/id", hardwareRequest);
        hardwareClient.delete("hardware/id");

        assertEquals(new BigDecimal("120.50"), materials.get(0).pricePerSheet());
        assertEquals(new BigDecimal("120.50"), material.pricePerSheet());
        assertEquals(new BigDecimal("120.50"), updatedMaterial.pricePerSheet());
        assertEquals(new BigDecimal("2.50"), edgeBandings.get(0).pricePerMeter());
        assertEquals(new BigDecimal("2.50"), edgeBanding.pricePerMeter());
        assertEquals(new BigDecimal("2.50"), updatedEdgeBanding.pricePerMeter());
        assertEquals(new BigDecimal("12.34"), hardwares.get(0).unitPrice());
        assertEquals(new BigDecimal("12.34"), hardware.unitPrice());
        assertEquals(new BigDecimal("12.34"), updatedHardware.unitPrice());
        assertTrue(requestBodies.stream().anyMatch(body ->
                body.contains("\"pricePerSheet\":120.50")));
        assertTrue(requestBodies.stream().anyMatch(body ->
                body.contains("\"pricePerMeter\":2.50")));
        assertTrue(requestBodies.stream().anyMatch(body ->
                body.contains("\"unitPrice\":12.34")));
        assertTrue(requests.contains("PUT /api/v1/materials/material%2Fid"));
        assertTrue(requests.contains("DELETE /api/v1/materials/material%2Fid"));
        assertTrue(requests.contains("PUT /api/v1/edge-bandings/edge%2Fid"));
        assertTrue(requests.contains("DELETE /api/v1/edge-bandings/edge%2Fid"));
        assertTrue(requests.contains("PUT /api/v1/hardwares/hardware%2Fid"));
        assertTrue(requests.contains("DELETE /api/v1/hardwares/hardware%2Fid"));
    }

    @Test
    void propagatesCatalogHttpErrors() {
        failCatalogRequests = true;

        assertEquals(422, assertThrows(ApiHttpException.class,
                () -> materialClient.findAll()).statusCode());
    }

    @Test
    void propagatesCatalogTransportFailures() {
        int port = server.getAddress().getPort();
        server.stop(0);

        ApiHttpClient unavailableHttpClient = new ApiHttpClient(
                java.net.URI.create("http://127.0.0.1:" + port + "/api/v1"),
                java.time.Duration.ofSeconds(1),
                java.time.Duration.ofSeconds(1));
        HardwareApiClient unavailableHardwareClient = new HardwareApiClient(unavailableHttpClient);

        assertEquals(ApiTransportException.Reason.CONNECTION_FAILURE,
                assertThrows(ApiTransportException.class,
                        unavailableHardwareClient::findAll).reason());
    }

    @Test
    void usesExactSalesPipelineEndpointsAndAcceptsNoContent() {
        salesPipelineClient.findPipeline();
        salesPipelineClient.create(new SalesProjectRequest(
                "customer-id", "environment-id", "Project", BigDecimal.valueOf(12.34)));
        salesPipelineClient.findById("project/id");
        salesPipelineClient.move("project/id", new ChangeSalesStageRequest(SalesStage.CONTACT));

        assertEquals(List.of(
                "GET /api/v1/sales-pipeline",
                "POST /api/v1/sales-projects",
                "GET /api/v1/sales-projects/project%2Fid",
                "PUT /api/v1/sales-projects/project%2Fid/stage"
        ), requests);
    }
}
