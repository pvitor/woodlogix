package com.woodlogix.server.api.mapper;

import com.woodlogix.application.hardware.HardwareInput;
import com.woodlogix.application.hardware.HardwareRow;
import com.woodlogix.server.api.dto.HardwareRequest;
import com.woodlogix.server.api.dto.HardwareResponse;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface HardwareMapper {
    HardwareInput toInput(HardwareRequest request);

    HardwareResponse toResponse(HardwareRow row);

    List<HardwareResponse> toResponses(List<HardwareRow> rows);
}
