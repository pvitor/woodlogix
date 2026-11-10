package com.woodlogix.server.api.mapper;

import com.woodlogix.application.edgebanding.EdgeBandingRow;
import com.woodlogix.application.material.MaterialRow;
import com.woodlogix.application.workpiece.ProjectWorkpieceInput;
import com.woodlogix.application.workpiece.ProjectWorkpieceRow;
import com.woodlogix.server.api.dto.EdgeBandingResponse;
import com.woodlogix.server.api.dto.MaterialResponse;
import com.woodlogix.server.api.dto.ProjectWorkpieceRequest;
import com.woodlogix.server.api.dto.ProjectWorkpieceResponse;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ProjectWorkpieceMapper {
    ProjectWorkpieceInput toInput(ProjectWorkpieceRequest request);

    ProjectWorkpieceResponse toResponse(ProjectWorkpieceRow row);

    List<ProjectWorkpieceResponse> toResponses(List<ProjectWorkpieceRow> rows);

    MaterialResponse toResponse(MaterialRow row);

    EdgeBandingResponse toResponse(EdgeBandingRow row);
}
