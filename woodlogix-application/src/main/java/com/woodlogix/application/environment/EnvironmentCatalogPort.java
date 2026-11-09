package com.woodlogix.application.environment;

import java.util.List;
import java.util.Optional;

public interface EnvironmentCatalogPort {
    boolean customerExists(String customerId);

    boolean exists(String environmentId);

    List<EnvironmentRow> findAllOrderedByType();

    List<EnvironmentRow> findByCustomer(String customerId);

    EnvironmentRow createAndFlush(String customerId, String type);

    List<EnvironmentRow> createAllAndFlush(String customerId, List<String> types);

    Optional<EnvironmentRow> updateAndFlush(String environmentId, String customerId, String type);

    EnvironmentRow renameAndSave(String environmentId, String type);

    boolean hasPartReferences(String environmentId);

    boolean hasFurnitureItemReferences(String environmentId);

    boolean hasSalesProjectReferences(String environmentId);

    void deleteAndFlush(String environmentId);

    void deleteAllAndFlush(List<String> environmentIds);
}
