package com.woodlogix.desktop.service;

import com.woodlogix.application.environment.EnvironmentRow;
import com.woodlogix.core.enums.RoomType;
import com.woodlogix.desktop.api.client.EnvironmentApiClient;
import com.woodlogix.desktop.api.dto.EnvironmentAddRequest;
import com.woodlogix.desktop.api.dto.EnvironmentReplaceRequest;
import com.woodlogix.desktop.api.dto.EnvironmentResponse;
import com.woodlogix.desktop.api.mapper.EnvironmentMapperImpl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RemoteEnvironmentServiceTest {
    private final EnvironmentApiClient client = mock(EnvironmentApiClient.class);
    private final RemoteEnvironmentService service = new RemoteEnvironmentService(
            client, new EnvironmentMapperImpl());

    @Test
    void delegatesAllEnvironmentOperationsAndPreservesRemoteIds() {
        EnvironmentResponse response = new EnvironmentResponse(
                "server-environment", "server-customer", "Customer", "Kitchen");
        EnvironmentAddRequest addRequest = new EnvironmentAddRequest(
                List.of(RoomType.KITCHEN), "Pantry");
        EnvironmentReplaceRequest replaceRequest = new EnvironmentReplaceRequest(
                List.of("Kitchen", "Pantry"));
        when(client.findAll()).thenReturn(List.of(response));
        when(client.findByCustomer("server-customer")).thenReturn(List.of(response));
        when(client.createForCustomer("server-customer", addRequest))
                .thenReturn(List.of(response));
        when(client.replaceCustomerEnvironments("server-customer", replaceRequest))
                .thenReturn(List.of(response));

        assertEquals("server-environment", service.findAll().get(0).id());
        assertEquals("server-environment",
                service.findByCustomer("server-customer").get(0).id());
        assertEquals("server-environment",
                service.createForCustomer("server-customer",
                        List.of(RoomType.KITCHEN), "Pantry").get(0).id());
        assertEquals("server-environment",
                service.replaceCustomerEnvironments("server-customer",
                        List.of("Kitchen", "Pantry")).get(0).id());
        service.delete("server-environment");
        service.deleteAllByCustomer("server-customer");

        verify(client).findAll();
        verify(client).findByCustomer("server-customer");
        verify(client).createForCustomer("server-customer", addRequest);
        verify(client).replaceCustomerEnvironments("server-customer", replaceRequest);
        verify(client).delete("server-environment");
        verify(client).deleteAllByCustomer("server-customer");
    }

    @Test
    void preservesEmptyResponsesAndClientFailures() {
        when(client.findAll()).thenReturn(List.of());
        assertEquals(List.of(), service.findAll());

        RuntimeException failure = new RuntimeException("http failure");
        org.mockito.Mockito.doThrow(failure).when(client).delete("environment");
        assertSame(failure, assertThrows(RuntimeException.class,
                () -> service.delete("environment")));
    }
}
