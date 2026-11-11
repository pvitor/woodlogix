package com.woodlogix.desktop.service;

import com.woodlogix.core.enums.SalesStage;
import com.woodlogix.desktop.api.client.SalesPipelineApiClient;
import com.woodlogix.desktop.api.dto.PipelineResponse;
import com.woodlogix.desktop.api.dto.SalesProjectRequest;
import com.woodlogix.desktop.api.dto.SalesProjectResponse;
import com.woodlogix.desktop.api.mapper.SalesPipelineMapperImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RemoteSalesPipelineServiceTest {
    private final SalesPipelineApiClient client = mock(SalesPipelineApiClient.class);
    private final RemoteSalesPipelineService service = new RemoteSalesPipelineService(
            client, new SalesPipelineMapperImpl());

    @Test
    void delegatesPipelineProjectAndStageOperationsWithRemoteIds() {
        SalesProjectResponse response = new SalesProjectResponse(
                "server-project", "Kitchen", "Customer", "email", "phone",
                "KITCHEN", BigDecimal.TEN, SalesStage.CONTACT);
        SalesProjectData data = new SalesProjectData(
                "server-customer", "server-environment", "Kitchen", BigDecimal.TEN);
        SalesProjectRequest request = new SalesProjectRequest(
                "server-customer", "server-environment", "Kitchen", BigDecimal.TEN);
        when(client.findPipeline()).thenReturn(new PipelineResponse(List.of()));
        when(client.create(request)).thenReturn(response);
        when(client.findById("server-project")).thenReturn(response);

        assertEquals(SalesStage.values().length, service.findPipeline().size());
        assertEquals("server-project", service.create(data).id());
        assertEquals("server-project", service.findById("server-project").id());
        service.move("server-project", SalesStage.NEGOTIATION);

        verify(client).findPipeline();
        verify(client).create(request);
        verify(client).findById("server-project");
        verify(client).move("server-project",
                new com.woodlogix.desktop.api.dto.ChangeSalesStageRequest(SalesStage.NEGOTIATION));
    }

    @Test
    void preservesEmptyPipelineAndClientFailures() {
        when(client.findPipeline()).thenReturn(new PipelineResponse(List.of()));
        assertEquals(SalesStage.values().length, service.findPipeline().size());

        RuntimeException failure = new RuntimeException("transport failure");
        when(client.findById("project")).thenThrow(failure);
        assertSame(failure, assertThrows(RuntimeException.class,
                () -> service.findById("project")));
    }
}
