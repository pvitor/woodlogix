package com.woodlogix.application.proposal;

import com.woodlogix.application.transaction.TransactionPort;
import com.woodlogix.core.model.Proposal;
import com.woodlogix.core.model.ProposalDraft;
import com.woodlogix.core.model.ProposalDraftId;
import com.woodlogix.core.model.ProposalDraftStatus;
import com.woodlogix.core.model.ProposalId;
import com.woodlogix.core.model.SalesProjectId;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class ProposalManagement {
    private final ProposalPersistencePort proposals;
    private final ProposalProjectPort projects;
    private final TransactionPort transactions;
    private final Clock clock;

    public ProposalManagement(
            ProposalPersistencePort proposals,
            ProposalProjectPort projects,
            TransactionPort transactions,
            Clock clock) {
        this.proposals = Objects.requireNonNull(proposals);
        this.projects = Objects.requireNonNull(projects);
        this.transactions = Objects.requireNonNull(transactions);
        this.clock = Objects.requireNonNull(clock);
    }

    public ProposalRecord create(CreateProposalInput input) {
        Objects.requireNonNull(input, "input must not be null");
        return transactions.executeWithResult(() -> {
            ProposalId proposalId = proposals.reserveNextId(LocalDate.now(clock).getYear());
            ProposalProjectReference project = projects.findById(input.salesProjectId())
                    .orElseThrow(SalesProjectNotFoundException::new);
            if (!project.salesProjectId().equals(input.salesProjectId())) {
                throw new IllegalArgumentException("Project reference does not match input");
            }

            ProposalDraft draft = new ProposalDraft(
                    new ProposalDraftId(UUID.randomUUID().toString()),
                    proposalId,
                    project.salesProjectId(),
                    null,
                    ProposalDraftStatus.EDITABLE,
                    project.customer(),
                    input.recipient(),
                    input.currency(),
                    input.conversion(),
                    input.items(),
                    input.generalDiscount(),
                    input.validityPeriod(),
                    input.commercialTerms());
            Proposal proposal = new Proposal(proposalId, project.salesProjectId(), draft);
            return proposals.saveNew(proposal);
        });
    }

    public ProposalRecord findById(ProposalId proposalId) {
        Objects.requireNonNull(proposalId, "proposalId must not be null");
        return transactions.executeReadOnly(() -> proposals.findById(proposalId)
                .orElseThrow(ProposalNotFoundException::new));
    }

    public List<ProposalSummaryRow> findAllBySalesProjectId(
            SalesProjectId salesProjectId) {
        Objects.requireNonNull(salesProjectId, "salesProjectId must not be null");
        return transactions.executeReadOnly(
                () -> proposals.findAllBySalesProjectId(salesProjectId));
    }
}
