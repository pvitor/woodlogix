package com.woodlogix.core.model;

import java.util.Objects;

public final class CommercialTermsSnapshot {
    private final PaymentTerms paymentTerms;
    private final LeadTime leadTime;
    private final TransportTerms transportTerms;
    private final InstallationTerms installationTerms;
    private final String notes;

    public CommercialTermsSnapshot(
            PaymentTerms paymentTerms,
            LeadTime leadTime,
            TransportTerms transportTerms,
            InstallationTerms installationTerms,
            String notes) {
        this.paymentTerms = Objects.requireNonNull(paymentTerms, "paymentTerms must not be null");
        this.leadTime = leadTime;
        this.transportTerms = transportTerms;
        this.installationTerms = installationTerms;
        this.notes = optionalText(notes, "notes");
    }

    public PaymentTerms getPaymentTerms() {
        return paymentTerms;
    }

    public LeadTime getLeadTime() {
        return leadTime;
    }

    public TransportTerms getTransportTerms() {
        return transportTerms;
    }

    public InstallationTerms getInstallationTerms() {
        return installationTerms;
    }

    public String getNotes() {
        return notes;
    }

    private static String optionalText(String value, String field) {
        if (value == null) {
            return null;
        }
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
