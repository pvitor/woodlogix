package com.woodlogix.desktop.api.mapper;

import com.woodlogix.application.customer.CustomerRow;
import com.woodlogix.application.edgebanding.EdgeBandingRow;
import com.woodlogix.application.environment.EnvironmentRow;
import com.woodlogix.application.hardware.HardwareRow;
import com.woodlogix.application.material.MaterialRow;
import com.woodlogix.application.sales.SalesProjectRow;
import com.woodlogix.application.workpiece.ProjectWorkpieceRow;
import com.woodlogix.core.enums.RoomType;
import com.woodlogix.core.enums.SalesStage;
import com.woodlogix.desktop.api.dto.ActiveDraftResponse;
import com.woodlogix.desktop.api.dto.CustomerRequest;
import com.woodlogix.desktop.api.dto.CustomerResponse;
import com.woodlogix.desktop.api.dto.ChangeSalesStageRequest;
import com.woodlogix.desktop.api.dto.EdgeBandingRequest;
import com.woodlogix.desktop.api.dto.EdgeBandingResponse;
import com.woodlogix.desktop.api.dto.EnvironmentAddRequest;
import com.woodlogix.desktop.api.dto.EnvironmentReplaceRequest;
import com.woodlogix.desktop.api.dto.EnvironmentResponse;
import com.woodlogix.desktop.api.dto.EstimateDraftDto;
import com.woodlogix.desktop.api.dto.HardwareRequest;
import com.woodlogix.desktop.api.dto.HardwareResponse;
import com.woodlogix.desktop.api.dto.MaterialRequest;
import com.woodlogix.desktop.api.dto.MaterialResponse;
import com.woodlogix.desktop.api.dto.PipelineResponse;
import com.woodlogix.desktop.api.dto.PipelineStageResponse;
import com.woodlogix.desktop.api.dto.ProjectWorkpieceRequest;
import com.woodlogix.desktop.api.dto.ProjectWorkpieceResponse;
import com.woodlogix.desktop.api.dto.SalesProjectRequest;
import com.woodlogix.desktop.api.dto.SalesProjectResponse;
import com.woodlogix.desktop.service.CustomerData;
import com.woodlogix.desktop.service.EdgeBandingData;
import com.woodlogix.desktop.service.EstimateDraftData;
import com.woodlogix.desktop.service.HardwareData;
import com.woodlogix.desktop.service.MaterialData;
import com.woodlogix.desktop.service.ProjectWorkpieceData;
import com.woodlogix.desktop.service.SalesProjectData;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiMapperTest {
    @Test
    void mapsCustomerMaterialEdgeBandingAndHardwareTypes() {
        CustomerMapper customerMapper = new CustomerMapperImpl();
        CustomerData customerData = new CustomerData(
                "Ada Lovelace", null, "ada@example.test", null);
        assertEquals(new CustomerRequest("Ada Lovelace", null, "ada@example.test", null),
                customerMapper.toRequest(customerData));
        assertEquals(new CustomerRow("customer-1", "Ada Lovelace", null,
                        "ada@example.test", null),
                customerMapper.toRow(new CustomerResponse(
                        "customer-1", "Ada Lovelace", null, "ada@example.test", null)));
        assertNull(customerMapper.toRow(null));

        BigDecimal precise = new BigDecimal("123.4500000000000000000000001");
        MaterialMapper materialMapper = new MaterialMapperImpl();
        MaterialRequest materialRequest = materialMapper.toRequest(new MaterialData(
                "MDF", "White", precise, new BigDecimal("2750.00"),
                new BigDecimal("1840.00"), new BigDecimal("120.50")));
        assertSame(precise, materialRequest.thicknessMm());
        MaterialRow materialRow = materialMapper.toRow(new MaterialResponse(
                "material-1", "MDF", "White", precise, new BigDecimal("2750.00"),
                new BigDecimal("1840.00"), new BigDecimal("120.50")));
        assertSame(precise, materialRow.thicknessMm());
        assertTrue(materialMapper.toRows(List.of()).isEmpty());

        EdgeBandingMapper edgeBandingMapper = new EdgeBandingMapperImpl();
        assertSame(precise, edgeBandingMapper.toRequest(
                new EdgeBandingData("PVC", new BigDecimal("22.00"), precise)).pricePerMeter());
        assertEquals(new EdgeBandingRow("edge-1", "PVC", new BigDecimal("22.00"), precise),
                edgeBandingMapper.toRow(
                        new EdgeBandingResponse("edge-1", "PVC", new BigDecimal("22.00"), precise)));

        HardwareMapper hardwareMapper = new HardwareMapperImpl();
        assertSame(precise, hardwareMapper.toRequest(
                new HardwareData("Hinge", "PAIR", precise)).unitPrice());
        assertEquals(new HardwareRow("hardware-1", "Hinge", "PAIR", precise),
                hardwareMapper.toRow(
                        new HardwareResponse("hardware-1", "Hinge", "PAIR", precise)));
    }

    @Test
    void mapsEnvironmentResponsesAndExistingBatchRequestInputs() {
        EnvironmentMapper mapper = new EnvironmentMapperImpl();
        EnvironmentResponse response = new EnvironmentResponse(
                "environment-1", "customer-1", "Ada Lovelace", "KITCHEN");

        assertEquals(new EnvironmentRow(
                "environment-1", "customer-1", "Ada Lovelace", "KITCHEN"),
                mapper.toRow(response));
        assertEquals(List.of(RoomType.KITCHEN), mapper.toAddRequest(
                List.of(RoomType.KITCHEN), null).roomTypes());
        assertNull(mapper.toAddRequest(List.of(RoomType.KITCHEN), null).customRoomType());
        assertEquals(List.of(), mapper.toAddRequest(null, "CUSTOM").roomTypes());
        assertEquals("CUSTOM", mapper.toAddRequest(null, "CUSTOM").customRoomType());
        assertEquals(List.of(), mapper.toReplaceRequest(List.of()).types());
        assertEquals(List.of("KITCHEN", "CUSTOM"),
                mapper.toReplaceRequest(List.of("KITCHEN", "CUSTOM")).types());
        assertTrue(mapper.toRows(List.of()).isEmpty());
    }

    @Test
    void mapsPipelineInSalesStageOrderIncludingEmptyStages() {
        SalesPipelineMapper mapper = new SalesPipelineMapperImpl();
        BigDecimal amount = new BigDecimal("2500.0000000000000001");
        assertEquals(new ChangeSalesStageRequest(SalesStage.QUOTE_SENT),
                mapper.toChangeStageRequest(SalesStage.QUOTE_SENT));
        assertNull(mapper.toChangeStageRequest(null));
        SalesProjectResponse project = new SalesProjectResponse(
                "project-1", "Kitchen", "Ada Lovelace", null, null, "",
                amount, SalesStage.CONTACT);
        PipelineResponse response = new PipelineResponse(List.of(
                new PipelineStageResponse(SalesStage.CLOSED, List.of()),
                new PipelineStageResponse(SalesStage.CONTACT, List.of(project))));

        Map<SalesStage, List<SalesProjectRow>> pipeline = mapper.toPipeline(response);
        assertEquals(List.of(SalesStage.values()), pipeline.keySet().stream().toList());
        assertEquals("", pipeline.get(SalesStage.CONTACT).get(0).environmentName());
        assertSame(amount, pipeline.get(SalesStage.CONTACT).get(0).totalValue());
        assertTrue(pipeline.get(SalesStage.MEASUREMENT_SCHEDULED).isEmpty());
        assertTrue(pipeline.get(SalesStage.QUOTE_SENT).isEmpty());
        assertTrue(pipeline.get(SalesStage.NEGOTIATION).isEmpty());
        assertTrue(pipeline.get(SalesStage.CLOSED).isEmpty());

        assertEquals(new SalesProjectRequest("customer-1", null, "Kitchen", amount),
                mapper.toRequest(new SalesProjectData(
                        "customer-1", null, "Kitchen", amount)));
        assertNull(mapper.toPipeline(null));
    }

    @Test
    void mapsProjectWorkpiecesAndOptionalNestedEdgeBanding() {
        ProjectWorkpieceMapper mapper = new ProjectWorkpieceMapperImpl();
        BigDecimal length = new BigDecimal("600.0000000000000001");
        ProjectWorkpieceData data = new ProjectWorkpieceData(
                "project-1", "Counter", length, new BigDecimal("400.00"), 2,
                "material-1", null, true, false, false, false);
        ProjectWorkpieceRequest request = mapper.toRequest(data);
        assertSame(length, request.lengthMm());
        assertNull(request.edgeBandingId());

        MaterialResponse material = new MaterialResponse("material-1", "MDF", "White",
                new BigDecimal("18.00"), new BigDecimal("2750.00"),
                new BigDecimal("1840.00"), new BigDecimal("120.50"));
        ProjectWorkpieceRow row = mapper.toRow(new ProjectWorkpieceResponse(
                "piece-1", "project-1", "Counter", length, new BigDecimal("400.00"),
                2, material, null, true, false, false, false));
        assertEquals("MDF", row.material().name());
        assertNull(row.edgeBanding());
        assertSame(length, row.lengthMm());
        assertTrue(mapper.toRows(List.of()).isEmpty());
    }

    @Test
    void mapsDraftItemsAndActiveDraftAbsence() {
        EstimateDraftMapper mapper = new EstimateDraftMapperImpl();
        BigDecimal factor = new BigDecimal("15.0000000000000001");
        EstimateDraftData data = new EstimateDraftData(null, factor,
                new BigDecimal("1.50"), new BigDecimal("20.00"), new BigDecimal("3.00"),
                new BigDecimal("30.00"),
                List.of(new EstimateDraftData.EstimateDraftWorkpieceData(
                        null, "Counter", new BigDecimal("600.00"), new BigDecimal("400.00"),
                        1, "material-1", null, true, false, false, false)),
                List.of(new EstimateDraftData.EstimateDraftHardwareData("hardware-1", 2)));
        EstimateDraftDto dto = mapper.toRequest(data);
        assertSame(factor, dto.wasteFactor());
        assertEquals("Counter", dto.workpieces().get(0).name());
        assertEquals(new EstimateDraftDto.HardwareDto("hardware-1", 2),
                dto.hardwares().get(0));
        assertEquals(data, mapper.toData(dto));

        assertEquals(Optional.empty(),
                mapper.toActiveDraftData(new ActiveDraftResponse(null)));
        assertEquals(Optional.of(data),
                mapper.toActiveDraftData(new ActiveDraftResponse(dto)));
        assertNull(mapper.toActiveDraftData(null));
        assertTrue(mapper.toRequest(new EstimateDraftData(null, factor,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(), List.of())).workpieces().isEmpty());
    }
}
