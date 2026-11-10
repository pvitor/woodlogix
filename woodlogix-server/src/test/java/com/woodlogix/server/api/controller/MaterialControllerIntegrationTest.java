package com.woodlogix.server.api.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.woodlogix.application.material.MaterialCatalog;
import com.woodlogix.application.material.MaterialInput;
import com.woodlogix.application.material.MaterialRow;
import com.woodlogix.server.WoodLogixServerApplication;
import com.woodlogix.server.api.dto.MaterialResponse;
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

    private static void assertMaterialResponse(MaterialRow expected, MaterialResponse actual) {
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
