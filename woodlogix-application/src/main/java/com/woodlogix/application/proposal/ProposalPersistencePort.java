package com.woodlogix.application.proposal;

import com.woodlogix.core.model.Proposal;
import com.woodlogix.core.model.ProposalId;
import com.woodlogix.core.model.SalesProjectId;

import java.util.List;
import java.util.Optional;

public interface ProposalPersistencePort {
    ProposalId reserveNextId(int year);

    ProposalRecord saveNew(Proposal proposal);

    Optional<ProposalRecord> findById(ProposalId proposalId);

    List<ProposalSummaryRow> findAllBySalesProjectId(SalesProjectId salesProjectId);
}
