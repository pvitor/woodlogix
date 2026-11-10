package com.woodlogix.server.api.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.woodlogix.application.customer.CustomerCatalog;
import com.woodlogix.application.customer.CustomerInput;
import com.woodlogix.application.environment.EnvironmentCatalog;
import com.woodlogix.application.sales.SalesPipeline;
import com.woodlogix.application.sales.SalesPipelinePort;
import com.woodlogix.application.sales.SalesProjectInput;
import com.woodlogix.application.sales.SalesProjectRow;
import com.woodlogix.core.enums.SalesStage;
import com.woodlogix.server.WoodLogixServerApplication;
import com.woodlogix.server.api.dto.ApiErrorResponse;
import com.woodlogix.server.api.dto.ChangeSalesStageRequest;
import com.woodlogix.server.api.dto.PipelineResponse;
import com.woodlogix.server.api.dto.SalesProjectRequest;
import com.woodlogix.server.api.dto.SalesProjectResponse;
import com.woodlogix.server.persistence.repository.SalesProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = WoodLogixServerApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@AutoConfigureMockMvc
class SalesPipelineControllerIntegrationTest {
    @TempDir
    static Path temporaryDirectory;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerCatalog customerCatalog;

    @Autowired
    private EnvironmentCatalog environmentCatalog;

    @Autowired
    private SalesPipeline salesPipeline;

    @Autowired
    private SalesProjectRepository salesProjectRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("woodlogix.database.path",
                () -> temporaryDirectory.resolve("sales-pipeline-api.db").toString());
        registry.add("spring.main.banner-mode", () -> "off");
        registry.add("logging.level.root", () -> "ERROR");
        registry.add("logging.file.name", () -> "");
    }

    @BeforeEach
    void clearFixtures() {
        jdbcTemplate.update("DELETE FROM project_estimate_hardwares");
        jdbcTemplate.update("DELETE FROM project_estimate_workpieces");
        jdbcTemplate.update("DELETE FROM project_estimates");
        jdbcTemplate.update("DELETE FROM project_workpieces");
        jdbcTemplate.update("DELETE FROM sales_projects");
        jdbcTemplate.update("DELETE FROM project_environments");
        jdbcTemplate.update("DELETE FROM customers");
    }

    @Test
    void listsEveryStageAndProjectsInApplicationOrder() throws Exception {
        String customerId = customer("Customer");
        SalesProjectRow older = createProject(customerId, "Older", "10");
        SalesProjectRow newer = createProject(customerId, "Newer", "20");
        SalesProjectRow closed = createProject(customerId, "Closed", "30");
        salesPipeline.move(closed.id(), SalesStage.CLOSED);
        jdbcTemplate.update("UPDATE sales_projects SET created_at = ? WHERE id = ?",
                "2025-01-01T10:00:00", older.id());
        jdbcTemplate.update("UPDATE sales_projects SET created_at = ? WHERE id = ?",
                "2025-01-02T10:00:00", newer.id());

        PipelineResponse response = read(
                mockMvc.perform(get("/api/v1/sales-pipeline").accept(APPLICATION_JSON))
                        .andExpect(status().isOk())
                        .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                        .andReturn(),
                new TypeReference<>() {
                });

        assertEquals(List.of(SalesStage.values()),
                response.stages().stream().map(stage -> stage.stage()).toList());
        assertEquals(List.of(newer.id(), older.id()),
                response.stages().get(0).projects().stream()
                        .map(SalesProjectResponse::id).toList());
        assertEquals(List.of(closed.id()),
                response.stages().get(SalesStage.CLOSED.ordinal()).projects().stream()
                        .map(SalesProjectResponse::id).toList());
        assertTrue(response.stages().stream().skip(1).limit(3)
                .allMatch(stage -> stage.projects().isEmpty()));
    }

    @Test
    void createsProjectsWithAndWithoutEnvironmentAndPersistsResponses() throws Exception {
        String customerId = customer("Customer");
        String environmentId = environmentCatalog.create(customerId, "Kitchen").id();

        SalesProjectResponse linked = read(mockMvc.perform(post("/api/v1/sales-projects")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(new SalesProjectRequest(
                                customerId, environmentId, "Kitchen project",
                                new BigDecimal("1234.56")))))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andReturn(), new TypeReference<>() {
        });
        SalesProjectResponse unlinked = read(mockMvc.perform(post("/api/v1/sales-projects")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(new SalesProjectRequest(
                                customerId, "  ", "Free project",
                                new BigDecimal("99.00")))))
                .andExpect(status().isCreated())
                .andReturn(), new TypeReference<>() {
        });

        assertEquals("Kitchen", linked.environmentName());
        assertEquals("", unlinked.environmentName());
        assertEquals(2, salesProjectRepository.count());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sales_projects WHERE id = ? AND customer_id = ? "
                        + "AND environment_id = ?", Integer.class,
                linked.id(), customerId, environmentId));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sales_projects WHERE id = ? AND environment_id IS NULL",
                Integer.class, unlinked.id()));
    }

    @Test
    void mapsInvalidReferencesToStructuredErrorsWithoutCreatingProjects() throws Exception {
        String customerId = customer("Customer");
        String otherCustomerId = customer("Other");
        String environmentId = environmentCatalog.create(otherCustomerId, "Kitchen").id();
        SalesProjectRequest request = new SalesProjectRequest(
                customerId, environmentId, "Project", BigDecimal.ONE);

        assertError(mockMvc.perform(post("/api/v1/sales-projects")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(new SalesProjectRequest(
                                "missing", null, "Project", BigDecimal.ONE))))
                .andExpect(status().isNotFound()).andReturn(),
                "error.customer-not-found");
        assertError(mockMvc.perform(post("/api/v1/sales-projects")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(new SalesProjectRequest(
                                customerId, "missing", "Project", BigDecimal.ONE))))
                .andExpect(status().isNotFound()).andReturn(),
                "error.environment-not-found");
        assertInvalid(mockMvc.perform(post("/api/v1/sales-projects")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isBadRequest()).andReturn());
        assertEquals(0, salesProjectRepository.count());
        assertFalse(environmentId.isBlank());
        assertFalse(otherCustomerId.equals(customerId));
    }

    @Test
    void mapsNullInvalidAndMalformedCreationRequestsToBadRequest() throws Exception {
        String customerId = customer("Customer");

        assertInvalid(mockMvc.perform(post("/api/v1/sales-projects")
                        .contentType(APPLICATION_JSON).content("null"))
                .andExpect(status().isBadRequest()).andReturn());
        assertInvalid(mockMvc.perform(post("/api/v1/sales-projects")
                        .contentType(APPLICATION_JSON)
                        .content("{\"customerId\":null,\"projectName\":\"Project\","
                                + "\"totalValue\":1}"))
                .andExpect(status().isBadRequest()).andReturn());
        assertInvalid(mockMvc.perform(post("/api/v1/sales-projects")
                        .contentType(APPLICATION_JSON)
                        .content("{\"customerId\":\"" + customerId
                                + "\",\"projectName\":\"Project\",\"totalValue\":-1}"))
                .andExpect(status().isBadRequest()).andReturn());
        assertInvalid(mockMvc.perform(post("/api/v1/sales-projects")
                        .contentType(APPLICATION_JSON)
                        .content("{\"customerId\":\"" + customerId
                                + "\",\"projectName\":\"Project\",\"totalValue\":"))
                .andExpect(status().isBadRequest()).andReturn());
        assertEquals(0, salesProjectRepository.count());
    }

    @Test
    void getsExistingAndMissingProjects() throws Exception {
        String customerId = customer("Customer");
        SalesProjectRow project = createProject(customerId, "Project", "1234.56");

        SalesProjectResponse response = read(mockMvc.perform(
                        get("/api/v1/sales-projects/{id}", project.id())
                                .accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andReturn(), new TypeReference<>() {
        });
        assertEquals(project.id(), response.id());
        assertEquals("Project", response.projectName());

        assertError(mockMvc.perform(get("/api/v1/sales-projects/{id}", "missing"))
                        .andExpect(status().isNotFound()).andReturn(),
                "error.project-not-found");
    }

    @Test
    void changesStagePreservingOtherFieldsAndAcceptsAnyValidStage() throws Exception {
        String customerId = customer("Customer");
        String environmentId = environmentCatalog.create(customerId, "Kitchen").id();
        SalesProjectRow project = createProject(customerId, environmentId, "Project", "1234.56");
        var before = jdbcTemplate.queryForMap(
                "SELECT * FROM sales_projects WHERE id = ?", project.id());

        mockMvc.perform(put("/api/v1/sales-projects/{id}/stage", project.id())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(
                                new ChangeSalesStageRequest(SalesStage.CLOSED))))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        mockMvc.perform(put("/api/v1/sales-projects/{id}/stage", project.id())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(
                                new ChangeSalesStageRequest(SalesStage.CONTACT))))
                .andExpect(status().isNoContent());

        var after = jdbcTemplate.queryForMap(
                "SELECT * FROM sales_projects WHERE id = ?", project.id());
        assertEquals(SalesStage.CONTACT.name(), after.get("stage"));
        assertEquals(before.get("id"), after.get("id"));
        assertEquals(before.get("customer_id"), after.get("customer_id"));
        assertEquals(before.get("environment_id"), after.get("environment_id"));
        assertEquals(before.get("project_name"), after.get("project_name"));
        assertEquals(before.get("total_value"), after.get("total_value"));
    }

    @Test
    void mapsMissingProjectAndInvalidStageRequests() throws Exception {
        assertError(mockMvc.perform(put("/api/v1/sales-projects/{id}/stage", "missing")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(
                                new ChangeSalesStageRequest(SalesStage.CLOSED))))
                .andExpect(status().isNotFound()).andReturn(),
                "error.project-not-found");
        assertInvalid(mockMvc.perform(put("/api/v1/sales-projects/{id}/stage", "missing")
                        .contentType(APPLICATION_JSON).content("null"))
                .andExpect(status().isBadRequest()).andReturn());
        assertInvalid(mockMvc.perform(put("/api/v1/sales-projects/{id}/stage", "missing")
                        .contentType(APPLICATION_JSON)
                        .content("{\"stage\":null}"))
                .andExpect(status().isBadRequest()).andReturn());
        assertInvalid(mockMvc.perform(put("/api/v1/sales-projects/{id}/stage", "missing")
                        .contentType(APPLICATION_JSON)
                        .content("{\"stage\":\"NOT_A_STAGE\"}"))
                .andExpect(status().isBadRequest()).andReturn());
    }

    @Test
    void rollsBackCreationAfterFlushWhenPersistenceFails() throws Exception {
        String customerId = customer("Customer");
        SalesPipelinePort realPort = (SalesPipelinePort) ReflectionTestUtils
                .getField(salesPipeline, "projects");
        SalesPipelinePort intercepted = org.mockito.Mockito.mock(
                SalesPipelinePort.class,
                org.mockito.AdditionalAnswers.delegatesTo(realPort));
        org.mockito.Mockito.doAnswer(invocation -> {
            SalesProjectRow created = realPort.createAndFlush(invocation.getArgument(0));
            assertTrue(salesProjectRepository.existsById(created.id()));
            throw new IllegalStateException("injected pipeline failure");
        }).when(intercepted).createAndFlush(
                org.mockito.ArgumentMatchers.any(SalesProjectInput.class));
        ReflectionTestUtils.setField(salesPipeline, "projects", intercepted);
        try {
            assertThrows(Exception.class, () -> mockMvc.perform(post("/api/v1/sales-projects")
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(new SalesProjectRequest(
                            customerId, null, "Rollback", BigDecimal.ONE)))));
        } finally {
            ReflectionTestUtils.setField(salesPipeline, "projects", realPort);
        }

        assertEquals(0, salesProjectRepository.count());
        assertTrue(jdbcTemplate.queryForList("PRAGMA foreign_key_check").isEmpty());
    }

    private String customer(String name) {
        return customerCatalog.create(new CustomerInput(name, null, null, null)).id();
    }

    private SalesProjectRow createProject(String customerId, String name, String totalValue) {
        return createProject(customerId, null, name, totalValue);
    }

    private SalesProjectRow createProject(
            String customerId, String environmentId, String name, String totalValue
    ) {
        return salesPipeline.create(new SalesProjectInput(
                customerId, environmentId, name, new BigDecimal(totalValue)));
    }

    private void assertInvalid(MvcResult result) throws Exception {
        assertError(result, "validation.sales-project-invalid");
    }

    private void assertError(MvcResult result, String messageKey) throws Exception {
        ApiErrorResponse error = objectMapper.readValue(
                result.getResponse().getContentAsString(), ApiErrorResponse.class);
        assertEquals("VALIDATION_ERROR".equals(messageKey)
                        ? "VALIDATION_ERROR" : messageKey.startsWith("error.")
                        ? "NOT_FOUND" : "VALIDATION_ERROR", error.code());
        assertEquals(messageKey, error.messageKey());
        assertEquals(List.of(), error.fieldErrors());
        assertEquals(null, error.diagnosticId());
    }

    private void assertError(MvcResult result, String messageKey, boolean ignored)
            throws Exception {
        assertError(result, messageKey);
    }

    private <T> T read(MvcResult result, TypeReference<T> type) throws Exception {
        return objectMapper.readValue(result.getResponse().getContentAsString(), type);
    }
}
