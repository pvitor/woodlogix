package com.woodlogix.desktop.service;

import com.woodlogix.application.customer.CustomerRow;
import com.woodlogix.core.enums.SalesStage;
import com.woodlogix.desktop.persistence.entity.CustomerEntity;
import com.woodlogix.desktop.persistence.entity.ProjectEnvironmentEntity;
import com.woodlogix.desktop.persistence.entity.SalesProjectEntity;
import com.woodlogix.desktop.persistence.repository.CustomerRepository;
import com.woodlogix.desktop.persistence.repository.ProjectEnvironmentRepository;
import com.woodlogix.desktop.persistence.repository.SalesProjectRepository;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@Validated
public class SalesPipelineService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SalesPipelineService.class);

    private final CustomerRepository customerRepository;
    private final ProjectEnvironmentRepository environmentRepository;
    private final SalesProjectRepository salesProjectRepository;

    public SalesPipelineService(
            CustomerRepository customerRepository,
            ProjectEnvironmentRepository environmentRepository,
            SalesProjectRepository salesProjectRepository
    ) {
        this.customerRepository = Objects.requireNonNull(
                customerRepository, "customerRepository must not be null"
        );
        this.environmentRepository = Objects.requireNonNull(
                environmentRepository, "environmentRepository must not be null"
        );
        this.salesProjectRepository = Objects.requireNonNull(
                salesProjectRepository, "salesProjectRepository must not be null"
        );
    }

    @Transactional(readOnly = true)
    public Map<SalesStage, List<SalesProjectRow>> findPipeline() {
        Map<SalesStage, List<SalesProjectRow>> pipeline = new EnumMap<>(SalesStage.class);
        for (SalesStage stage : SalesStage.values()) {
            pipeline.put(stage, salesProjectRepository.findAllByStageOrderByCreatedAtDesc(stage)
                    .stream().map(this::toRow).toList());
        }
        return pipeline;
    }

    @Transactional
    public SalesProjectRow create(@Valid SalesProjectData data) {
        CustomerEntity customer = customerRepository.findById(data.customerId())
                .orElseThrow(() -> new IllegalArgumentException("error.customer-not-found"));
        ProjectEnvironmentEntity environment = data.environmentId() == null
                || data.environmentId().isBlank()
                ? null
                : environmentRepository.findById(data.environmentId())
                        .orElseThrow(() ->
                                new IllegalArgumentException("error.environment-not-found"));
        if (environment != null
                && !environment.getCustomer().getId().equals(data.customerId())) {
            throw new IllegalArgumentException("error.environment-customer-mismatch");
        }
        SalesProjectEntity project = salesProjectRepository.saveAndFlush(
                new SalesProjectEntity(customer, environment, data.projectName(), data.totalValue())
        );
        LOGGER.info("Sales project added to pipeline");
        return toRow(project);
    }

    @Transactional
    public void move(String projectId, SalesStage stage) {
        SalesProjectEntity project = salesProjectRepository
                .findWithCustomerAndEnvironmentById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("error.project-not-found"));
        project.moveTo(stage);
        salesProjectRepository.flush();
        LOGGER.info("Sales project moved to stage {}", stage);
    }

    @Transactional(readOnly = true)
    public SalesProjectRow findById(String projectId) {
        return salesProjectRepository.findWithCustomerAndEnvironmentById(projectId)
                .map(this::toRow)
                .orElseThrow(() -> new IllegalArgumentException("error.project-not-found"));
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

    private SalesProjectRow toRow(SalesProjectEntity project) {
        return new SalesProjectRow(
                project.getId(),
                project.getProjectName(),
                project.getCustomer().getFullName(),
                project.getCustomer().getEmail(),
                project.getCustomer().getPhone(),
                project.getEnvironment() == null ? "" : project.getEnvironment().getType(),
                project.getTotalValue(),
                project.getStage()
        );
    }
}
