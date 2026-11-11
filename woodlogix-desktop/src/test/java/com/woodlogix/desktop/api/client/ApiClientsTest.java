package com.woodlogix.desktop.api.client;

import com.woodlogix.core.enums.RoomType;
import com.woodlogix.core.enums.SalesStage;
import com.woodlogix.desktop.api.dto.ChangeSalesStageRequest;
import com.woodlogix.desktop.api.dto.CustomerRequest;
import com.woodlogix.desktop.api.dto.EnvironmentAddRequest;
import com.woodlogix.desktop.api.dto.EnvironmentReplaceRequest;
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

class ApiClientsTest {
    private HttpServer server;
    private List<String> requests;
    private CustomerApiClient customerClient;
    private EnvironmentApiClient environmentClient;
    private SalesPipelineApiClient salesPipelineClient;

    @BeforeEach
    void startServer() throws IOException {
        requests = new ArrayList<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI());
            String path = exchange.getRequestURI().getPath();
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
