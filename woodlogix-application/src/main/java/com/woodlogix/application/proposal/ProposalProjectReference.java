package com.woodlogix.application.proposal;

import com.woodlogix.core.model.CustomerSnapshot;
import com.woodlogix.core.model.SalesProjectId;

import java.util.Objects;

public record ProposalProjectReference(
        SalesProjectId salesProjectId,
        CustomerSnapshot customer
) {
    public ProposalProjectReference {
        Objects.requireNonNull(salesProjectId, "salesProjectId must not be null");
        Objects.requireNonNull(customer, "customer must not be null");
    }
}
