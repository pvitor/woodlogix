package com.woodlogix.core.enums;

import java.util.Locale;

public enum SalesStage {
    CONTACT,
    MEASUREMENT_SCHEDULED,
    QUOTE_SENT,
    NEGOTIATION,
    CLOSED;

    public String getMessageKey() {
        return "sales.stage." + name().toLowerCase(Locale.ROOT);
    }
}
