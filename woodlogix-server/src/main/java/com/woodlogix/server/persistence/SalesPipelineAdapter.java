package com.woodlogix.server.persistence;

import com.woodlogix.application.sales.SalesPipelinePort;
import com.woodlogix.application.sales.SalesProjectInput;
import com.woodlogix.application.sales.SalesProjectRow;
import com.woodlogix.core.enums.SalesStage;
import com.woodlogix.server.persistence.entity.CustomerEntity;
import com.woodlogix.server.persistence.entity.ProjectEnvironmentEntity;
import com.woodlogix.server.persistence.entity.SalesProjectEntity;
import com.woodlogix.server.persistence.repository.CustomerRepository;
import com.woodlogix.server.persistence.repository.ProjectEnvironmentRepository;
import com.woodlogix.server.persistence.repository.SalesProjectRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class SalesPipelineAdapter implements SalesPipelinePort {
    private final CustomerRepository customers;
    private final ProjectEnvironmentRepository environments;
    private final SalesProjectRepository projects;

    public SalesPipelineAdapter(
            CustomerRepository customers,
            ProjectEnvironmentRepository environments,
            SalesProjectRepository projects
    ) {
        this.customers = customers;
        this.environments = environments;
        this.projects = projects;
    }

    @Override
    public boolean customerExists(String customerId) {
        return customers.existsById(customerId);
    }

    @Override
    public Optional<String> findEnvironmentCustomerId(String environmentId) {
        return environments.findById(environmentId)
                .map(environment -> environment.getCustomer().getId());
    }

    @Override
    public List<SalesProjectRow> findAllByStageOrderByCreatedAtDesc(SalesStage stage) {
        return projects.findAllByStageOrderByCreatedAtDesc(stage).stream()
                .map(SalesPipelineAdapter::toRow)
                .toList();
    }

    @Override
    public SalesProjectRow createAndFlush(SalesProjectInput input) {
        CustomerEntity customer = customers.findById(input.customerId())
                .orElseThrow(() -> new IllegalArgumentException("error.customer-not-found"));
        ProjectEnvironmentEntity environment = input.environmentId() == null
                || input.environmentId().isBlank()
                ? null
                : environments.findById(input.environmentId())
                        .orElseThrow(() ->
                                new IllegalArgumentException("error.environment-not-found"));
        return toRow(projects.saveAndFlush(new SalesProjectEntity(customer, environment,
                input.projectName(), input.totalValue())));
    }

    @Override
    public boolean moveAndFlush(String projectId, SalesStage stage) {
        Optional<SalesProjectEntity> project =
                projects.findWithCustomerAndEnvironmentById(projectId);
        if (project.isEmpty()) {
            return false;
        }
        project.orElseThrow().moveTo(stage);
        projects.flush();
        return true;
    }

    @Override
    public Optional<SalesProjectRow> findById(String projectId) {
        return projects.findWithCustomerAndEnvironmentById(projectId).map(SalesPipelineAdapter::toRow);
    }

    private static SalesProjectRow toRow(SalesProjectEntity project) {
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
