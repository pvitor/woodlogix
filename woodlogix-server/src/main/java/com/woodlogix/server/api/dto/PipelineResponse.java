package com.woodlogix.server.api.dto;

import java.util.List;
import java.util.Objects;

public record PipelineResponse(List<PipelineStageResponse> stages) {
    public PipelineResponse {
        stages = List.copyOf(Objects.requireNonNull(stages, "stages must not be null"));
    }
}
