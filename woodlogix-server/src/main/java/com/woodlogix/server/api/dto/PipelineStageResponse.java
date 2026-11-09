package com.woodlogix.server.api.dto;

import com.woodlogix.core.enums.SalesStage;

import java.util.List;
import java.util.Objects;

public record PipelineStageResponse(SalesStage stage, List<SalesProjectResponse> projects) {
    public PipelineStageResponse {
        projects = List.copyOf(Objects.requireNonNull(projects, "projects must not be null"));
    }
}
