package com.woodlogix.application.proposal;

import com.woodlogix.core.model.ProposalId;
import com.woodlogix.core.model.SalesProjectId;

import java.util.Objects;

public record ProposalSummaryRow(
        ProposalId proposalId,
        SalesProjectId salesProjectId
) {
    public ProposalSummaryRow {
        Objects.requireNonNull(proposalId, "proposalId must not be null");
        Objects.requireNonNull(salesProjectId, "salesProjectId must not be null");
    }
}
