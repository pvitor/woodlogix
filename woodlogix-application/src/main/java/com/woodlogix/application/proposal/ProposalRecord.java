package com.woodlogix.application.proposal;

import com.woodlogix.core.model.Proposal;

import java.util.Objects;

public record ProposalRecord(
        Proposal proposal,
        long persistenceVersion
) {
    public ProposalRecord {
        Objects.requireNonNull(proposal, "proposal must not be null");
        if (persistenceVersion < 0) {
            throw new IllegalArgumentException("persistenceVersion must not be negative");
        }
    }
}
