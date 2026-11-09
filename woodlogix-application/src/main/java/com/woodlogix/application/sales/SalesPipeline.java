package com.woodlogix.application.sales;

import com.woodlogix.application.transaction.TransactionPort;
import com.woodlogix.core.enums.SalesStage;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class SalesPipeline {
    private final SalesPipelinePort projects;
    private final TransactionPort transactions;

    public SalesPipeline(SalesPipelinePort projects, TransactionPort transactions) {
        this.projects = Objects.requireNonNull(projects);
        this.transactions = Objects.requireNonNull(transactions);
    }

    public Map<SalesStage, List<SalesProjectRow>> findPipeline() {
        return transactions.executeReadOnly(() -> {
            Map<SalesStage, List<SalesProjectRow>> pipeline = new EnumMap<>(SalesStage.class);
            for (SalesStage stage : SalesStage.values()) {
                pipeline.put(stage, projects.findAllByStageOrderByCreatedAtDesc(stage));
            }
            return pipeline;
        });
    }

    public SalesProjectRow create(SalesProjectInput input) {
        Objects.requireNonNull(input, "input must not be null");
        return transactions.executeWithResult(() -> {
            if (!projects.customerExists(input.customerId())) {
                throw new IllegalArgumentException("error.customer-not-found");
            }
            String environmentId = input.environmentId();
            if (environmentId != null && !environmentId.isBlank()) {
                String environmentCustomerId = projects.findEnvironmentCustomerId(environmentId)
                        .orElseThrow(() ->
                                new IllegalArgumentException("error.environment-not-found"));
                if (!environmentCustomerId.equals(input.customerId())) {
                    throw new IllegalArgumentException("error.environment-customer-mismatch");
                }
            }
            return projects.createAndFlush(input);
        });
    }

    public void move(String projectId, SalesStage stage) {
        transactions.execute(() -> {
            if (!projects.moveAndFlush(projectId, stage)) {
                throw new IllegalArgumentException("error.project-not-found");
            }
        });
    }

    public SalesProjectRow findById(String projectId) {
        return transactions.executeReadOnly(() -> projects.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("error.project-not-found")));
    }
}
