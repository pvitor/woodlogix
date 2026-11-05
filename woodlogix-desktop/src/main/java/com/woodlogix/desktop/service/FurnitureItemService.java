package com.woodlogix.desktop.service;

import com.woodlogix.desktop.persistence.entity.CustomerEntity;
import com.woodlogix.desktop.persistence.entity.FurnitureItemEntity;
import com.woodlogix.desktop.persistence.entity.ProjectEnvironmentEntity;
import com.woodlogix.desktop.persistence.repository.CustomerRepository;
import com.woodlogix.desktop.persistence.repository.FurnitureItemRepository;
import com.woodlogix.desktop.persistence.repository.ProjectEnvironmentRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Objects;

@Service
@Validated
public class FurnitureItemService {
    private static final Logger LOGGER = LoggerFactory.getLogger(FurnitureItemService.class);

    private final FurnitureItemRepository furnitureItemRepository;
    private final CustomerRepository customerRepository;
    private final ProjectEnvironmentRepository environmentRepository;

    public FurnitureItemService(
            FurnitureItemRepository furnitureItemRepository,
            CustomerRepository customerRepository,
            ProjectEnvironmentRepository environmentRepository
    ) {
        this.furnitureItemRepository = Objects.requireNonNull(furnitureItemRepository);
        this.customerRepository = Objects.requireNonNull(customerRepository);
        this.environmentRepository = Objects.requireNonNull(environmentRepository);
    }

    @Transactional
    public FurnitureItemEntity create(@Valid FurnitureItemData data) {
        CustomerEntity customer = findCustomer(data.customerId());
        ProjectEnvironmentEntity environment = findEnvironment(data.environmentId(), customer);
        FurnitureItemEntity item = furnitureItemRepository.saveAndFlush(new FurnitureItemEntity(
                customer, environment, data.name(), data.furnitureType(), data.woodType(),
                data.lengthMm(), data.heightMm(), data.depthMm()
        ));
        LOGGER.info("Furniture item registered");
        return item;
    }

    @Transactional
    public FurnitureItemEntity update(String itemId, @Valid FurnitureItemData data) {
        FurnitureItemEntity item = furnitureItemRepository.findById(itemId)
                .orElseThrow(() -> new EntityNotFoundException("error.furniture-item-not-found"));
        CustomerEntity customer = findCustomer(data.customerId());
        ProjectEnvironmentEntity environment = findEnvironment(data.environmentId(), customer);
        item.update(customer, environment, data.name(), data.furnitureType(), data.woodType(),
                data.lengthMm(), data.heightMm(), data.depthMm());
        FurnitureItemEntity updated = furnitureItemRepository.saveAndFlush(item);
        LOGGER.info("Furniture item updated");
        return updated;
    }

    @Transactional
    public void delete(String itemId) {
        FurnitureItemEntity item = furnitureItemRepository.findById(itemId)
                .orElseThrow(() -> new EntityNotFoundException("error.furniture-item-not-found"));
        furnitureItemRepository.delete(item);
        furnitureItemRepository.flush();
        LOGGER.info("Furniture item deleted");
    }

    @Transactional(readOnly = true)
    public List<FurnitureItemEntity> findByCustomer(String customerId) {
        return furnitureItemRepository.findAllByCustomerIdOrderByNameAsc(customerId);
    }

    @Transactional(readOnly = true)
    public List<FurnitureItemEntity> searchByCustomer(String customerId, String searchTerm) {
        if (searchTerm == null || searchTerm.isBlank()) {
            return findByCustomer(customerId);
        }
        return furnitureItemRepository
                .findAllByCustomerIdAndNameContainingIgnoreCaseOrderByNameAsc(
                        customerId, searchTerm.trim());
    }

    @Transactional(readOnly = true)
    public List<FurnitureItemEntity> findByEnvironment(String environmentId) {
        return furnitureItemRepository.findAllByEnvironmentIdOrderByNameAsc(environmentId);
    }

    private CustomerEntity findCustomer(String customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new EntityNotFoundException("error.customer-not-found"));
    }

    private ProjectEnvironmentEntity findEnvironment(
            String environmentId, CustomerEntity customer
    ) {
        if (environmentId == null || environmentId.isBlank()) {
            return null;
        }
        ProjectEnvironmentEntity environment = environmentRepository.findById(environmentId)
                .orElseThrow(() -> new EntityNotFoundException("error.environment-not-found"));
        if (!customer.getId().equals(environment.getCustomer().getId())) {
            throw new IllegalArgumentException("error.environment-customer-mismatch");
        }
        return environment;
    }
}
