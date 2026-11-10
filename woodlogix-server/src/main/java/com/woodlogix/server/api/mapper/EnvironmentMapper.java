package com.woodlogix.server.api.mapper;

import com.woodlogix.application.environment.EnvironmentRow;
import com.woodlogix.application.environment.EnvironmentSyncResult;
import com.woodlogix.core.enums.RoomType;
import com.woodlogix.server.api.dto.EnvironmentAddRequest;
import com.woodlogix.server.api.dto.EnvironmentReplaceRequest;
import com.woodlogix.server.api.dto.EnvironmentResponse;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface EnvironmentMapper {
    EnvironmentResponse toResponse(EnvironmentRow row);

    List<EnvironmentResponse> toResponses(List<EnvironmentRow> rows);

    @Named("environmentAddRoomTypes")
    default List<RoomType> roomTypes(EnvironmentAddRequest request) {
        return request == null ? null : request.roomTypes();
    }

    @Named("environmentAddCustomRoomType")
    default String customRoomType(EnvironmentAddRequest request) {
        return request == null ? null : request.customRoomType();
    }

    @Named("environmentReplacementTypes")
    default List<String> replacementTypes(EnvironmentReplaceRequest request) {
        return request == null ? null : request.types();
    }

    @Named("synchronizedEnvironments")
    default List<EnvironmentResponse> toResponses(EnvironmentSyncResult result) {
        return result == null ? null : toResponses(result.environments());
    }
}
