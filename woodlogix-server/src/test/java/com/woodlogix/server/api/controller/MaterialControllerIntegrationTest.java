package com.woodlogix.server.api.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.woodlogix.application.draft.EstimateDraft;
import com.woodlogix.application.draft.EstimateDraftInput;
import com.woodlogix.application.material.MaterialCatalog;
import com.woodlogix.application.material.MaterialInput;
import com.woodlogix.application.material.MaterialRow;
import com.woodlogix.server.WoodLogixServerApplication;
import com.woodlogix.server.api.dto.ApiErrorResponse;
import com.woodlogix.server.api.dto.MaterialRequest;
import com.woodlogix.server.api.dto.MaterialResponse;
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
class MaterialControllerIntegrationTest {
    @TempDir
    static Path temporaryDirectory;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MaterialCatalog materialCatalog;

    @Autowired
    private EstimateDraft estimateDraft;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("woodlogix.database.path",
                () -> temporaryDirectory.resolve("materials-api.db").toString());
        registry.add("spring.main.banner-mode", () -> "off");
        registry.add("logging.level.root", () -> "ERROR");
        registry.add("logging.file.name", () -> "");
    }

    @BeforeEach
    void clearMaterials() {
        materialCatalog.findAll().forEach(material -> materialCatalog.delete(material.id()));
    }

    @Test
    void returnsJsonMaterialsOrderedByNameColorAndThicknessFromTemporaryDatabase() throws Exception {
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1' AND success = 1",
                Integer.class));
        String actualDatabasePath = jdbcTemplate.queryForObject(
                "PRAGMA database_list",
                (resultSet, rowNumber) -> resultSet.getString("file"));
        assertEquals(temporaryDirectory.resolve("materials-api.db").toAbsolutePath().normalize(),
                Path.of(actualDatabasePath).toAbsolutePath().normalize());

        mockMvc.perform(get("/api/v1/materials").accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(content().json("[]"));

        MaterialRow birch = createMaterial(
                "Birch", "Natural", "18.25", "2750.25", "1840.50", "123456789.01");
        MaterialRow oakWhiteThick = createMaterial(
                "Oak", "White", "18.00", "2750.00", "1840.00", "120.50");
        MaterialRow oakBlack = createMaterial(
                "Oak", "Black", "18.00", "2440.00", "1220.00", "98.75");
        MaterialRow oakWhiteThin = createMaterial(
                "Oak", "White", "6.00", "2200.00", "1100.00", "45.25");

        MvcResult result = mockMvc.perform(get("/api/v1/materials").accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andReturn();

        List<MaterialResponse> responses = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<>() {
                });
        assertEquals(4, responses.size());
        assertMaterialResponse(birch, responses.get(0));
        assertMaterialResponse(oakBlack, responses.get(1));
        assertMaterialResponse(oakWhiteThin, responses.get(2));
        assertMaterialResponse(oakWhiteThick, responses.get(3));
        assertTrue(responses.get(2).thicknessMm().compareTo(new BigDecimal("6.00")) == 0);
        assertTrue(responses.get(3).pricePerSheet().compareTo(new BigDecimal("120.50")) == 0);
    }

    @Test
    void createsMaterialAndPersistsItThroughTheCatalog() throws Exception {
        MaterialRequest request = materialRequest(
                "MDF", "White", "18.25", "2750.25", "1840.50", "123456789.01");

        MvcResult result = mockMvc.perform(post("/api/v1/materials")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andReturn();

        MaterialResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), MaterialResponse.class);
        MaterialRow expected = new MaterialRow(response.id(), request.name(), request.color(),
                request.thicknessMm(), request.lengthMm(), request.widthMm(), request.pricePerSheet());
        assertTrue(response.id() != null && !response.id().isBlank());
        assertMaterialResponse(expected, response);

        MaterialRow persisted = materialCatalog.findAll().stream()
                .filter(material -> material.id().equals(response.id()))
                .findFirst()
                .orElseThrow();
        assertMaterialResponse(persisted, response);
    }

    @Test
    void acceptsAndPersistsZeroPrice() throws Exception {
        MaterialRequest request = materialRequest(
                "MDF", "Natural", "18.00", "2750.00", "1840.00", "0");

        MvcResult result = mockMvc.perform(post("/api/v1/materials")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andReturn();

        MaterialResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), MaterialResponse.class);
        assertEquals(0, response.pricePerSheet().compareTo(BigDecimal.ZERO));
        MaterialRow persisted = materialCatalog.findAll().stream()
                .filter(material -> material.id().equals(response.id()))
                .findFirst()
                .orElseThrow();
        assertEquals(0, persisted.pricePerSheet().compareTo(BigDecimal.ZERO));
    }

    @Test
    void updatesMaterialAndPersistsValuesWithoutChangingItsId() throws Exception {
        MaterialRow original = createMaterial(
                "MDF", "White", "18.00", "2750.00", "1840.00", "120.50");
        MaterialRequest request = materialRequest(
                "Plywood", "Natural", "15.00", "2440.00", "1220.00", "98.75");

        MvcResult result = mockMvc.perform(put("/api/v1/materials/{id}", original.id())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andReturn();

        MaterialResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), MaterialResponse.class);
        MaterialResponse expected = new MaterialResponse(original.id(), request.name(),
                request.color(), request.thicknessMm(), request.lengthMm(),
                request.widthMm(), request.pricePerSheet());
        assertEquals(expected, response);

        MvcResult getResult = mockMvc.perform(get("/api/v1/materials").accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        List<MaterialResponse> materials = objectMapper.readValue(
                getResult.getResponse().getContentAsString(), new TypeReference<>() {
                });
        assertEquals(1, materials.size());
        assertMaterialResponse(new MaterialRow(expected.id(), expected.name(), expected.color(),
                expected.thicknessMm(), expected.lengthMm(), expected.widthMm(),
                expected.pricePerSheet()), materials.get(0));
    }

    @Test
    void updatesMaterialReferencedByAnActiveDraftWithoutChangingTheRelationship() throws Exception {
        MaterialRow material = createMaterial(
                "MDF", "White", "18.00", "2750.00", "1840.00", "120.50");
        estimateDraft.saveActiveDraft(new EstimateDraftInput(
                null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO,
                List.of(new EstimateDraftInput.WorkpieceInput(
                        null, "Panel", new BigDecimal("100"), new BigDecimal("50"), 1,
                        material.id(), null, false, false, false, false)),
                List.of()));

        try {
            MaterialRequest request = materialRequest(
                    "Plywood", "Natural", "15.00", "2440.00", "1220.00", "98.75");
            mockMvc.perform(put("/api/v1/materials/{id}", material.id())
                            .contentType(APPLICATION_JSON)
                            .content(objectMapper.writeValueAsBytes(request)))
                    .andExpect(status().isOk());

            assertEquals(material.id(), jdbcTemplate.queryForObject(
                    "SELECT material_id FROM project_estimate_workpieces LIMIT 1",
                    String.class));
            assertEquals("Plywood", jdbcTemplate.queryForObject(
                    "SELECT name FROM materials WHERE id = ?", String.class, material.id()));
        } finally {
            estimateDraft.saveActiveDraft(new EstimateDraftInput(
                    null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, List.of(), List.of()));
        }
    }

    @Test
    void returnsStructuredBadRequestForInvalidMaterialFieldsWithoutPersisting() throws Exception {
        List<MaterialRequest> invalidRequests = List.of(
                materialRequest(null, "White", "18", "2750", "1840", "120"),
                materialRequest("MDF", null, "18", "2750", "1840", "120"),
                materialRequest("", "White", "18", "2750", "1840", "120"),
                materialRequest("MDF", " ", "18", "2750", "1840", "120"),
                materialRequest("MDF", "White", "0", "2750", "1840", "120"),
                materialRequest("MDF", "White", "18", "0", "1840", "120"),
                materialRequest("MDF", "White", "18", "2750", "0", "120"),
                materialRequest("MDF", "White", "18", "2750", "1840", "-0.01")
        );
        ApiErrorResponse expectedError = new ApiErrorResponse(
                "VALIDATION_ERROR", "validation.material-invalid", List.of(), null);

        for (MaterialRequest request : invalidRequests) {
            int existingMaterials = materialCatalog.findAll().size();
            MvcResult result = mockMvc.perform(post("/api/v1/materials")
                            .contentType(APPLICATION_JSON)
                            .content(objectMapper.writeValueAsBytes(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                    .andReturn();

            ApiErrorResponse error = objectMapper.readValue(
                    result.getResponse().getContentAsString(), ApiErrorResponse.class);
            assertEquals(expectedError, error);
            assertEquals(existingMaterials, materialCatalog.findAll().size());
        }
    }

    @Test
    void returnsStructuredBadRequestForInvalidUpdateWithoutModifyingMaterial() throws Exception {
        MaterialRow original = createMaterial(
                "MDF", "White", "18.00", "2750.00", "1840.00", "120.50");
        List<MaterialRequest> invalidRequests = List.of(
                materialRequest(null, "White", "18", "2750", "1840", "120"),
                materialRequest("MDF", null, "18", "2750", "1840", "120"),
                materialRequest("", "White", "18", "2750", "1840", "120"),
                materialRequest("MDF", " ", "18", "2750", "1840", "120"),
                materialRequest("MDF", "White", "0", "2750", "1840", "120"),
                materialRequest("MDF", "White", "18", "0", "1840", "120"),
                materialRequest("MDF", "White", "18", "2750", "0", "120"),
                materialRequest("MDF", "White", "18", "2750", "1840", "-0.01")
        );
        ApiErrorResponse expectedError = new ApiErrorResponse(
                "VALIDATION_ERROR", "validation.material-invalid", List.of(), null);

        for (MaterialRequest request : invalidRequests) {
            MvcResult result = mockMvc.perform(put("/api/v1/materials/{id}", original.id())
                            .contentType(APPLICATION_JSON)
                            .content(objectMapper.writeValueAsBytes(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                    .andReturn();

            assertEquals(expectedError, objectMapper.readValue(
                    result.getResponse().getContentAsString(), ApiErrorResponse.class));
        }

        assertMaterialResponse(original, materialCatalog.findAll().get(0));
    }

    @Test
    void returnsStructuredBadRequestForNullJsonRequestWithoutPersisting() throws Exception {
        int existingMaterials = materialCatalog.findAll().size();

        MvcResult result = mockMvc.perform(post("/api/v1/materials")
                        .contentType(APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andReturn();

        ApiErrorResponse error = objectMapper.readValue(
                result.getResponse().getContentAsString(), ApiErrorResponse.class);
        assertEquals(new ApiErrorResponse(
                "VALIDATION_ERROR", "validation.material-invalid", List.of(), null), error);
        assertEquals(existingMaterials, materialCatalog.findAll().size());
    }

    @Test
    void returnsStructuredBadRequestForNullJsonUpdateWithoutPersisting() throws Exception {
        MaterialRow original = createMaterial(
                "MDF", "White", "18.00", "2750.00", "1840.00", "120.50");

        MvcResult result = mockMvc.perform(put("/api/v1/materials/{id}", original.id())
                        .contentType(APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andReturn();

        assertEquals(new ApiErrorResponse(
                "VALIDATION_ERROR", "validation.material-invalid", List.of(), null),
                objectMapper.readValue(
                        result.getResponse().getContentAsString(), ApiErrorResponse.class));
        assertMaterialResponse(original, materialCatalog.findAll().get(0));
    }

    @Test
    void returnsNotFoundForUnknownMaterialIdWithoutCreatingOrModifyingMaterials() throws Exception {
        MaterialRow existing = createMaterial(
                "MDF", "White", "18.00", "2750.00", "1840.00", "120.50");
        MaterialRequest request = materialRequest(
                "Plywood", "Natural", "15.00", "2440.00", "1220.00", "98.75");

        MvcResult result = mockMvc.perform(put("/api/v1/materials/{id}",
                        "missing-material-id")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andReturn();

        assertEquals(new ApiErrorResponse(
                "NOT_FOUND", "error.material-not-found", List.of(), null),
                objectMapper.readValue(
                        result.getResponse().getContentAsString(), ApiErrorResponse.class));
        assertEquals(1, materialCatalog.findAll().size());
        assertMaterialResponse(existing, materialCatalog.findAll().get(0));
    }

    private MaterialRow createMaterial(
            String name,
            String color,
            String thicknessMm,
            String lengthMm,
            String widthMm,
            String pricePerSheet
    ) {
        return materialCatalog.create(new MaterialInput(
                name,
                color,
                new BigDecimal(thicknessMm),
                new BigDecimal(lengthMm),
                new BigDecimal(widthMm),
                new BigDecimal(pricePerSheet)));
    }

    private static MaterialRequest materialRequest(
            String name,
            String color,
            String thicknessMm,
            String lengthMm,
            String widthMm,
            String pricePerSheet
    ) {
        return new MaterialRequest(name, color, decimal(thicknessMm), decimal(lengthMm),
                decimal(widthMm), decimal(pricePerSheet));
    }

    private static BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    private static void assertMaterialResponse(MaterialRow expected, MaterialResponse actual) {
        assertEquals(expected.id(), actual.id());
        assertEquals(expected.name(), actual.name());
        assertEquals(expected.color(), actual.color());
        assertDecimalEquals(expected.thicknessMm(), actual.thicknessMm());
        assertDecimalEquals(expected.lengthMm(), actual.lengthMm());
        assertDecimalEquals(expected.widthMm(), actual.widthMm());
        assertDecimalEquals(expected.pricePerSheet(), actual.pricePerSheet());
    }

    private static void assertMaterialResponse(MaterialRow expected, MaterialRow actual) {
        assertEquals(expected.id(), actual.id());
        assertEquals(expected.name(), actual.name());
        assertEquals(expected.color(), actual.color());
        assertDecimalEquals(expected.thicknessMm(), actual.thicknessMm());
        assertDecimalEquals(expected.lengthMm(), actual.lengthMm());
        assertDecimalEquals(expected.widthMm(), actual.widthMm());
        assertDecimalEquals(expected.pricePerSheet(), actual.pricePerSheet());
    }

    private static void assertDecimalEquals(BigDecimal expected, BigDecimal actual) {
        assertTrue(actual != null && expected.compareTo(actual) == 0,
                () -> "Expected " + expected + " but was " + actual);
    }
}
