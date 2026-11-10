package com.woodlogix.desktop.api.mapper;

import com.woodlogix.application.material.MaterialRow;
import com.woodlogix.desktop.api.dto.MaterialRequest;
import com.woodlogix.desktop.api.dto.MaterialResponse;
import com.woodlogix.desktop.service.MaterialData;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface MaterialMapper {
    MaterialRequest toRequest(MaterialData data);

    MaterialRow toRow(MaterialResponse response);

    List<MaterialRow> toRows(List<MaterialResponse> responses);
}
