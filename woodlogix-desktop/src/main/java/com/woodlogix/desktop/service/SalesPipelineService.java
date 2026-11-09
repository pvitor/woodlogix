package com.woodlogix.desktop.service;

import com.woodlogix.application.customer.CustomerCatalog;
import com.woodlogix.application.customer.CustomerRow;
import com.woodlogix.application.environment.EnvironmentCatalog;
import com.woodlogix.application.environment.EnvironmentRow;
import com.woodlogix.application.sales.SalesPipeline;
import com.woodlogix.application.sales.SalesProjectInput;
import com.woodlogix.application.sales.SalesProjectRow;
import com.woodlogix.core.enums.SalesStage;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@Validated
public class SalesPipelineService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SalesPipelineService.class);

    private final SalesPipeline salesPipeline;
    private final CustomerCatalog customerCatalog;
    private final EnvironmentCatalog environmentCatalog;

    public SalesPipelineService(
            SalesPipeline salesPipeline,
            CustomerCatalog customerCatalog,
            EnvironmentCatalog environmentCatalog
    ) {
        this.salesPipeline = Objects.requireNonNull(salesPipeline, "salesPipeline must not be null");
        this.customerCatalog = Objects.requireNonNull(customerCatalog, "customerCatalog must not be null");
        this.environmentCatalog = Objects.requireNonNull(
                environmentCatalog, "environmentCatalog must not be null"
        );
    }

    public Map<SalesStage, List<SalesProjectRow>> findPipeline() {
        return salesPipeline.findPipeline();
    }

    public SalesProjectRow create(@Valid SalesProjectData data) {
        SalesProjectRow project = salesPipeline.create(new SalesProjectInput(data.customerId(),
                data.environmentId(), data.projectName(), data.totalValue()));
        LOGGER.info("Sales project added to pipeline");
        return project;
    }

    public void move(String projectId, SalesStage stage) {
        salesPipeline.move(projectId, stage);
        LOGGER.info("Sales project moved to stage {}", stage);
    }

    public SalesProjectRow findById(String projectId) {
        return salesPipeline.findById(projectId);
    }

    @Transactional(readOnly = true)
    public List<CustomerRow> findCustomers() {
        return customerCatalog.findAll();
    }

    @Transactional(readOnly = true)
    public List<EnvironmentRow> findEnvironments(String customerId) {
        if (customerId == null || customerId.isBlank()) {
            return List.of();
        }
        return environmentCatalog.findByCustomer(customerId);
    }

}
