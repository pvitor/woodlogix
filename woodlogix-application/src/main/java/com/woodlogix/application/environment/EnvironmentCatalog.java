package com.woodlogix.application.environment;

import com.woodlogix.application.customer.CustomerNotFoundException;
import com.woodlogix.application.transaction.TransactionPort;
import com.woodlogix.core.enums.RoomType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class EnvironmentCatalog {
    private static final String HAS_RELATED_RECORDS = "error.environment-has-related-records";

    private final EnvironmentCatalogPort environments;
    private final EnvironmentChangeNotifier notifier;
    private final TransactionPort transactions;

    public EnvironmentCatalog(EnvironmentCatalogPort environments, EnvironmentChangeNotifier notifier,
                              TransactionPort transactions) {
        this.environments = Objects.requireNonNull(environments);
        this.notifier = Objects.requireNonNull(notifier);
        this.transactions = Objects.requireNonNull(transactions);
    }

    public List<EnvironmentRow> findAll() {
        return transactions.executeReadOnly(
                () -> distinctRows(environments.findAllOrderedByType()));
    }

    public List<EnvironmentRow> findByCustomer(String customerId) {
        return transactions.executeReadOnly(
                () -> distinctRows(environments.findByCustomer(customerId)));
    }

    public EnvironmentRow create(String customerId, String type) {
        return transactions.executeWithResult(() -> {
            requireCustomer(customerId);
            EnvironmentRow created = environments.createAndFlush(customerId, type);
            notifier.environmentChanged();
            return created;
        });
    }

    public List<EnvironmentRow> createForCustomer(String customerId, List<RoomType> roomTypes,
                                                  String customRoomType) {
        return transactions.executeWithResult(() -> {
            requireCustomer(customerId);
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
                    .collect(Collectors.toSet());
            if (customType != null && !selectedKeys.add(normalizeRoomType(customType))) {
                customType = null;
            }
            String customTypeToCreate = customType;

            Set<String> existingTypes = environments.findByCustomer(customerId).stream()
                    .map(EnvironmentRow::type)
                    .map(EnvironmentCatalog::normalizeRoomType)
                    .collect(Collectors.toSet());
            List<String> typesToCreate = selectedTypes.stream()
                    .filter(type -> !existingTypes.contains(normalizeRoomType(type.name())))
                    .map(RoomType::name)
                    .collect(Collectors.toCollection(ArrayList::new));
            if (customTypeToCreate != null
                    && !existingTypes.contains(normalizeRoomType(customTypeToCreate))
                    && selectedTypes.stream().noneMatch(type ->
                            normalizeRoomType(type.name()).equals(normalizeRoomType(customTypeToCreate)))) {
                typesToCreate.add(customTypeToCreate);
            }
            if (typesToCreate.isEmpty()) {
                return List.of();
            }
            List<EnvironmentRow> created = environments.createAllAndFlush(customerId, typesToCreate);
            notifier.environmentChanged();
            return created;
        });
    }

    public EnvironmentSyncResult saveOrUpdateCustomerEnvironments(String customerId,
                                                                 List<String> roomTypes) {
        return transactions.executeWithResult(() -> synchronize(customerId, roomTypes));
    }

    public void deleteAllByCustomer(String customerId) {
        transactions.execute(() -> synchronize(customerId, List.of()));
    }

    public EnvironmentRow update(String environmentId, String customerId, String type) {
        return transactions.executeWithResult(() -> {
            if (!environments.exists(environmentId)) {
                throw new EnvironmentNotFoundException();
            }
            requireCustomer(customerId);
            EnvironmentRow updated = environments.updateAndFlush(environmentId, customerId, type)
                    .orElseThrow(EnvironmentNotFoundException::new);
            notifier.environmentChanged();
            return updated;
        });
    }

    public void delete(String environmentId) {
        transactions.execute(() -> {
            if (!environments.exists(environmentId)) {
                throw new EnvironmentNotFoundException();
            }
            environments.deleteAndFlush(environmentId);
            notifier.environmentChanged();
        });
    }

    private EnvironmentSyncResult synchronize(String customerId, List<String> roomTypes) {
        requireCustomer(customerId);

        Map<String, String> requestedTypes = new LinkedHashMap<>();
        for (String roomType : roomTypes) {
            requestedTypes.putIfAbsent(normalizeRoomType(roomType), roomType.trim());
        }

        List<EnvironmentRow> existing = environments.findByCustomer(customerId);
        Map<String, EnvironmentRow> existingByType = new LinkedHashMap<>();
        List<EnvironmentRow> environmentsToRemove = new ArrayList<>();
        for (EnvironmentRow environment : existing) {
            String normalizedType = normalizeRoomType(environment.type());
            if (!requestedTypes.containsKey(normalizedType)
                    || existingByType.containsKey(normalizedType)) {
                environmentsToRemove.add(environment);
            } else {
                existingByType.put(normalizedType, environment);
            }
        }

        List<EnvironmentRow> environmentsToRename = new ArrayList<>();
        List<String> typesToCreate = new ArrayList<>();
        for (Map.Entry<String, String> requested : requestedTypes.entrySet()) {
            EnvironmentRow environment = existingByType.get(requested.getKey());
            if (environment == null) {
                typesToCreate.add(requested.getValue());
            } else if (!environment.type().equals(requested.getValue())) {
                environmentsToRename.add(environment);
            }
        }

        if (environmentsToRemove.isEmpty() && environmentsToRename.isEmpty()
                && typesToCreate.isEmpty()) {
            return new EnvironmentSyncResult(existing, false, requestedTypes.size());
        }

        boolean hasLinkedRecords = environmentsToRemove.stream()
                .anyMatch(environment -> environments.hasPartReferences(environment.id())
                        || environments.hasFurnitureItemReferences(environment.id())
                        || environments.hasSalesProjectReferences(environment.id()));
        if (hasLinkedRecords) {
            throw new IllegalStateException(HAS_RELATED_RECORDS);
        }

        if (!environmentsToRemove.isEmpty()) {
            environments.deleteAllAndFlush(environmentsToRemove.stream().map(EnvironmentRow::id).toList());
        }
        for (EnvironmentRow environment : environmentsToRename) {
            String requestedType = requestedTypes.get(normalizeRoomType(environment.type()));
            environments.renameAndSave(environment.id(), requestedType);
        }
        if (!typesToCreate.isEmpty()) {
            environments.createAllAndFlush(customerId, typesToCreate);
        }

        notifier.environmentChanged();
        return new EnvironmentSyncResult(environments.findByCustomer(customerId), true, requestedTypes.size());
    }

    private void requireCustomer(String customerId) {
        if (!environments.customerExists(customerId)) {
            throw new CustomerNotFoundException();
        }
    }

    private static List<EnvironmentRow> distinctRows(List<EnvironmentRow> rows) {
        Map<String, EnvironmentRow> uniqueRows = new LinkedHashMap<>();
        for (EnvironmentRow environment : rows) {
            String key = environment.customerId() + "\u0000" + normalizeRoomType(environment.type());
            uniqueRows.putIfAbsent(key, environment);
        }
        return new ArrayList<>(uniqueRows.values());
    }

    private static String normalizeRoomType(String roomType) {
        return roomType.trim()
                .replaceAll("[\\s_-]+", "")
                .toLowerCase(Locale.ROOT);
    }
}
