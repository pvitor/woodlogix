package com.woodlogix.server.api.mapper;

import com.woodlogix.application.edgebanding.EdgeBandingInput;
import com.woodlogix.application.edgebanding.EdgeBandingRow;
import com.woodlogix.server.api.dto.EdgeBandingRequest;
import com.woodlogix.server.api.dto.EdgeBandingResponse;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface EdgeBandingMapper {
    EdgeBandingInput toInput(EdgeBandingRequest request);

    EdgeBandingResponse toResponse(EdgeBandingRow row);

    List<EdgeBandingResponse> toResponses(List<EdgeBandingRow> rows);
}
