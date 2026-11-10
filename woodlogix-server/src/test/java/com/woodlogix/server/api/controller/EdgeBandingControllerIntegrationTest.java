package com.woodlogix.server.api.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.woodlogix.application.customer.CustomerCatalog;
import com.woodlogix.application.customer.CustomerInput;
import com.woodlogix.application.draft.EstimateDraft;
import com.woodlogix.application.draft.EstimateDraftInput;
import com.woodlogix.application.edgebanding.EdgeBandingCatalog;
import com.woodlogix.application.edgebanding.EdgeBandingInput;
import com.woodlogix.application.edgebanding.EdgeBandingRow;
import com.woodlogix.application.material.MaterialCatalog;
import com.woodlogix.application.material.MaterialInput;
import com.woodlogix.application.sales.SalesPipeline;
import com.woodlogix.application.sales.SalesProjectInput;
import com.woodlogix.application.workpiece.ProjectWorkpiece;
import com.woodlogix.application.workpiece.ProjectWorkpieceInput;
import com.woodlogix.server.WoodLogixServerApplication;
import com.woodlogix.server.api.dto.ApiErrorResponse;
import com.woodlogix.server.api.dto.EdgeBandingRequest;
import com.woodlogix.server.api.dto.EdgeBandingResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
class EdgeBandingControllerIntegrationTest {
    @TempDir
    static Path temporaryDirectory;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EdgeBandingCatalog edgeBandingCatalog;

    @Autowired
    private MaterialCatalog materialCatalog;

    @Autowired
    private EstimateDraft estimateDraft;

    @Autowired
    private CustomerCatalog customerCatalog;

    @Autowired
    private SalesPipeline salesPipeline;

    @Autowired
    private ProjectWorkpiece projectWorkpiece;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("woodlogix.database.path",
                () -> temporaryDirectory.resolve("edge-bandings-api.db").toString());
        registry.add("spring.main.banner-mode", () -> "off");
        registry.add("logging.level.root", () -> "ERROR");
        registry.add("logging.file.name", () -> "");
    }

    @BeforeEach
    void clearFixtures() {
        estimateDraft.saveActiveDraft(emptyDraft());
        projectWorkpiece.findAll().forEach(workpiece -> projectWorkpiece.delete(workpiece.id()));
        edgeBandingCatalog.findAll().forEach(edgeBanding ->
                edgeBandingCatalog.delete(edgeBanding.id()));
    }

    @Test
    void returnsEdgeBandingsOrderedByNameAndWidth() throws Exception {
        EdgeBandingRow wide = createEdgeBanding("PVC", "30", "2.50");
        EdgeBandingRow narrow = createEdgeBanding("PVC", "10", "1.25");
        EdgeBandingRow abs = createEdgeBanding("ABS", "20", "3.00");

        MvcResult result = mockMvc.perform(get("/api/v1/edge-bandings").accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andReturn();

        List<EdgeBandingResponse> responses = objectMapper.readValue(
                result.getResponse().getContentAsString(), new TypeReference<>() {
                });
        assertEquals(List.of(abs.id(), narrow.id(), wide.id()),
                responses.stream().map(EdgeBandingResponse::id).toList());
    }

    @Test
    void createsAndPersistsEdgeBanding() throws Exception {
        EdgeBandingRequest request = edgeBandingRequest("PVC", "22.00", "1.25");

        MvcResult result = mockMvc.perform(post("/api/v1/edge-bandings")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andReturn();

        EdgeBandingResponse response = readResponse(result);
        assertTrue(response.id() != null && !response.id().isBlank());
        assertEquals(request.name(), response.name());
        assertDecimalEquals(request.widthMm(), response.widthMm());
        assertDecimalEquals(request.pricePerMeter(), response.pricePerMeter());
        assertEquals(response.id(), edgeBandingCatalog.findAll().get(0).id());
    }

    @Test
    void updatesPreservingIdAndPersistsValuesVisibleThroughGet() throws Exception {
        EdgeBandingRow original = createEdgeBanding("PVC", "22", "1.25");
        EdgeBandingRequest request = edgeBandingRequest("ABS", "30", "3.50");

        MvcResult result = mockMvc.perform(put("/api/v1/edge-bandings/{id}", original.id())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andReturn();

        EdgeBandingResponse response = readResponse(result);
        assertEquals(original.id(), response.id());
        assertEquals("ABS", response.name());
        assertDecimalEquals(new BigDecimal("30"), response.widthMm());
        assertDecimalEquals(new BigDecimal("3.50"), response.pricePerMeter());

        MvcResult getResult = mockMvc.perform(get("/api/v1/edge-bandings").accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        List<EdgeBandingResponse> responses = objectMapper.readValue(
                getResult.getResponse().getContentAsString(), new TypeReference<>() {
                });
        assertEquals(1, responses.size());
        EdgeBandingResponse persisted = responses.get(0);
        assertEquals(response.id(), persisted.id());
        assertEquals(response.name(), persisted.name());
        assertDecimalEquals(response.widthMm(), persisted.widthMm());
        assertDecimalEquals(response.pricePerMeter(), persisted.pricePerMeter());
    }

    @Test
    void deletesEdgeBandingAndItNoLongerAppearsInGet() throws Exception {
        EdgeBandingRow edgeBanding = createEdgeBanding("PVC", "22", "1.25");

        mockMvc.perform(delete("/api/v1/edge-bandings/{id}", edgeBanding.id()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        mockMvc.perform(get("/api/v1/edge-bandings").accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void returnsBadRequestForInvalidDataAndNullJsonWithoutPersisting() throws Exception {
        EdgeBandingRequest invalid = edgeBandingRequest(" ", "0", "-1");

        assertInvalidResponse(mockMvc.perform(post("/api/v1/edge-bandings")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andReturn());
        assertInvalidResponse(mockMvc.perform(post("/api/v1/edge-bandings")
                .contentType(APPLICATION_JSON)
                .content("null"))
                .andExpect(status().isBadRequest())
                .andReturn());
        EdgeBandingRow existing = createEdgeBanding("PVC", "22", "1.25");
        assertInvalidResponse(mockMvc.perform(put("/api/v1/edge-bandings/{id}", existing.id())
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andReturn());
        assertInvalidResponse(mockMvc.perform(put("/api/v1/edge-bandings/{id}", "missing")
                .contentType(APPLICATION_JSON)
                .content("null"))
                .andExpect(status().isBadRequest())
                .andReturn());
        EdgeBandingRow persisted = edgeBandingCatalog.findAll().get(0);
        assertEquals(existing.id(), persisted.id());
        assertEquals(existing.name(), persisted.name());
        assertDecimalEquals(existing.widthMm(), persisted.widthMm());
        assertDecimalEquals(existing.pricePerMeter(), persisted.pricePerMeter());
    }

    @Test
    void returnsNotFoundForUnknownIdsWithoutCreatingRecords() throws Exception {
        EdgeBandingRow existing = createEdgeBanding("PVC", "22", "1.25");
        EdgeBandingRequest request = edgeBandingRequest("ABS", "30", "3.50");

        MvcResult updateResult = mockMvc.perform(put("/api/v1/edge-bandings/{id}",
                        "missing-edge-banding")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andReturn();
        assertNotFound(updateResult);

        MvcResult deleteResult = mockMvc.perform(delete("/api/v1/edge-bandings/{id}",
                        "missing-edge-banding"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andReturn();
        assertNotFound(deleteResult);
        assertEquals(List.of(existing.id()),
                edgeBandingCatalog.findAll().stream().map(EdgeBandingRow::id).toList());
    }

    @Test
    void rejectsDeletionReferencedByProjectWorkpieceAndPreservesDatabase() throws Exception {
        EdgeBandingRow edgeBanding = createEdgeBanding("PVC", "22", "1.25");
        var material = materialCatalog.create(new MaterialInput(
                "MDF", "White", new BigDecimal("18"), new BigDecimal("2750"),
                new BigDecimal("1840"), new BigDecimal("120")));
        var customer = customerCatalog.create(new CustomerInput("Customer", null, null, null));
        var project = salesPipeline.create(new SalesProjectInput(
                customer.id(), null, "Project", new BigDecimal("100")));
        var workpiece = projectWorkpiece.create(new ProjectWorkpieceInput(
                project.id(), "Panel", new BigDecimal("100"), new BigDecimal("50"), 1,
                material.id(), edgeBanding.id(), true, false, false, false));

        MvcResult result = mockMvc.perform(delete("/api/v1/edge-bandings/{id}", edgeBanding.id()))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andReturn();

        assertConflict(result);
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM edge_bandings WHERE id = ?", Integer.class, edgeBanding.id()));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM project_workpieces "
                        + "WHERE id = ? AND edge_banding_id = ?",
                Integer.class, workpiece.id(), edgeBanding.id()));
        assertDatabaseIntegrity();
    }

    @Test
    void rejectsDeletionReferencedByDraftWorkpieceAndPreservesDatabase() throws Exception {
        EdgeBandingRow edgeBanding = createEdgeBanding("PVC", "22", "1.25");
        var material = materialCatalog.create(new MaterialInput(
                "MDF", "White", new BigDecimal("18"), new BigDecimal("2750"),
                new BigDecimal("1840"), new BigDecimal("120")));
        estimateDraft.saveActiveDraft(new EstimateDraftInput(
                null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO,
                List.of(new EstimateDraftInput.WorkpieceInput(
                        null, "Panel", new BigDecimal("100"), new BigDecimal("50"), 1,
                        material.id(), edgeBanding.id(), true, false, false, false)),
                List.of()));

        MvcResult result = mockMvc.perform(delete("/api/v1/edge-bandings/{id}", edgeBanding.id()))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andReturn();

        assertConflict(result);
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM edge_bandings WHERE id = ?", Integer.class, edgeBanding.id()));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM project_estimate_workpieces "
                        + "WHERE edge_banding_id = ?", Integer.class, edgeBanding.id()));
        assertDatabaseIntegrity();
    }

    private EdgeBandingRow createEdgeBanding(String name, String widthMm, String pricePerMeter) {
        return edgeBandingCatalog.create(new EdgeBandingInput(
                name, new BigDecimal(widthMm), new BigDecimal(pricePerMeter)));
    }

    private static EdgeBandingRequest edgeBandingRequest(
            String name, String widthMm, String pricePerMeter
    ) {
        return new EdgeBandingRequest(name, new BigDecimal(widthMm), new BigDecimal(pricePerMeter));
    }

    private static EstimateDraftInput emptyDraft() {
        return new EstimateDraftInput(
                null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, List.of(), List.of());
    }

    private EdgeBandingResponse readResponse(MvcResult result) throws Exception {
        return objectMapper.readValue(result.getResponse().getContentAsString(),
                EdgeBandingResponse.class);
    }

    private void assertInvalidResponse(MvcResult result) throws Exception {
        assertEquals(new ApiErrorResponse(
                        "VALIDATION_ERROR", "validation.edge-banding-invalid", List.of(), null),
                objectMapper.readValue(result.getResponse().getContentAsString(),
                        ApiErrorResponse.class));
    }

    private void assertNotFound(MvcResult result) throws Exception {
        assertEquals(new ApiErrorResponse(
                        "NOT_FOUND", "error.edge-banding-not-found", List.of(), null),
                objectMapper.readValue(result.getResponse().getContentAsString(),
                        ApiErrorResponse.class));
    }

    private void assertConflict(MvcResult result) throws Exception {
        assertEquals(new ApiErrorResponse(
                        "CONFLICT", "error.edge-banding-has-related-records", List.of(), null),
                objectMapper.readValue(result.getResponse().getContentAsString(),
                        ApiErrorResponse.class));
    }

    private void assertDatabaseIntegrity() {
        assertTrue(jdbcTemplate.queryForList("PRAGMA foreign_key_check").isEmpty());
    }

    private static void assertDecimalEquals(BigDecimal expected, BigDecimal actual) {
        assertTrue(actual != null && expected.compareTo(actual) == 0,
                () -> "Expected " + expected + " but was " + actual);
    }
}
