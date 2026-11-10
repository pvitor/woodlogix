package com.woodlogix.server.api.mapper;

import com.woodlogix.application.sales.SalesProjectInput;
import com.woodlogix.application.sales.SalesProjectRow;
import com.woodlogix.core.enums.SalesStage;
import com.woodlogix.server.api.dto.PipelineResponse;
import com.woodlogix.server.api.dto.PipelineStageResponse;
import com.woodlogix.server.api.dto.ChangeSalesStageRequest;
import com.woodlogix.server.api.dto.SalesProjectRequest;
import com.woodlogix.server.api.dto.SalesProjectResponse;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface SalesPipelineMapper {
    SalesProjectInput toInput(SalesProjectRequest request);

    SalesProjectResponse toResponse(SalesProjectRow row);

    List<SalesProjectResponse> toResponses(List<SalesProjectRow> rows);

    @Named("salesStage")
    default SalesStage toStage(ChangeSalesStageRequest request) {
        return request == null ? null : request.stage();
    }

    @Named("pipelineResponse")
    default PipelineResponse toResponse(Map<SalesStage, List<SalesProjectRow>> pipeline) {
        if (pipeline == null) {
            return null;
        }

        List<PipelineStageResponse> stages = new ArrayList<>();
        for (SalesStage stage : SalesStage.values()) {
            List<SalesProjectRow> projects = pipeline.get(stage);
            stages.add(new PipelineStageResponse(
                    stage, projects == null ? List.of() : toResponses(projects)));
        }
        return new PipelineResponse(stages);
    }
}
