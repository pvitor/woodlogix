package com.woodlogix.desktop.service;

import com.woodlogix.application.customer.CustomerRow;
import com.woodlogix.desktop.api.client.CustomerApiClient;
import com.woodlogix.desktop.api.dto.CustomerRequest;
import com.woodlogix.desktop.api.dto.CustomerResponse;
import com.woodlogix.desktop.api.mapper.CustomerMapperImpl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerServiceHttpTest {
    private final CustomerApiClient client = mock(CustomerApiClient.class);
    private final CustomerService service = new CustomerService(client, new CustomerMapperImpl());

    @Test
    void delegatesAllCustomerOperationsAndMapsRows() {
        CustomerResponse response = new CustomerResponse(
                "server-id", "Ada Lovelace", "11999999999", "ada@example.test", "Street");
        CustomerRequest request = new CustomerRequest(
                "Ada Lovelace", "11999999999", "ada@example.test", "Street");
        CustomerData data = new CustomerData(
                "Ada Lovelace", "11999999999", "ada@example.test", "Street");
        when(client.findAll()).thenReturn(List.of(response));
        when(client.search("Ada")).thenReturn(List.of(response));
        when(client.findByName("Ada Lovelace")).thenReturn(List.of(response));
        when(client.create(request)).thenReturn(response);
        when(client.update("server-id", request)).thenReturn(response);

        assertEquals(List.of(new CustomerRow(
                "server-id", "Ada Lovelace", "11999999999", "ada@example.test", "Street")),
                service.findAll());
        assertEquals(1, service.search("Ada").size());
        assertEquals(1, service.findByName("Ada Lovelace").size());
        assertEquals("server-id", service.create(data).id());
        assertEquals("server-id", service.update("server-id", data).id());
        service.delete("server-id");

        verify(client).findAll();
        verify(client).search("Ada");
        verify(client).findByName("Ada Lovelace");
        verify(client).create(request);
        verify(client).update("server-id", request);
        verify(client).delete("server-id");
    }

    @Test
    void preservesClientFailures() {
        RuntimeException failure = new RuntimeException("client failure");
        when(client.findAll()).thenThrow(failure);
        assertSame(failure, assertThrows(RuntimeException.class, service::findAll));
    }
}
