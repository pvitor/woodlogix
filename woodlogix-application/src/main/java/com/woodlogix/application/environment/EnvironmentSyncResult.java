package com.woodlogix.application.environment;

import java.util.List;
import java.util.Objects;

public record EnvironmentSyncResult(
        List<EnvironmentRow> environments,
        boolean changed,
        int requestedCount
) {
    public EnvironmentSyncResult {
        environments = List.copyOf(Objects.requireNonNull(environments, "environments must not be null"));
    }
}
