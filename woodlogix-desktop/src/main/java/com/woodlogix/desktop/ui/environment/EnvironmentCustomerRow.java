package com.woodlogix.desktop.ui.environment;

import java.util.List;

public record EnvironmentCustomerRow(
        String customerId,
        String customerName,
        List<String> environmentTypes
) {
    public EnvironmentCustomerRow {
        environmentTypes = List.copyOf(environmentTypes);
    }
}
