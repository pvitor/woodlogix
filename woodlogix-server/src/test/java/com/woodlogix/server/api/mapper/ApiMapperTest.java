package com.woodlogix.server.api.mapper;

import com.woodlogix.application.customer.CustomerInput;
import com.woodlogix.application.customer.CustomerRow;
import com.woodlogix.application.draft.EstimateDraftInput;
import com.woodlogix.application.draft.EstimateDraftRow;
import com.woodlogix.application.edgebanding.EdgeBandingInput;
import com.woodlogix.application.edgebanding.EdgeBandingRow;
import com.woodlogix.application.environment.EnvironmentRow;
import com.woodlogix.application.environment.EnvironmentSyncResult;
import com.woodlogix.application.hardware.HardwareInput;
import com.woodlogix.application.hardware.HardwareRow;
import com.woodlogix.application.material.MaterialInput;
import com.woodlogix.application.material.MaterialRow;
import com.woodlogix.application.sales.SalesProjectInput;
import com.woodlogix.application.sales.SalesProjectRow;
import com.woodlogix.application.workpiece.ProjectWorkpieceInput;
import com.woodlogix.application.workpiece.ProjectWorkpieceRow;
import com.woodlogix.core.enums.RoomType;
import com.woodlogix.core.enums.SalesStage;
import com.woodlogix.server.api.dto.ActiveDraftResponse;
import com.woodlogix.server.api.dto.CustomerRequest;
import com.woodlogix.server.api.dto.CustomerResponse;
import com.woodlogix.server.api.dto.ChangeSalesStageRequest;
import com.woodlogix.server.api.dto.EdgeBandingRequest;
import com.woodlogix.server.api.dto.EdgeBandingResponse;
import com.woodlogix.server.api.dto.EnvironmentAddRequest;
import com.woodlogix.server.api.dto.EnvironmentReplaceRequest;
import com.woodlogix.server.api.dto.EnvironmentResponse;
import com.woodlogix.server.api.dto.EstimateDraftDto;
import com.woodlogix.server.api.dto.HardwareRequest;
import com.woodlogix.server.api.dto.HardwareResponse;
import com.woodlogix.server.api.dto.MaterialRequest;
import com.woodlogix.server.api.dto.MaterialResponse;
import com.woodlogix.server.api.dto.PipelineResponse;
import com.woodlogix.server.api.dto.ProjectWorkpieceRequest;
import com.woodlogix.server.api.dto.ProjectWorkpieceResponse;
import com.woodlogix.server.api.dto.SalesProjectRequest;
import com.woodlogix.server.api.dto.SalesProjectResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiMapperTest {
    @Test
    void mapsCustomerMaterialEdgeBandingAndHardwareWithoutChangingValues() {
        CustomerMapper customerMapper = new CustomerMapperImpl();
        CustomerRequest customerRequest = new CustomerRequest(
                "Ada Lovelace", null, "ada@example.test", null);
        assertEquals(new CustomerInput("Ada Lovelace", null, "ada@example.test", null),
                customerMapper.toInput(customerRequest));
        assertEquals(new CustomerResponse("customer-1", "Ada Lovelace", null,
                        "ada@example.test", null),
                customerMapper.toResponse(new CustomerRow(
                        "customer-1", "Ada Lovelace", null, "ada@example.test", null)));
        assertTrue(customerMapper.toResponses(List.of()).isEmpty());
        assertNull(customerMapper.toInput(null));

        BigDecimal precise = new BigDecimal("123.4500000000000000000000001");
        MaterialMapper materialMapper = new MaterialMapperImpl();
        assertEquals(new MaterialInput("MDF", "White", precise,
                        new BigDecimal("2750.00"), new BigDecimal("1840.00"),
                        new BigDecimal("120.50")),
                materialMapper.toInput(new MaterialRequest("MDF", "White", precise,
                        new BigDecimal("2750.00"), new BigDecimal("1840.00"),
                        new BigDecimal("120.50"))));
        MaterialResponse materialResponse = materialMapper.toResponse(new MaterialRow(
                "material-1", "MDF", "White", precise,
                new BigDecimal("2750.00"), new BigDecimal("1840.00"),
                new BigDecimal("120.50")));
        assertSame(precise, materialResponse.thicknessMm());

        EdgeBandingMapper edgeBandingMapper = new EdgeBandingMapperImpl();
        assertEquals(new EdgeBandingInput("PVC", new BigDecimal("22.00"), precise),
                edgeBandingMapper.toInput(
                        new EdgeBandingRequest("PVC", new BigDecimal("22.00"), precise)));
        assertSame(precise, edgeBandingMapper.toResponse(new EdgeBandingRow(
                "edge-1", "PVC", new BigDecimal("22.00"), precise)).pricePerMeter());

        HardwareMapper hardwareMapper = new HardwareMapperImpl();
        assertEquals(new HardwareInput("Hinge", "PAIR", precise),
                hardwareMapper.toInput(new HardwareRequest("Hinge", "PAIR", precise)));
        assertSame(precise, hardwareMapper.toResponse(
                new HardwareRow("hardware-1", "Hinge", "PAIR", precise)).unitPrice());
    }

    @Test
    void mapsEnvironmentResponsesAndSynchronizationInputsInOrder() {
        EnvironmentMapper mapper = new EnvironmentMapperImpl();
        EnvironmentRow row = new EnvironmentRow(
                "environment-1", "customer-1", "Ada Lovelace", "KITCHEN");

        assertEquals(new EnvironmentResponse(
                "environment-1", "customer-1", "Ada Lovelace", "KITCHEN"),
                mapper.toResponse(row));
        assertEquals(List.of(new EnvironmentResponse(
                        "environment-1", "customer-1", "Ada Lovelace", "KITCHEN")),
                mapper.toResponses(new EnvironmentSyncResult(List.of(row), true, 1)));
        assertEquals(List.of(RoomType.KITCHEN),
                mapper.roomTypes(new EnvironmentAddRequest(List.of(RoomType.KITCHEN), null)));
        assertNull(mapper.customRoomType(
                new EnvironmentAddRequest(List.of(RoomType.KITCHEN), null)));
        assertEquals(List.of("KITCHEN", "CUSTOM"),
                mapper.replacementTypes(new EnvironmentReplaceRequest(
                        List.of("KITCHEN", "CUSTOM"))));
        assertTrue(mapper.toResponses(new EnvironmentSyncResult(List.of(), false, 0)).isEmpty());
        assertNull(mapper.toResponses((EnvironmentSyncResult) null));
    }

    @Test
    void mapsSalesProjectsAndEveryPipelineStageInEnumOrder() {
        SalesPipelineMapper mapper = new SalesPipelineMapperImpl();
        BigDecimal amount = new BigDecimal("2500.0000000000000001");
        assertEquals(SalesStage.QUOTE_SENT,
                mapper.toStage(new ChangeSalesStageRequest(SalesStage.QUOTE_SENT)));
        assertNull(mapper.toStage(null));
        SalesProjectRow project = new SalesProjectRow(
                "project-1", "Kitchen", "Ada Lovelace", null, null, "",
                amount, SalesStage.CONTACT);
        assertEquals(new SalesProjectInput("customer-1", null, "Kitchen", amount),
                mapper.toInput(new SalesProjectRequest("customer-1", null, "Kitchen", amount)));
        SalesProjectResponse response = mapper.toResponse(project);
        assertEquals("", response.environmentName());
        assertSame(amount, response.totalValue());

        Map<SalesStage, List<SalesProjectRow>> source = new EnumMap<>(SalesStage.class);
        source.put(SalesStage.CLOSED, List.of());
        source.put(SalesStage.CONTACT, List.of(project));
        PipelineResponse pipeline = mapper.toResponse(source);

        assertEquals(List.of(SalesStage.values()), pipeline.stages().stream()
                .map(stage -> stage.stage()).collect(Collectors.toList()));
        assertEquals(List.of(response), pipeline.stages().get(0).projects());
        assertTrue(pipeline.stages().get(1).projects().isEmpty());
        assertTrue(mapper.toResponse(Map.of()).stages().stream()
                .allMatch(stage -> stage.projects().isEmpty()));
        assertNull(mapper.toResponse((Map<SalesStage, List<SalesProjectRow>>) null));
    }

    @Test
    void mapsProjectWorkpiecesAndOptionalNestedEdgeBanding() {
        ProjectWorkpieceMapper mapper = new ProjectWorkpieceMapperImpl();
        BigDecimal length = new BigDecimal("600.0000000000000001");
        ProjectWorkpieceRequest request = new ProjectWorkpieceRequest(
                "project-1", "Counter", length, new BigDecimal("400.00"), 2,
                "material-1", null, true, false, false, false);
        assertEquals(new ProjectWorkpieceInput(
                "project-1", "Counter", length, new BigDecimal("400.00"), 2,
                "material-1", null, true, false, false, false),
                mapper.toInput(request));

        MaterialRow material = new MaterialRow("material-1", "MDF", "White",
                new BigDecimal("18.00"), new BigDecimal("2750.00"),
                new BigDecimal("1840.00"), new BigDecimal("120.50"));
        ProjectWorkpieceRow row = new ProjectWorkpieceRow(
                "piece-1", "project-1", "Counter", length, new BigDecimal("400.00"),
                2, material, null, true, false, false, false);
        ProjectWorkpieceResponse response = mapper.toResponse(row);
        assertEquals("MDF", response.material().name());
        assertNull(response.edgeBanding());
        assertSame(length, response.lengthMm());
        assertTrue(mapper.toResponses(List.of()).isEmpty());
    }

    @Test
    void mapsDraftItemsAndOptionalActiveDraft() {
        EstimateDraftMapper mapper = new EstimateDraftMapperImpl();
        BigDecimal factor = new BigDecimal("15.0000000000000001");
        EstimateDraftDto dto = new EstimateDraftDto(null, factor,
                new BigDecimal("1.50"), new BigDecimal("20.00"), new BigDecimal("3.00"),
                new BigDecimal("30.00"),
                List.of(new EstimateDraftDto.WorkpieceDto(null, "Counter",
                        new BigDecimal("600.00"), new BigDecimal("400.00"), 1,
                        "material-1", null, true, false, false, false)),
                List.of(new EstimateDraftDto.HardwareDto("hardware-1", 2)));
        EstimateDraftInput input = mapper.toInput(dto);
        assertSame(factor, input.wasteFactor());
        assertEquals("Counter", input.workpieces().get(0).name());
        assertEquals(new EstimateDraftInput.HardwareInput("hardware-1", 2),
                input.hardwares().get(0));
        assertEquals(dto, mapper.toDto(new EstimateDraftRow(
                dto.projectId(), dto.wasteFactor(), dto.estimatedHours(), dto.hourlyRate(),
                dto.machineRate(), dto.profitMargin(),
                List.of(new EstimateDraftRow.WorkpieceRow(null, "Counter",
                        new BigDecimal("600.00"), new BigDecimal("400.00"), 1,
                        "material-1", null, true, false, false, false)),
                List.of(new EstimateDraftRow.HardwareRow("hardware-1", 2)))));

        ActiveDraftResponse absent = mapper.toActiveDraftResponse(Optional.empty());
        assertNull(absent.draft());
        assertEquals(dto, mapper.toActiveDraftResponse(Optional.of(
                new EstimateDraftRow(dto.projectId(), dto.wasteFactor(), dto.estimatedHours(),
                        dto.hourlyRate(), dto.machineRate(), dto.profitMargin(),
                        List.of(new EstimateDraftRow.WorkpieceRow(null, "Counter",
                                new BigDecimal("600.00"), new BigDecimal("400.00"), 1,
                                "material-1", null, true, false, false, false)),
                        List.of(new EstimateDraftRow.HardwareRow("hardware-1", 2)))))
                .draft());
        assertNull(mapper.toActiveDraftResponse(null));
        assertTrue(mapper.toInput(new EstimateDraftDto(null, factor, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of(), List.of()))
                .workpieces().isEmpty());
    }
}
