package com.woodlogix.desktop.service;

import com.woodlogix.application.customer.CustomerRow;
import com.woodlogix.application.environment.EnvironmentRow;
import com.woodlogix.application.sales.SalesPipeline;
import com.woodlogix.application.sales.SalesProjectInput;
import com.woodlogix.application.sales.SalesProjectRow;
import com.woodlogix.core.enums.SalesStage;
import com.woodlogix.desktop.persistence.entity.CustomerEntity;
import com.woodlogix.desktop.persistence.entity.ProjectEnvironmentEntity;
import com.woodlogix.desktop.persistence.repository.CustomerRepository;
import com.woodlogix.desktop.persistence.repository.ProjectEnvironmentRepository;
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

    private final CustomerRepository customerRepository;
    private final ProjectEnvironmentRepository environmentRepository;
    private final SalesPipeline salesPipeline;

    public SalesPipelineService(
            SalesPipeline salesPipeline,
            CustomerRepository customerRepository,
            ProjectEnvironmentRepository environmentRepository
    ) {
        this.salesPipeline = Objects.requireNonNull(salesPipeline, "salesPipeline must not be null");
        this.customerRepository = Objects.requireNonNull(
                customerRepository, "customerRepository must not be null"
        );
        this.environmentRepository = Objects.requireNonNull(
                environmentRepository, "environmentRepository must not be null"
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
        return customerRepository.findAll().stream()
                .map(customer -> new CustomerRow(customer.getId(), customer.getFullName(),
                        customer.getPhone(), customer.getEmail(), customer.getAddress()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EnvironmentRow> findEnvironments(String customerId) {
        if (customerId == null || customerId.isBlank()) {
            return List.of();
        }
        return environmentRepository.findAllByCustomerId(customerId).stream()
                .map(environment -> new EnvironmentRow(environment.getId(),
                        environment.getCustomer().getId(), environment.getCustomer().getFullName(),
                        environment.getType()))
                .toList();
    }

}
