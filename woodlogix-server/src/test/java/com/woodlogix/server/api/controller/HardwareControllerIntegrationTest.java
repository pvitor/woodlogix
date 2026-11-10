package com.woodlogix.server.api.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.woodlogix.application.draft.EstimateDraft;
import com.woodlogix.application.draft.EstimateDraftInput;
import com.woodlogix.application.hardware.DeleteHardware;
import com.woodlogix.application.hardware.HardwareCatalog;
import com.woodlogix.application.hardware.HardwareInput;
import com.woodlogix.application.hardware.HardwareRow;
import com.woodlogix.server.WoodLogixServerApplication;
import com.woodlogix.server.api.dto.ApiErrorResponse;
import com.woodlogix.server.api.dto.HardwareRequest;
import com.woodlogix.server.api.dto.HardwareResponse;
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
class HardwareControllerIntegrationTest {
    @TempDir
    static Path temporaryDirectory;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private HardwareCatalog hardwareCatalog;

    @Autowired
    private DeleteHardware deleteHardware;

    @Autowired
    private EstimateDraft estimateDraft;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("woodlogix.database.path",
                () -> temporaryDirectory.resolve("hardwares-api.db").toString());
        registry.add("spring.main.banner-mode", () -> "off");
        registry.add("logging.level.root", () -> "ERROR");
        registry.add("logging.file.name", () -> "");
    }

    @BeforeEach
    void clearFixtures() {
        estimateDraft.saveActiveDraft(emptyDraft());
        hardwareCatalog.findAll().forEach(hardware -> deleteHardware.execute(hardware.id()));
    }

    @Test
    void returnsHardwaresOrderedByName() throws Exception {
        HardwareRow z = createHardware("Z hinge", "PAIR", "12.34");
        HardwareRow a = createHardware("A screw", "UNIT", "0");

        MvcResult result = mockMvc.perform(get("/api/v1/hardwares").accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andReturn();

        List<HardwareResponse> responses = objectMapper.readValue(
                result.getResponse().getContentAsString(), new TypeReference<>() {
                });
        assertEquals(List.of(a.id(), z.id()),
                responses.stream().map(HardwareResponse::id).toList());
        assertEquals(List.of("A screw", "Z hinge"),
                responses.stream().map(HardwareResponse::name).toList());
    }

    @Test
    void createsAndPersistsHardware() throws Exception {
        HardwareRequest request = hardwareRequest("Hinge", "PAIR", "12.34");

        MvcResult result = mockMvc.perform(post("/api/v1/hardwares")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andReturn();

        HardwareResponse response = readResponse(result);
        assertTrue(response.id() != null && !response.id().isBlank());
        assertEquals(request.name(), response.name());
        assertEquals(request.unit(), response.unit());
        assertDecimalEquals(request.unitPrice(), response.unitPrice());
        assertEquals(response.id(), hardwareCatalog.findAll().get(0).id());
    }

    @Test
    void updatesPreservingIdAndPersistsValuesVisibleThroughGet() throws Exception {
        HardwareRow original = createHardware("Hinge", "PAIR", "12.34");
        HardwareRequest request = hardwareRequest("Slide", "BOX", "99.91");

        MvcResult result = mockMvc.perform(put("/api/v1/hardwares/{id}", original.id())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andReturn();

        HardwareResponse response = readResponse(result);
        assertEquals(original.id(), response.id());
        assertEquals("Slide", response.name());
        assertEquals("BOX", response.unit());
        assertDecimalEquals(new BigDecimal("99.91"), response.unitPrice());

        MvcResult getResult = mockMvc.perform(get("/api/v1/hardwares").accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        List<HardwareResponse> responses = objectMapper.readValue(
                getResult.getResponse().getContentAsString(), new TypeReference<>() {
                });
        assertEquals(1, responses.size());
        HardwareResponse persisted = responses.get(0);
        assertEquals(response.id(), persisted.id());
        assertEquals(response.name(), persisted.name());
        assertEquals(response.unit(), persisted.unit());
        assertDecimalEquals(response.unitPrice(), persisted.unitPrice());
    }

    @Test
    void deletesHardwareAndItNoLongerAppearsInGet() throws Exception {
        HardwareRow hardware = createHardware("Hinge", "PAIR", "12.34");

        mockMvc.perform(delete("/api/v1/hardwares/{id}", hardware.id()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        mockMvc.perform(get("/api/v1/hardwares").accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void returnsBadRequestForInvalidDataAndNullJsonWithoutPersisting() throws Exception {
        HardwareRequest invalid = hardwareRequest(" ", "UNIT", "-0.01");

        assertInvalidResponse(mockMvc.perform(post("/api/v1/hardwares")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andReturn());
        assertInvalidResponse(mockMvc.perform(post("/api/v1/hardwares")
                .contentType(APPLICATION_JSON)
                .content("null"))
                .andExpect(status().isBadRequest())
                .andReturn());

        HardwareRow existing = createHardware("Hinge", "PAIR", "12.34");
        assertInvalidResponse(mockMvc.perform(put("/api/v1/hardwares/{id}", existing.id())
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andReturn());
        assertInvalidResponse(mockMvc.perform(put("/api/v1/hardwares/{id}", existing.id())
                .contentType(APPLICATION_JSON)
                .content("null"))
                .andExpect(status().isBadRequest())
                .andReturn());

        HardwareRow persisted = hardwareCatalog.findAll().get(0);
        assertEquals(existing.id(), persisted.id());
        assertEquals(existing.name(), persisted.name());
        assertEquals(existing.unit(), persisted.unit());
        assertDecimalEquals(existing.unitPrice(), persisted.unitPrice());
    }

    @Test
    void returnsNotFoundForUnknownIdsWithoutCreatingRecords() throws Exception {
        HardwareRow existing = createHardware("Hinge", "PAIR", "12.34");
        HardwareRequest request = hardwareRequest("Slide", "BOX", "99.91");

        MvcResult updateResult = mockMvc.perform(put("/api/v1/hardwares/{id}",
                        "missing-hardware")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andReturn();
        assertNotFound(updateResult);

        MvcResult deleteResult = mockMvc.perform(delete("/api/v1/hardwares/{id}",
                        "missing-hardware"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andReturn();
        assertNotFound(deleteResult);
        assertEquals(List.of(existing.id()),
                hardwareCatalog.findAll().stream().map(HardwareRow::id).toList());
    }

    @Test
    void rejectsDeletionReferencedByDraftAndPreservesHardwareAndReference() throws Exception {
        HardwareRow hardware = createHardware("Hinge", "PAIR", "12.34");
        estimateDraft.saveActiveDraft(new EstimateDraftInput(
                null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, List.of(),
                List.of(new EstimateDraftInput.HardwareInput(hardware.id(), 2))));

        MvcResult result = mockMvc.perform(delete("/api/v1/hardwares/{id}", hardware.id()))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andReturn();

        assertConflict(result);
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM hardwares WHERE id = ?", Integer.class, hardware.id()));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM project_estimate_hardwares "
                        + "WHERE hardware_id = ?", Integer.class, hardware.id()));
        assertDatabaseIntegrity();
    }

    private HardwareRow createHardware(String name, String unit, String unitPrice) {
        return hardwareCatalog.create(new HardwareInput(
                name, unit, new BigDecimal(unitPrice)));
    }

    private static HardwareRequest hardwareRequest(
            String name, String unit, String unitPrice
    ) {
        return new HardwareRequest(name, unit, new BigDecimal(unitPrice));
    }

    private static EstimateDraftInput emptyDraft() {
        return new EstimateDraftInput(
                null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, List.of(), List.of());
    }

    private HardwareResponse readResponse(MvcResult result) throws Exception {
        return objectMapper.readValue(result.getResponse().getContentAsString(),
                HardwareResponse.class);
    }

    private void assertInvalidResponse(MvcResult result) throws Exception {
        assertEquals(new ApiErrorResponse(
                        "VALIDATION_ERROR", "validation.hardware-invalid", List.of(), null),
                objectMapper.readValue(result.getResponse().getContentAsString(),
                        ApiErrorResponse.class));
    }

    private void assertNotFound(MvcResult result) throws Exception {
        assertEquals(new ApiErrorResponse(
                        "NOT_FOUND", "error.hardware-not-found", List.of(), null),
                objectMapper.readValue(result.getResponse().getContentAsString(),
                        ApiErrorResponse.class));
    }

    private void assertConflict(MvcResult result) throws Exception {
        assertEquals(new ApiErrorResponse(
                        "CONFLICT", "error.hardware-has-related-records", List.of(), null),
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
