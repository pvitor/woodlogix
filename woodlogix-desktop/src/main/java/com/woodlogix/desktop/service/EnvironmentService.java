package com.woodlogix.desktop.service;

import com.woodlogix.core.enums.RoomType;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import com.woodlogix.desktop.persistence.entity.CustomerEntity;
import com.woodlogix.desktop.persistence.entity.ProjectEnvironmentEntity;
import com.woodlogix.desktop.persistence.repository.CustomerRepository;
import com.woodlogix.desktop.persistence.repository.FurniturePartRepository;
import com.woodlogix.desktop.persistence.repository.ProjectEnvironmentRepository;
import com.woodlogix.desktop.persistence.repository.SalesProjectRepository;
import com.woodlogix.desktop.service.event.EnvironmentChangedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@Validated
public class EnvironmentService {
    private static final Logger LOGGER = LoggerFactory.getLogger(EnvironmentService.class);

    private final CustomerRepository customerRepository;
    private final ProjectEnvironmentRepository environmentRepository;
    private final FurniturePartRepository furniturePartRepository;
    private final SalesProjectRepository salesProjectRepository;
    private final ApplicationEventPublisher eventPublisher;

    public EnvironmentService(
            CustomerRepository customerRepository,
            ProjectEnvironmentRepository environmentRepository,
            FurniturePartRepository furniturePartRepository,
            SalesProjectRepository salesProjectRepository,
            ApplicationEventPublisher eventPublisher
    ) {
        this.customerRepository = Objects.requireNonNull(
                customerRepository, "customerRepository must not be null"
        );
        this.environmentRepository = Objects.requireNonNull(
                environmentRepository, "environmentRepository must not be null"
        );
        this.furniturePartRepository = Objects.requireNonNull(
                furniturePartRepository, "furniturePartRepository must not be null"
        );
        this.salesProjectRepository = Objects.requireNonNull(
                salesProjectRepository, "salesProjectRepository must not be null"
        );
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Transactional(readOnly = true)
    public List<EnvironmentRow> findAll() {
        return distinctEnvironmentRows(environmentRepository.findAllByOrderByTypeAsc());
    }

    @Transactional(readOnly = true)
    public List<EnvironmentRow> findByCustomer(String customerId) {
        return distinctEnvironmentRows(environmentRepository.findAllByCustomerId(customerId));
    }

    @Transactional
    public EnvironmentRow create(@Valid EnvironmentData data) {
        CustomerEntity customer = customerRepository.findById(data.customerId())
                .orElseThrow(() -> new EntityNotFoundException("error.customer-not-found"));
        ProjectEnvironmentEntity environment = environmentRepository.saveAndFlush(
                new ProjectEnvironmentEntity(customer, data.type())
        );
        eventPublisher.publishEvent(new EnvironmentChangedEvent());
        LOGGER.info("Project environment registered");
        return toRow(environment);
    }

    @Transactional
    public List<EnvironmentRow> createForCustomer(
            @NotBlank String customerId,
            @NotEmpty List<@NotNull RoomType> roomTypes
    ) {
        return createForCustomer(customerId, roomTypes, null);
    }

    @Transactional
    public List<EnvironmentRow> createForCustomer(
            @NotBlank String customerId,
            List<@NotNull RoomType> roomTypes,
            String customRoomType
    ) {
        CustomerEntity customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new EntityNotFoundException("error.customer-not-found"));
        List<RoomType> selectedTypes = roomTypes == null ? List.of()
                : roomTypes.stream().distinct().toList();
        String customType = customRoomType == null ? null : customRoomType.trim();
        if (customType != null && customType.isBlank()) {
            customType = null;
        }
        if (selectedTypes.isEmpty() && customType == null) {
            throw new IllegalArgumentException("error.environment-type");
        }

        Set<String> selectedKeys = selectedTypes.stream()
                .map(type -> normalizeRoomType(type.name()))
                .collect(java.util.stream.Collectors.toSet());
        if (customType != null
                && !selectedKeys.add(normalizeRoomType(customType))) {
            customType = null;
        }
        String customTypeToCreate = customType;

        Set<String> existingTypes = environmentRepository.findAllByCustomerId(customerId)
                .stream()
                .map(ProjectEnvironmentEntity::getType)
                .map(EnvironmentService::normalizeRoomType)
                .collect(java.util.stream.Collectors.toSet());
        List<ProjectEnvironmentEntity> environments = selectedTypes.stream()
                .filter(type -> !existingTypes.contains(normalizeRoomType(type.name())))
                .map(type -> new ProjectEnvironmentEntity(customer, type))
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        if (customTypeToCreate != null
                && !existingTypes.contains(normalizeRoomType(customTypeToCreate))
                && !selectedTypes.stream().anyMatch(type ->
                        normalizeRoomType(type.name()).equals(normalizeRoomType(customTypeToCreate)))) {
            environments.add(new ProjectEnvironmentEntity(customer, customTypeToCreate));
        }
        if (environments.isEmpty()) {
            LOGGER.info("No new project environments to register");
            return List.of();
        }
        List<EnvironmentRow> created = environmentRepository.saveAllAndFlush(environments).stream()
                .map(this::toRow)
                .toList();
        eventPublisher.publishEvent(new EnvironmentChangedEvent());
        LOGGER.info("Project environments registered: {}", created.size());
        return created;
    }

    @Transactional
    public List<EnvironmentRow> saveOrUpdateCustomerEnvironments(
            @NotBlank String customerId,
            @NotNull List<@NotBlank String> roomTypes
    ) {
        CustomerEntity customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new EntityNotFoundException("error.customer-not-found"));

        Map<String, String> requestedTypes = new LinkedHashMap<>();
        for (String roomType : roomTypes) {
            String normalizedType = normalizeRoomType(roomType);
            requestedTypes.putIfAbsent(normalizedType, roomType.trim());
        }

        List<ProjectEnvironmentEntity> existing =
                environmentRepository.findAllByCustomerId(customerId);
        Map<String, ProjectEnvironmentEntity> existingByType = new LinkedHashMap<>();
        List<ProjectEnvironmentEntity> environmentsToRemove =
                new java.util.ArrayList<>();
        for (ProjectEnvironmentEntity environment : existing) {
            String normalizedType = normalizeRoomType(environment.getType());
            if (!requestedTypes.containsKey(normalizedType)
                    || existingByType.containsKey(normalizedType)) {
                environmentsToRemove.add(environment);
            } else {
                existingByType.put(normalizedType, environment);
            }
        }

        List<ProjectEnvironmentEntity> environmentsToSave =
                new java.util.ArrayList<>();
        for (Map.Entry<String, String> requested : requestedTypes.entrySet()) {
            ProjectEnvironmentEntity environment = existingByType.get(requested.getKey());
            if (environment == null) {
                environmentsToSave.add(new ProjectEnvironmentEntity(
                        customer, requested.getValue()));
            } else if (!environment.getType().equals(requested.getValue())) {
                environment.update(customer, requested.getValue());
                environmentsToSave.add(environment);
            }
        }

        if (environmentsToRemove.isEmpty() && environmentsToSave.isEmpty()) {
            return existing.stream()
                    .filter(environment -> !environmentsToRemove.contains(environment))
                    .map(this::toRow)
                    .toList();
        }

        boolean hasLinkedRecords = environmentsToRemove.stream()
                .anyMatch(environment -> furniturePartRepository
                                .existsByProjectEnvironmentId(environment.getId())
                        || salesProjectRepository.existsByEnvironmentId(environment.getId()));
        if (hasLinkedRecords) {
            LOGGER.warn("Customer environment replacement rejected because rooms have linked records");
            throw new IllegalStateException("error.environment-has-related-records");
        }

        try {
            if (!environmentsToRemove.isEmpty()) {
                environmentRepository.deleteAll(environmentsToRemove);
                environmentRepository.flush();
            }
            if (!environmentsToSave.isEmpty()) {
                environmentRepository.saveAllAndFlush(environmentsToSave);
            }
        } catch (DataIntegrityViolationException exception) {
            LOGGER.warn("Bulk environment replacement rejected by related records", exception);
            throw new IllegalStateException("error.environment-has-related-records", exception);
        }

        eventPublisher.publishEvent(new EnvironmentChangedEvent());
        LOGGER.info("Customer environments synchronized: {}", requestedTypes.size());
        return environmentRepository.findAllByCustomerId(customerId).stream()
                .map(this::toRow)
                .toList();
    }

    @Transactional
    public void deleteAllByCustomer(@NotBlank String customerId) {
        saveOrUpdateCustomerEnvironments(customerId, List.of());
        LOGGER.info("All project environments removed for customer");
    }

    @Transactional
    public EnvironmentRow update(String environmentId, @Valid EnvironmentData data) {
        ProjectEnvironmentEntity environment = environmentRepository.findById(environmentId)
                .orElseThrow(() -> new EntityNotFoundException("error.environment-not-found"));
        CustomerEntity customer = customerRepository.findById(data.customerId())
                .orElseThrow(() -> new EntityNotFoundException("error.customer-not-found"));
        environment.update(customer, data.type());
        ProjectEnvironmentEntity updated = environmentRepository.saveAndFlush(environment);
        eventPublisher.publishEvent(new EnvironmentChangedEvent());
        LOGGER.info("Project environment updated");
        return toRow(updated);
    }

    @Transactional
    public void delete(String environmentId) {
        if (!environmentRepository.existsById(environmentId)) {
            throw new EntityNotFoundException("error.environment-not-found");
        }
        try {
            environmentRepository.deleteById(environmentId);
            environmentRepository.flush();
            eventPublisher.publishEvent(new EnvironmentChangedEvent());
            LOGGER.info("Project environment deleted");
        } catch (DataIntegrityViolationException exception) {
            LOGGER.warn("Environment deletion rejected because related records exist", exception);
            throw new IllegalStateException("error.environment-has-related-records", exception);
        }
    }

    @Transactional(readOnly = true)
    public List<CustomerRow> findCustomers() {
        return customerRepository.findAll().stream()
                .map(customer -> new CustomerRow(customer.getId(), customer.getFullName(),
                        customer.getPhone(), customer.getEmail(), customer.getAddress()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CustomerRow> findCustomers(String name) {
        if (name == null || name.isBlank()) {
            return findCustomers();
        }
        return customerRepository.findByFullNameContainingIgnoreCase(name.trim()).stream()
                .map(customer -> new CustomerRow(customer.getId(), customer.getFullName(),
                        customer.getPhone(), customer.getEmail(), customer.getAddress()))
                .toList();
    }

    private EnvironmentRow toRow(ProjectEnvironmentEntity environment) {
        return new EnvironmentRow(environment.getId(), environment.getCustomer().getId(),
                environment.getCustomer().getFullName(), environment.getType());
    }

    private List<EnvironmentRow> distinctEnvironmentRows(
            List<ProjectEnvironmentEntity> environments
    ) {
        Map<String, EnvironmentRow> uniqueRows = new LinkedHashMap<>();
        for (ProjectEnvironmentEntity environment : environments) {
            String key = environment.getCustomer().getId() + "\u0000"
                    + normalizeRoomType(environment.getType());
            uniqueRows.putIfAbsent(key, toRow(environment));
        }
        return new ArrayList<>(uniqueRows.values());
    }

    private static String normalizeRoomType(String roomType) {
        return roomType.trim()
                .replaceAll("[\\s_-]+", "")
                .toLowerCase(Locale.ROOT);
    }
}
