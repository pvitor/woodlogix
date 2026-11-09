package com.woodlogix.desktop.api.dto;

import java.util.List;
import java.util.Objects;

public record EnvironmentReplaceRequest(List<String> types) {
    public EnvironmentReplaceRequest {
        types = List.copyOf(Objects.requireNonNull(types, "types must not be null"));
    }
}
