package com.woodlogix.desktop.api.mapper;

import com.woodlogix.application.environment.EnvironmentRow;
import com.woodlogix.core.enums.RoomType;
import com.woodlogix.desktop.api.dto.EnvironmentAddRequest;
import com.woodlogix.desktop.api.dto.EnvironmentReplaceRequest;
import com.woodlogix.desktop.api.dto.EnvironmentResponse;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface EnvironmentMapper {
    EnvironmentRow toRow(EnvironmentResponse response);

    List<EnvironmentRow> toRows(List<EnvironmentResponse> responses);

    @Named("environmentAddRequest")
    default EnvironmentAddRequest toAddRequest(List<RoomType> roomTypes, String customRoomType) {
        return new EnvironmentAddRequest(
                roomTypes == null ? List.of() : roomTypes, customRoomType);
    }

    @Named("environmentReplaceRequest")
    default EnvironmentReplaceRequest toReplaceRequest(List<String> types) {
        return new EnvironmentReplaceRequest(types);
    }
}
