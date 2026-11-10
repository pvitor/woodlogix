package com.woodlogix.desktop.api.mapper;

import com.woodlogix.application.sales.SalesProjectRow;
import com.woodlogix.core.enums.SalesStage;
import com.woodlogix.desktop.api.dto.ChangeSalesStageRequest;
import com.woodlogix.desktop.api.dto.PipelineResponse;
import com.woodlogix.desktop.api.dto.PipelineStageResponse;
import com.woodlogix.desktop.api.dto.SalesProjectRequest;
import com.woodlogix.desktop.api.dto.SalesProjectResponse;
import com.woodlogix.desktop.service.SalesProjectData;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface SalesPipelineMapper {
    SalesProjectRequest toRequest(SalesProjectData data);

    SalesProjectRow toRow(SalesProjectResponse response);

    List<SalesProjectRow> toRows(List<SalesProjectResponse> responses);

    @Named("salesStageRequest")
    default ChangeSalesStageRequest toChangeStageRequest(SalesStage stage) {
        return stage == null ? null : new ChangeSalesStageRequest(stage);
    }

    @Named("salesPipeline")
    default Map<SalesStage, List<SalesProjectRow>> toPipeline(PipelineResponse response) {
        if (response == null) {
            return null;
        }

        Map<SalesStage, PipelineStageResponse> responseStages = new EnumMap<>(SalesStage.class);
        for (PipelineStageResponse stage : response.stages()) {
            responseStages.put(stage.stage(), stage);
        }

        Map<SalesStage, List<SalesProjectRow>> pipeline = new LinkedHashMap<>();
        for (SalesStage stage : SalesStage.values()) {
            PipelineStageResponse responseStage = responseStages.get(stage);
            pipeline.put(stage, responseStage == null ? List.of() : toRows(responseStage.projects()));
        }
        return pipeline;
    }
}
