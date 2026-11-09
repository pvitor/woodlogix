package com.woodlogix.server.api.dto;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.woodlogix.core.enums.RoomType;
import com.woodlogix.core.enums.SalesStage;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransportDtoJsonTest {
    private final ObjectMapper objectMapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    @Test
    void serializesAndDeserializesDtosUsingTheStableContractExample() throws Exception {
        Map<String, Object> examples = examples();
        JsonNode actual = objectMapper.valueToTree(examples);

        assertJsonEquivalent(readContractExample(), actual);
        assertRoundTrip(actual, "customerRequest", CustomerRequest.class,
                (CustomerRequest) examples.get("customerRequest"));
        assertRoundTrip(actual, "customerResponse", CustomerResponse.class,
                (CustomerResponse) examples.get("customerResponse"));
        assertRoundTrip(actual, "environmentAddRequest", EnvironmentAddRequest.class,
                (EnvironmentAddRequest) examples.get("environmentAddRequest"));
        assertRoundTrip(actual, "environmentReplaceRequest", EnvironmentReplaceRequest.class,
                (EnvironmentReplaceRequest) examples.get("environmentReplaceRequest"));
        assertRoundTrip(actual, "environmentResponse", EnvironmentResponse.class,
                (EnvironmentResponse) examples.get("environmentResponse"));
        assertRoundTrip(actual, "materialRequest", MaterialRequest.class,
                (MaterialRequest) examples.get("materialRequest"));
        assertRoundTrip(actual, "materialResponse", MaterialResponse.class,
                (MaterialResponse) examples.get("materialResponse"));
        assertRoundTrip(actual, "edgeBandingRequest", EdgeBandingRequest.class,
                (EdgeBandingRequest) examples.get("edgeBandingRequest"));
        assertRoundTrip(actual, "edgeBandingResponse", EdgeBandingResponse.class,
                (EdgeBandingResponse) examples.get("edgeBandingResponse"));
        assertRoundTrip(actual, "hardwareRequest", HardwareRequest.class,
                (HardwareRequest) examples.get("hardwareRequest"));
        assertRoundTrip(actual, "hardwareResponse", HardwareResponse.class,
                (HardwareResponse) examples.get("hardwareResponse"));
        assertRoundTrip(actual, "salesProjectRequest", SalesProjectRequest.class,
                (SalesProjectRequest) examples.get("salesProjectRequest"));
        assertRoundTrip(actual, "changeSalesStageRequest", ChangeSalesStageRequest.class,
                (ChangeSalesStageRequest) examples.get("changeSalesStageRequest"));
        assertRoundTrip(actual, "salesProjectResponse", SalesProjectResponse.class,
                (SalesProjectResponse) examples.get("salesProjectResponse"));
        assertRoundTrip(actual, "pipelineResponse", PipelineResponse.class,
                (PipelineResponse) examples.get("pipelineResponse"));
        assertRoundTrip(actual, "projectWorkpieceRequest", ProjectWorkpieceRequest.class,
                (ProjectWorkpieceRequest) examples.get("projectWorkpieceRequest"));
        assertRoundTrip(actual, "projectWorkpieceResponse", ProjectWorkpieceResponse.class,
                (ProjectWorkpieceResponse) examples.get("projectWorkpieceResponse"));
        assertRoundTrip(actual, "estimateDraft", EstimateDraftDto.class,
                (EstimateDraftDto) examples.get("estimateDraft"));
        assertRoundTrip(actual, "emptyEstimateDraft", EstimateDraftDto.class,
                (EstimateDraftDto) examples.get("emptyEstimateDraft"));
        assertRoundTrip(actual, "activeDraftResponse", ActiveDraftResponse.class,
                (ActiveDraftResponse) examples.get("activeDraftResponse"));
        assertRoundTrip(actual, "apiErrorResponse", ApiErrorResponse.class,
                (ApiErrorResponse) examples.get("apiErrorResponse"));

        assertEquals(0, new BigDecimal("12500.00").compareTo(
                objectMapper.treeToValue(actual.get("salesProjectResponse"),
                        SalesProjectResponse.class).totalValue()));
        assertEquals(0, new BigDecimal("15.000").compareTo(
                objectMapper.treeToValue(actual.get("estimateDraft"),
                        EstimateDraftDto.class).wasteFactor()));
    }

    @Test
    void preservesOptionalNullsAndRepresentsEmptyCollectionsAsArrays() throws Exception {
        JsonNode actual = objectMapper.valueToTree(examples());

        JsonNode customer = actual.get("customerResponse");
        assertTrue(customer.has("phone"));
        assertTrue(customer.get("phone").isNull());
        assertTrue(customer.has("email"));
        assertTrue(customer.get("email").isNull());
        assertTrue(customer.has("address"));
        assertTrue(customer.get("address").isNull());

        assertTrue(actual.get("salesProjectRequest").has("environmentId"));
        assertTrue(actual.get("salesProjectRequest").get("environmentId").isNull());
        assertTrue(actual.get("projectWorkpieceResponse").has("edgeBanding"));
        assertTrue(actual.get("projectWorkpieceResponse").get("edgeBanding").isNull());
        assertTrue(actual.get("activeDraftResponse").has("draft"));
        assertTrue(actual.get("activeDraftResponse").get("draft").isNull());

        assertTrue(actual.get("environmentReplaceRequest").get("types").isArray());
        assertEquals(0, actual.get("environmentReplaceRequest").get("types").size());
        assertTrue(actual.get("pipelineResponse").get("stages").get(1)
                .get("projects").isArray());
        assertEquals(0, actual.get("pipelineResponse").get("stages").get(1)
                .get("projects").size());
        assertEquals(0, actual.get("emptyEstimateDraft").get("workpieces").size());
        assertEquals(0, actual.get("emptyEstimateDraft").get("hardwares").size());
        assertEquals("KITCHEN", actual.get("environmentAddRequest").get("roomTypes").get(0)
                .textValue());
        assertEquals("QUOTE_SENT", actual.get("changeSalesStageRequest").get("stage")
                .textValue());
    }

    @Test
    void rejectsNullRequiredCollections() {
        assertThrows(NullPointerException.class, () -> new EnvironmentAddRequest(null, null));
        assertThrows(NullPointerException.class, () -> new EnvironmentReplaceRequest(null));
        assertThrows(NullPointerException.class, () -> new PipelineStageResponse(
                SalesStage.CONTACT, null));
        assertThrows(NullPointerException.class, () -> new PipelineResponse(null));
        assertThrows(NullPointerException.class, () -> new EstimateDraftDto(null,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, null, List.of()));
        assertThrows(NullPointerException.class, () -> new ApiErrorResponse(
                "ERROR", "error.operation-failed", null, "diagnostic-1"));
    }

    @Test
    void copiesListComponentsDefensively() {
        List<RoomType> roomTypes = new ArrayList<>(List.of(RoomType.KITCHEN));
        EnvironmentAddRequest request = new EnvironmentAddRequest(roomTypes, null);

        roomTypes.clear();

        assertEquals(List.of(RoomType.KITCHEN), request.roomTypes());
        assertThrows(UnsupportedOperationException.class,
                () -> request.roomTypes().add(RoomType.BATHROOM));
    }

    private <T> void assertRoundTrip(
            JsonNode actual,
            String property,
            Class<T> type,
            T expected
    ) throws Exception {
        T decoded = objectMapper.treeToValue(actual.get(property), type);
        assertJsonEquivalent(objectMapper.valueToTree(expected), objectMapper.valueToTree(decoded));
    }

    private void assertJsonEquivalent(JsonNode expected, JsonNode actual) {
        if (expected.isNumber() && actual.isNumber()) {
            assertEquals(0, expected.decimalValue().compareTo(actual.decimalValue()));
        } else if (expected.isObject() && actual.isObject()) {
            assertEquals(expected.properties().stream().map(Map.Entry::getKey).toList(),
                    actual.properties().stream().map(Map.Entry::getKey).toList());
            expected.properties().forEach(entry ->
                    assertJsonEquivalent(entry.getValue(), actual.get(entry.getKey())));
        } else if (expected.isArray() && actual.isArray()) {
            assertEquals(expected.size(), actual.size());
            for (int index = 0; index < expected.size(); index++) {
                assertJsonEquivalent(expected.get(index), actual.get(index));
            }
        } else {
            assertEquals(expected, actual);
        }
    }

    private JsonNode readContractExample() throws IOException {
        try (InputStream input = getClass().getResourceAsStream("transport-dtos.json")) {
            assertNotNull(input, "contract example resource must be present");
            return objectMapper.readTree(input);
        }
    }

    private static Map<String, Object> examples() {
        Map<String, Object> examples = new LinkedHashMap<>();
        examples.put("customerRequest",
                new CustomerRequest("Ada Lovelace", null, null, null));
        examples.put("customerResponse",
                new CustomerResponse("customer-1", "Ada Lovelace", null, null, null));
        examples.put("environmentAddRequest",
                new EnvironmentAddRequest(List.of(RoomType.KITCHEN), null));
        examples.put("environmentReplaceRequest", new EnvironmentReplaceRequest(List.of()));
        examples.put("environmentResponse",
                new EnvironmentResponse("environment-1", "customer-1",
                        "Ada Lovelace", "KITCHEN"));

        MaterialRequest materialRequest = new MaterialRequest("MDF", "White",
                new BigDecimal("18.00"), new BigDecimal("2750.00"),
                new BigDecimal("1840.00"), new BigDecimal("120.50"));
        MaterialResponse materialResponse = new MaterialResponse("material-1", "MDF", "White",
                new BigDecimal("18.00"), new BigDecimal("2750.00"),
                new BigDecimal("1840.00"), new BigDecimal("120.50"));
        examples.put("materialRequest", materialRequest);
        examples.put("materialResponse", materialResponse);
        examples.put("edgeBandingRequest",
                new EdgeBandingRequest("PVC", new BigDecimal("22.00"),
                        new BigDecimal("1.25")));
        examples.put("edgeBandingResponse",
                new EdgeBandingResponse("edge-1", "PVC", new BigDecimal("22.00"),
                        new BigDecimal("1.25")));
        examples.put("hardwareRequest",
                new HardwareRequest("Hinge", "PAIR", new BigDecimal("5.75")));
        examples.put("hardwareResponse",
                new HardwareResponse("hardware-1", "Hinge", "PAIR",
                        new BigDecimal("5.75")));

        SalesProjectResponse project = new SalesProjectResponse("project-1", "Kitchen",
                "Ada Lovelace", null, null, "", new BigDecimal("12500.00"),
                SalesStage.CONTACT);
        examples.put("salesProjectRequest",
                new SalesProjectRequest("customer-1", null, "Kitchen",
                        new BigDecimal("12500.00")));
        examples.put("changeSalesStageRequest",
                new ChangeSalesStageRequest(SalesStage.QUOTE_SENT));
        examples.put("salesProjectResponse", project);
        examples.put("pipelineResponse", new PipelineResponse(List.of(
                new PipelineStageResponse(SalesStage.CONTACT, List.of(project)),
                new PipelineStageResponse(SalesStage.MEASUREMENT_SCHEDULED, List.of()),
                new PipelineStageResponse(SalesStage.QUOTE_SENT, List.of()),
                new PipelineStageResponse(SalesStage.NEGOTIATION, List.of()),
                new PipelineStageResponse(SalesStage.CLOSED, List.of()))));

        examples.put("projectWorkpieceRequest",
                new ProjectWorkpieceRequest("project-1", "Counter",
                        new BigDecimal("600.00"), new BigDecimal("400.00"), 2,
                        "material-1", null, true, false, false, false));
        examples.put("projectWorkpieceResponse",
                new ProjectWorkpieceResponse("workpiece-1", "project-1", "Counter",
                        new BigDecimal("600.00"), new BigDecimal("400.00"), 2,
                        materialResponse, null, true, false, false, false));

        EstimateDraftDto draft = new EstimateDraftDto(null, new BigDecimal("15.000"),
                new BigDecimal("0.00"), new BigDecimal("0.00"), new BigDecimal("0.00"),
                new BigDecimal("30.000"),
                List.of(new EstimateDraftDto.WorkpieceDto(null, "Standalone counter",
                        new BigDecimal("600.00"), new BigDecimal("400.00"), 1,
                        "material-1", null, false, false, false, false)),
                List.of(new EstimateDraftDto.HardwareDto("hardware-1", 2)));
        examples.put("estimateDraft", draft);
        examples.put("emptyEstimateDraft", new EstimateDraftDto(null,
                new BigDecimal("15.000"), new BigDecimal("0.00"), new BigDecimal("0.00"),
                new BigDecimal("0.00"), new BigDecimal("30.000"), List.of(), List.of()));
        examples.put("activeDraftResponse", new ActiveDraftResponse(null));
        examples.put("apiErrorResponse", new ApiErrorResponse("VALIDATION_ERROR",
                "error.operation-failed",
                List.of(new ApiFieldErrorResponse("fullName", "NOT_BLANK",
                        "error.customer-name")),
                "diagnostic-1"));
        return examples;
    }
}
