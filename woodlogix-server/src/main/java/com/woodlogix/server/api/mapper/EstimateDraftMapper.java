package com.woodlogix.server.api.mapper;

import com.woodlogix.application.draft.EstimateDraftInput;
import com.woodlogix.application.draft.EstimateDraftRow;
import com.woodlogix.server.api.dto.ActiveDraftResponse;
import com.woodlogix.server.api.dto.EstimateDraftDto;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;

import java.util.Optional;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface EstimateDraftMapper {
    EstimateDraftInput toInput(EstimateDraftDto draft);

    EstimateDraftInput.WorkpieceInput toInput(EstimateDraftDto.WorkpieceDto workpiece);

    EstimateDraftInput.HardwareInput toInput(EstimateDraftDto.HardwareDto hardware);

    EstimateDraftDto toDto(EstimateDraftRow draft);

    EstimateDraftDto.WorkpieceDto toDto(EstimateDraftRow.WorkpieceRow workpiece);

    EstimateDraftDto.HardwareDto toDto(EstimateDraftRow.HardwareRow hardware);

    @Named("activeDraftResponse")
    default ActiveDraftResponse toActiveDraftResponse(Optional<EstimateDraftRow> draft) {
        return draft == null ? null : new ActiveDraftResponse(draft.map(this::toDto).orElse(null));
    }
}
