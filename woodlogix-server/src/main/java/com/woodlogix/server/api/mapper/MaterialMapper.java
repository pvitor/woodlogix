package com.woodlogix.server.api.mapper;

import com.woodlogix.application.material.MaterialInput;
import com.woodlogix.application.material.MaterialRow;
import com.woodlogix.server.api.dto.MaterialRequest;
import com.woodlogix.server.api.dto.MaterialResponse;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface MaterialMapper {
    MaterialInput toInput(MaterialRequest request);

    MaterialResponse toResponse(MaterialRow row);

    List<MaterialResponse> toResponses(List<MaterialRow> rows);
}
