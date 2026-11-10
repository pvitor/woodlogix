package com.woodlogix.desktop.api.mapper;

import com.woodlogix.application.hardware.HardwareRow;
import com.woodlogix.desktop.api.dto.HardwareRequest;
import com.woodlogix.desktop.api.dto.HardwareResponse;
import com.woodlogix.desktop.service.HardwareData;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface HardwareMapper {
    HardwareRequest toRequest(HardwareData data);

    HardwareRow toRow(HardwareResponse response);

    List<HardwareRow> toRows(List<HardwareResponse> responses);
}
