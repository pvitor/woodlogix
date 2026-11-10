package com.woodlogix.server.api.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.woodlogix.application.customer.CustomerCatalog;
import com.woodlogix.application.customer.CustomerInput;
import com.woodlogix.application.material.MaterialCatalog;
import com.woodlogix.application.material.MaterialInput;
import com.woodlogix.application.sales.SalesPipeline;
import com.woodlogix.application.sales.SalesProjectInput;
import com.woodlogix.application.workpiece.ProjectWorkpiece;
import com.woodlogix.application.workpiece.ProjectWorkpieceInput;
import com.woodlogix.application.workpiece.ProjectWorkpieceRow;
import com.woodlogix.server.WoodLogixServerApplication;
import com.woodlogix.server.api.dto.ApiErrorResponse;
import com.woodlogix.server.api.dto.ProjectWorkpieceRequest;
import com.woodlogix.server.api.dto.ProjectWorkpieceResponse;
import com.woodlogix.server.persistence.repository.ProjectWorkpieceRepository;
import com.woodlogix.application.edgebanding.EdgeBandingCatalog;
import com.woodlogix.application.edgebanding.EdgeBandingInput;
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
class ProjectWorkpieceControllerIntegrationTest {
    @TempDir
    static Path temporaryDirectory;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerCatalog customerCatalog;

    @Autowired
    private SalesPipeline salesPipeline;

    @Autowired
    private MaterialCatalog materialCatalog;

    @Autowired
    private EdgeBandingCatalog edgeBandingCatalog;

    @Autowired
    private ProjectWorkpiece projectWorkpiece;

    @Autowired
    private ProjectWorkpieceRepository workpieceRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("woodlogix.database.path",
                () -> temporaryDirectory.resolve("project-workpiece-api.db").toString());
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
        jdbcTemplate.update("DELETE FROM materials");
        jdbcTemplate.update("DELETE FROM edge_bandings");
        jdbcTemplate.update("DELETE FROM customers");
    }

    @Test
    void listsAllAndFiltersByProjectReturningEmptyForMissingProject() throws Exception {
        Fixture fixture = fixture();
        ProjectWorkpieceRow first = create(fixture, "Z panel", true);
        ProjectWorkpieceRow second = create(fixture, "A shelf", false);

        List<ProjectWorkpieceResponse> all = read(mockMvc.perform(
                        get("/api/v1/project-workpieces").accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andReturn(), new TypeReference<>() {
        });
        List<ProjectWorkpieceResponse> byProject = read(mockMvc.perform(
                        get("/api/v1/sales-projects/{projectId}/workpieces",
                                fixture.projectId()).accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn(), new TypeReference<>() {
        });
        List<ProjectWorkpieceResponse> missingProject = read(mockMvc.perform(
                        get("/api/v1/sales-projects/{projectId}/workpieces", "missing")
                                .accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn(), new TypeReference<>() {
        });

        assertEquals(List.of(second.id(), first.id()),
                all.stream().map(ProjectWorkpieceResponse::id).toList());
        assertEquals(List.of(second.id(), first.id()),
                byProject.stream().map(ProjectWorkpieceResponse::id).toList());
        assertTrue(missingProject.isEmpty());
    }

    @Test
    void createsWithAndWithoutEdgeBandingAndPersistsThroughGet() throws Exception {
        Fixture fixture = fixture();
        ProjectWorkpieceRequest withEdge = request(
                fixture, "Panel", fixture.edgeBandingId(), true);
        ProjectWorkpieceRequest withoutEdge = request(
                fixture, "Shelf", null, false);

        ProjectWorkpieceResponse createdWithEdge = createHttp(withEdge);
        ProjectWorkpieceResponse createdWithoutEdge = createHttp(withoutEdge);
        List<ProjectWorkpieceResponse> persisted = read(mockMvc.perform(
                        get("/api/v1/sales-projects/{projectId}/workpieces",
                                fixture.projectId()))
                .andExpect(status().isOk())
                .andReturn(), new TypeReference<>() {
        });

        assertEquals(fixture.materialId(), createdWithEdge.material().id());
        assertEquals(fixture.edgeBandingId(), createdWithEdge.edgeBanding().id());
        assertEquals(null, createdWithoutEdge.edgeBanding());
        assertEquals(2, workpieceRepository.count());
        assertEquals(List.of(createdWithEdge.id(), createdWithoutEdge.id()),
                persisted.stream().map(ProjectWorkpieceResponse::id).toList());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM project_workpieces WHERE id = ? AND project_id = ?",
                Integer.class, createdWithEdge.id(), fixture.projectId()));
        assertTrue(jdbcTemplate.queryForList("PRAGMA foreign_key_check").isEmpty());
    }

    @Test
    void updatesAllowedFieldsPreservingIdRelationshipsAndOtherRecords() throws Exception {
        Fixture fixture = fixture();
        ProjectWorkpieceRow original = create(fixture, "Original", false);
        ProjectWorkpieceRow other = create(fixture, "Other", false);
        ProjectWorkpieceRequest update = request(
                fixture, "Updated", fixture.edgeBandingId(), true);

        ProjectWorkpieceResponse response = read(mockMvc.perform(
                        put("/api/v1/project-workpieces/{id}", original.id())
                                .contentType(APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(update)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andReturn(), new TypeReference<>() {
        });
        List<ProjectWorkpieceResponse> all = read(mockMvc.perform(
                        get("/api/v1/project-workpieces").accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn(), new TypeReference<>() {
        });
        ProjectWorkpieceResponse persisted = all.stream()
                .filter(item -> item.id().equals(original.id())).findFirst().orElseThrow();

        assertEquals(original.id(), response.id());
        assertEquals("Updated", response.name());
        assertEquals(fixture.edgeBandingId(), response.edgeBanding().id());
        assertEquals(new BigDecimal("321.50"), response.lengthMm());
        assertEquals(4, response.quantity());
        assertEquals(0, response.lengthMm().compareTo(persisted.lengthMm()));
        assertEquals(0, response.widthMm().compareTo(persisted.widthMm()));
        assertEquals(response.id(), persisted.id());
        assertEquals(response.name(), persisted.name());
        assertEquals(response.quantity(), persisted.quantity());
        assertEquals(response.material(), persisted.material());
        assertEquals(response.edgeBanding(), persisted.edgeBanding());
        assertEquals(response.edgeTop(), persisted.edgeTop());
        assertEquals("Other", workpieceRepository.findById(other.id()).orElseThrow().getName());
        assertEquals(2, workpieceRepository.count());
        assertTrue(jdbcTemplate.queryForList("PRAGMA foreign_key_check").isEmpty());
    }

    @Test
    void mapsMissingReferencesAndMissingWorkpieceToStructuredNotFound() throws Exception {
        Fixture fixture = fixture();

        assertNotFound(mockMvc.perform(post("/api/v1/project-workpieces")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(new ProjectWorkpieceRequest(
                                "missing-project", "Panel", bd("100"), bd("50"), 1,
                                fixture.materialId(), null, false, false, false, false))))
                .andExpect(status().isNotFound()).andReturn(),
                "error.sales-project-not-found");
        assertNotFound(mockMvc.perform(post("/api/v1/project-workpieces")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(new ProjectWorkpieceRequest(
                                fixture.projectId(), "Panel", bd("100"), bd("50"), 1,
                                "missing-material", null, false, false, false, false))))
                .andExpect(status().isNotFound()).andReturn(),
                "error.material-not-found");
        assertNotFound(mockMvc.perform(post("/api/v1/project-workpieces")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(new ProjectWorkpieceRequest(
                                fixture.projectId(), "Panel", bd("100"), bd("50"), 1,
                                fixture.materialId(), "missing-edge", true, false, false, false))))
                .andExpect(status().isNotFound()).andReturn(),
                "error.edge-banding-not-found");
        assertNotFound(mockMvc.perform(put("/api/v1/project-workpieces/{id}", "missing")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request(
                                fixture, "Panel", null, false))))
                .andExpect(status().isNotFound()).andReturn(),
                "error.workpiece-not-found");
        assertEquals(0, workpieceRepository.count());
    }

    @Test
    void mapsInvalidValuesNullBodiesAndMalformedJsonToBadRequest() throws Exception {
        Fixture fixture = fixture();
        List<ProjectWorkpieceRequest> invalid = List.of(
                new ProjectWorkpieceRequest(fixture.projectId(), null, bd("100"), bd("50"),
                        1, fixture.materialId(), null, false, false, false, false),
                new ProjectWorkpieceRequest(fixture.projectId(), " ", bd("100"), bd("50"),
                        1, fixture.materialId(), null, false, false, false, false),
                new ProjectWorkpieceRequest(fixture.projectId(), "Panel", null, bd("50"),
                        1, fixture.materialId(), null, false, false, false, false),
                new ProjectWorkpieceRequest(fixture.projectId(), "Panel", BigDecimal.ZERO,
                        bd("50"), 1, fixture.materialId(), null, false, false, false, false),
                new ProjectWorkpieceRequest(fixture.projectId(), "Panel", bd("-1"), bd("50"),
                        1, fixture.materialId(), null, false, false, false, false),
                new ProjectWorkpieceRequest(fixture.projectId(), "Panel", bd("100"), null,
                        1, fixture.materialId(), null, false, false, false, false),
                new ProjectWorkpieceRequest(fixture.projectId(), "Panel", bd("100"), bd("50"),
                        0, fixture.materialId(), null, false, false, false, false),
                new ProjectWorkpieceRequest(fixture.projectId(), "Panel", bd("100"), bd("50"),
                        -1, fixture.materialId(), null, false, false, false, false),
                new ProjectWorkpieceRequest(fixture.projectId(), "Panel", bd("100"), bd("50"),
                        1, fixture.materialId(), null, true, false, false, false)
        );

        for (ProjectWorkpieceRequest request : invalid) {
            assertInvalid(mockMvc.perform(post("/api/v1/project-workpieces")
                            .contentType(APPLICATION_JSON)
                            .content(objectMapper.writeValueAsBytes(request)))
                    .andExpect(status().isBadRequest()).andReturn());
        }
        assertInvalid(mockMvc.perform(post("/api/v1/project-workpieces")
                        .contentType(APPLICATION_JSON).content("null"))
                .andExpect(status().isBadRequest()).andReturn());
        assertInvalid(mockMvc.perform(put("/api/v1/project-workpieces/{id}", "missing")
                        .contentType(APPLICATION_JSON).content("null"))
                .andExpect(status().isBadRequest()).andReturn());
        assertInvalid(mockMvc.perform(post("/api/v1/project-workpieces")
                        .contentType(APPLICATION_JSON).content("{\"projectId\":"))
                .andExpect(status().isBadRequest()).andReturn());
        assertInvalid(mockMvc.perform(put("/api/v1/project-workpieces/{id}", "missing")
                        .contentType(APPLICATION_JSON).content("{\"name\":"))
                .andExpect(status().isBadRequest()).andReturn());

        assertEquals(0, workpieceRepository.count());
    }

    private Fixture fixture() {
        String customerId = customerCatalog.create(
                new CustomerInput("Customer", null, null, null)).id();
        String projectId = salesPipeline.create(new SalesProjectInput(
                customerId, null, "Project", bd("100"))).id();
        String materialId = materialCatalog.create(new MaterialInput(
                "MDF", "White", bd("18"), bd("1000"), bd("1000"), bd("120"))).id();
        String edgeBandingId = edgeBandingCatalog.create(new EdgeBandingInput(
                "PVC", bd("22"), bd("2.50"))).id();
        return new Fixture(projectId, materialId, edgeBandingId);
    }

    private ProjectWorkpieceRow create(Fixture fixture, String name, boolean withEdge) {
        return projectWorkpiece.create(new ProjectWorkpieceInput(
                fixture.projectId(), name, bd("100"), bd("200"), 2,
                fixture.materialId(), withEdge ? fixture.edgeBandingId() : null,
                withEdge, false, false, false));
    }

    private ProjectWorkpieceResponse createHttp(ProjectWorkpieceRequest request)
            throws Exception {
        return read(mockMvc.perform(post("/api/v1/project-workpieces")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andReturn(), new TypeReference<>() {
        });
    }

    private ProjectWorkpieceRequest request(
            Fixture fixture, String name, String edgeBandingId, boolean edgeTop
    ) {
        return new ProjectWorkpieceRequest(
                fixture.projectId(), name, bd("321.50"), bd("210.25"), 4,
                fixture.materialId(), edgeBandingId, edgeTop, false, false, false);
    }

    private void assertInvalid(MvcResult result) throws Exception {
        ApiErrorResponse error = objectMapper.readValue(
                result.getResponse().getContentAsString(), ApiErrorResponse.class);
        assertEquals("VALIDATION_ERROR", error.code());
        assertEquals("validation.workpiece-invalid", error.messageKey());
        assertEquals(List.of(), error.fieldErrors());
        assertEquals(null, error.diagnosticId());
    }

    private void assertNotFound(MvcResult result, String messageKey) throws Exception {
        ApiErrorResponse error = objectMapper.readValue(
                result.getResponse().getContentAsString(), ApiErrorResponse.class);
        assertEquals("NOT_FOUND", error.code());
        assertEquals(messageKey, error.messageKey());
        assertEquals(List.of(), error.fieldErrors());
        assertEquals(null, error.diagnosticId());
    }

    private <T> T read(MvcResult result, TypeReference<T> type) throws Exception {
        return objectMapper.readValue(result.getResponse().getContentAsString(), type);
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private record Fixture(String projectId, String materialId, String edgeBandingId) {
    }
}
