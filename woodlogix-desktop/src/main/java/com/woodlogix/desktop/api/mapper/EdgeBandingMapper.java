package com.woodlogix.desktop.api.mapper;

import com.woodlogix.application.edgebanding.EdgeBandingRow;
import com.woodlogix.desktop.api.dto.EdgeBandingRequest;
import com.woodlogix.desktop.api.dto.EdgeBandingResponse;
import com.woodlogix.desktop.service.EdgeBandingData;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface EdgeBandingMapper {
    EdgeBandingRequest toRequest(EdgeBandingData data);

    EdgeBandingRow toRow(EdgeBandingResponse response);

    List<EdgeBandingRow> toRows(List<EdgeBandingResponse> responses);
}
