package com.woodlogix.desktop.persistence;

import com.woodlogix.application.customer.CustomerNotFoundException;
import com.woodlogix.application.environment.EnvironmentCatalogPort;
import com.woodlogix.application.environment.EnvironmentNotFoundException;
import com.woodlogix.application.environment.EnvironmentRow;
import com.woodlogix.desktop.persistence.entity.CustomerEntity;
import com.woodlogix.desktop.persistence.entity.ProjectEnvironmentEntity;
import com.woodlogix.desktop.persistence.repository.CustomerRepository;
import com.woodlogix.desktop.persistence.repository.FurnitureItemRepository;
import com.woodlogix.desktop.persistence.repository.FurniturePartRepository;
import com.woodlogix.desktop.persistence.repository.ProjectEnvironmentRepository;
import com.woodlogix.desktop.persistence.repository.SalesProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class EnvironmentCatalogAdapter implements EnvironmentCatalogPort {
    private static final Logger LOGGER = LoggerFactory.getLogger(EnvironmentCatalogAdapter.class);
    private static final String HAS_RELATED_RECORDS = "error.environment-has-related-records";

    private final CustomerRepository customers;
    private final ProjectEnvironmentRepository environments;
    private final FurnitureItemRepository furnitureItems;
    private final FurniturePartRepository furnitureParts;
    private final SalesProjectRepository salesProjects;

    public EnvironmentCatalogAdapter(
            CustomerRepository customers,
            ProjectEnvironmentRepository environments,
            FurnitureItemRepository furnitureItems,
            FurniturePartRepository furnitureParts,
            SalesProjectRepository salesProjects
    ) {
        this.customers = customers;
        this.environments = environments;
        this.furnitureItems = furnitureItems;
        this.furnitureParts = furnitureParts;
        this.salesProjects = salesProjects;
    }

    @Override
    public boolean customerExists(String customerId) {
        return customers.findById(customerId).isPresent();
    }

    @Override
    public boolean exists(String environmentId) {
        return environments.existsById(environmentId);
    }

    @Override
    public List<EnvironmentRow> findAllOrderedByType() {
        return toRows(environments.findAllByOrderByTypeAsc());
    }

    @Override
    public List<EnvironmentRow> findByCustomer(String customerId) {
        return toRows(environments.findAllByCustomerId(customerId));
    }

    @Override
    public EnvironmentRow createAndFlush(String customerId, String type) {
        return toRow(environments.saveAndFlush(new ProjectEnvironmentEntity(customer(customerId), type)));
    }

    @Override
    public List<EnvironmentRow> createAllAndFlush(String customerId, List<String> types) {
        CustomerEntity customer = customer(customerId);
        List<ProjectEnvironmentEntity> newEnvironments = types.stream()
                .map(type -> new ProjectEnvironmentEntity(customer, type))
                .toList();
        return toRows(environments.saveAllAndFlush(newEnvironments));
    }

    @Override
    public Optional<EnvironmentRow> updateAndFlush(String environmentId, String customerId, String type) {
        return environments.findById(environmentId).map(environment -> {
            environment.update(customer(customerId), type);
            return toRow(environments.saveAndFlush(environment));
        });
    }

    @Override
    public EnvironmentRow renameAndSave(String environmentId, String type) {
        ProjectEnvironmentEntity environment = environments.findById(environmentId)
                .orElseThrow(EnvironmentNotFoundException::new);
        environment.update(environment.getCustomer(), type);
        return toRow(environments.saveAndFlush(environment));
    }

    @Override
    public boolean hasPartReferences(String environmentId) {
        return furnitureParts.existsByProjectEnvironmentId(environmentId);
    }

    @Override
    public boolean hasFurnitureItemReferences(String environmentId) {
        return furnitureItems.existsByEnvironmentId(environmentId);
    }

    @Override
    public boolean hasSalesProjectReferences(String environmentId) {
        return salesProjects.existsByEnvironmentId(environmentId);
    }

    @Override
    public void deleteAndFlush(String environmentId) {
        environments.deleteById(environmentId);
        environments.flush();
    }

    @Override
    public void deleteAllAndFlush(List<String> environmentIds) {
        try {
            environments.deleteAll(environments.findAllById(environmentIds));
            environments.flush();
        } catch (DataAccessException exception) {
            if (!SqliteIntegrityErrors.isForeignKeyViolation(exception)) {
                throw exception;
            }
            LOGGER.warn("Bulk environment replacement rejected by related records", exception);
            throw new IllegalStateException(HAS_RELATED_RECORDS, exception);
        }
    }

    private CustomerEntity customer(String customerId) {
        return customers.findById(customerId).orElseThrow(CustomerNotFoundException::new);
    }

    private List<EnvironmentRow> toRows(List<ProjectEnvironmentEntity> entities) {
        return entities.stream().map(EnvironmentCatalogAdapter::toRow).toList();
    }

    private static EnvironmentRow toRow(ProjectEnvironmentEntity environment) {
        return new EnvironmentRow(environment.getId(), environment.getCustomer().getId(),
                environment.getCustomer().getFullName(), environment.getType());
    }
}
