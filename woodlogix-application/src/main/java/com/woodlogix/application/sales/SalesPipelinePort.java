package com.woodlogix.application.sales;

import com.woodlogix.core.enums.SalesStage;

import java.util.List;
import java.util.Optional;

public interface SalesPipelinePort {
    boolean customerExists(String customerId);

    Optional<String> findEnvironmentCustomerId(String environmentId);

    List<SalesProjectRow> findAllByStageOrderByCreatedAtDesc(SalesStage stage);

    SalesProjectRow createAndFlush(SalesProjectInput input);

    boolean moveAndFlush(String projectId, SalesStage stage);

    Optional<SalesProjectRow> findById(String projectId);
}
