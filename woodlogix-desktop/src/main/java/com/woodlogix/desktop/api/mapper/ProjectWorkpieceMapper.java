package com.woodlogix.desktop.api.mapper;

import com.woodlogix.application.edgebanding.EdgeBandingRow;
import com.woodlogix.application.material.MaterialRow;
import com.woodlogix.application.workpiece.ProjectWorkpieceRow;
import com.woodlogix.desktop.api.dto.EdgeBandingResponse;
import com.woodlogix.desktop.api.dto.MaterialResponse;
import com.woodlogix.desktop.api.dto.ProjectWorkpieceRequest;
import com.woodlogix.desktop.api.dto.ProjectWorkpieceResponse;
import com.woodlogix.desktop.service.ProjectWorkpieceData;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ProjectWorkpieceMapper {
    ProjectWorkpieceRequest toRequest(ProjectWorkpieceData data);

    ProjectWorkpieceRow toRow(ProjectWorkpieceResponse response);

    List<ProjectWorkpieceRow> toRows(List<ProjectWorkpieceResponse> responses);

    MaterialRow toRow(MaterialResponse response);

    EdgeBandingRow toRow(EdgeBandingResponse response);
}
