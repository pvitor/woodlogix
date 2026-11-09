package com.woodlogix.desktop.service;

import com.woodlogix.application.customer.CustomerCatalog;
import com.woodlogix.application.customer.CustomerRow;
import com.woodlogix.application.environment.EnvironmentCatalog;
import com.woodlogix.application.environment.EnvironmentRow;
import com.woodlogix.application.sales.SalesPipeline;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EnvironmentAndSalesPipelineServiceTest {
    @Test
    void environmentFacadeDelegatesCustomerQueriesToCustomerCatalog() {
        CustomerCatalog customers = mock(CustomerCatalog.class);
        EnvironmentService service = new EnvironmentService(mock(EnvironmentCatalog.class), customers);
        List<CustomerRow> allCustomers = List.of(
                new CustomerRow("1", "Ana", null, null, null),
                new CustomerRow("2", "Bruno", null, null, null)
        );
        List<CustomerRow> matchingCustomers = List.of(allCustomers.get(0));
        when(customers.findAll()).thenReturn(allCustomers);
        when(customers.findByName("  ana ")).thenReturn(matchingCustomers);

        assertSame(allCustomers, service.findCustomers());
        assertSame(matchingCustomers, service.findCustomers("  ana "));

        verify(customers).findAll();
        verify(customers).findByName("  ana ");
    }

    @Test
    void salesPipelineFacadeDelegatesCustomerQueriesToCustomerCatalog() {
        CustomerCatalog customers = mock(CustomerCatalog.class);
        SalesPipelineService service = new SalesPipelineService(
                mock(SalesPipeline.class), customers, mock(EnvironmentCatalog.class));
        List<CustomerRow> rows = List.of(new CustomerRow("1", "Ana", null, null, null));
        when(customers.findAll()).thenReturn(rows);

        assertSame(rows, service.findCustomers());

        verify(customers).findAll();
    }

    @Test
    void salesPipelineFacadePreservesEmptyEnvironmentLookupForBlankCustomerId() {
        EnvironmentCatalog environments = mock(EnvironmentCatalog.class);
        SalesPipelineService service = new SalesPipelineService(
                mock(SalesPipeline.class), mock(CustomerCatalog.class), environments);

        assertEquals(List.of(), service.findEnvironments(null));
        assertEquals(List.of(), service.findEnvironments("  "));

        verify(environments, never()).findByCustomer(any());
    }

    @Test
    void salesPipelineFacadeDelegatesEnvironmentLookupWithoutChangingItsRows() {
        EnvironmentCatalog environments = mock(EnvironmentCatalog.class);
        SalesPipelineService service = new SalesPipelineService(
                mock(SalesPipeline.class), mock(CustomerCatalog.class), environments);
        List<EnvironmentRow> rows = List.of(
                new EnvironmentRow("1", "customer-1", "Ana", "Kitchen"),
                new EnvironmentRow("2", "customer-1", "Ana", "Bedroom")
        );
        when(environments.findByCustomer("customer-1")).thenReturn(rows);

        assertSame(rows, service.findEnvironments("customer-1"));

        verify(environments).findByCustomer("customer-1");
    }
}
