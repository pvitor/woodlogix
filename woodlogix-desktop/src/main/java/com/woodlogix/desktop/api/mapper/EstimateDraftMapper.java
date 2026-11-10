package com.woodlogix.desktop.api.mapper;

import com.woodlogix.desktop.api.dto.ActiveDraftResponse;
import com.woodlogix.desktop.api.dto.EstimateDraftDto;
import com.woodlogix.desktop.service.EstimateDraftData;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;

import java.util.Optional;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface EstimateDraftMapper {
    EstimateDraftDto toRequest(EstimateDraftData draft);

    EstimateDraftDto.WorkpieceDto toDto(EstimateDraftData.EstimateDraftWorkpieceData workpiece);

    EstimateDraftDto.HardwareDto toDto(EstimateDraftData.EstimateDraftHardwareData hardware);

    EstimateDraftData toData(EstimateDraftDto draft);

    EstimateDraftData.EstimateDraftWorkpieceData toData(EstimateDraftDto.WorkpieceDto workpiece);

    EstimateDraftData.EstimateDraftHardwareData toData(EstimateDraftDto.HardwareDto hardware);

    @Named("activeDraftData")
    default Optional<EstimateDraftData> toActiveDraftData(ActiveDraftResponse response) {
        return response == null
                ? null
                : Optional.ofNullable(response.draft()).map(this::toData);
    }
}
