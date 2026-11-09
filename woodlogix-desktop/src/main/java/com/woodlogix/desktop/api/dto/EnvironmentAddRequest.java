package com.woodlogix.desktop.api.dto;

import com.woodlogix.core.enums.RoomType;

import java.util.List;
import java.util.Objects;

public record EnvironmentAddRequest(List<RoomType> roomTypes, String customRoomType) {
    public EnvironmentAddRequest {
        roomTypes = List.copyOf(Objects.requireNonNull(roomTypes, "roomTypes must not be null"));
    }
}
