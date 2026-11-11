package com.woodlogix.desktop.service;

import com.woodlogix.application.sales.SalesProjectRow;
import com.woodlogix.core.enums.SalesStage;
import com.woodlogix.desktop.api.client.SalesPipelineApiClient;
import com.woodlogix.desktop.api.mapper.SalesPipelineMapper;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@Validated
public class RemoteSalesPipelineService {
    private final SalesPipelineApiClient client;
    private final SalesPipelineMapper mapper;

    public RemoteSalesPipelineService(
            SalesPipelineApiClient client,
            SalesPipelineMapper mapper
    ) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
    }

    public Map<SalesStage, List<SalesProjectRow>> findPipeline() {
        return mapper.toPipeline(client.findPipeline());
    }

    public SalesProjectRow create(@Valid SalesProjectData data) {
        return mapper.toRow(client.create(mapper.toRequest(data)));
    }

    public SalesProjectRow findById(String projectId) {
        return mapper.toRow(client.findById(projectId));
    }

    public void move(String projectId, SalesStage stage) {
        client.move(projectId, mapper.toChangeStageRequest(stage));
    }
}
