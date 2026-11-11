package com.woodlogix.core.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ProposalCalculationResult {
    private final ProposalId proposalId;
    private final ProposalDraftId draftId;
    private final ProposalDraftRevision draftRevision;
    private final List<ProposalCalculationLineResult> lineResults;
    private final DiscountSnapshot generalDiscount;
    private final ProposalTotalsSnapshot totals;
    private final InternalCostBreakdown generalCosts;

    public ProposalCalculationResult(
            ProposalId proposalId,
            ProposalDraftId draftId,
            ProposalDraftRevision draftRevision,
            List<ProposalCalculationLineResult> lineResults,
            DiscountSnapshot generalDiscount,
            ProposalTotalsSnapshot totals,
            InternalCostBreakdown generalCosts) {
        this.proposalId = Objects.requireNonNull(proposalId, "proposalId must not be null");
        this.draftId = Objects.requireNonNull(draftId, "draftId must not be null");
        this.draftRevision = Objects.requireNonNull(
                draftRevision, "draftRevision must not be null");
        this.lineResults = copyLineResults(lineResults);
        this.generalDiscount = generalDiscount;
        this.totals = Objects.requireNonNull(totals, "totals must not be null");
        this.generalCosts = generalCosts;
    }

    public ProposalId getProposalId() {
        return proposalId;
    }

    public ProposalDraftId getDraftId() {
        return draftId;
    }

    public ProposalDraftRevision getDraftRevision() {
        return draftRevision;
    }

    public List<ProposalCalculationLineResult> getLineResults() {
        return lineResults;
    }

    public DiscountSnapshot getGeneralDiscount() {
        return generalDiscount;
    }

    public ProposalTotalsSnapshot getTotals() {
        return totals;
    }

    public InternalCostBreakdown getGeneralCosts() {
        return generalCosts;
    }

    private static List<ProposalCalculationLineResult> copyLineResults(
            List<ProposalCalculationLineResult> values) {
        Objects.requireNonNull(values, "lineResults must not be null");
        List<ProposalCalculationLineResult> copied = new ArrayList<>(values.size());
        for (ProposalCalculationLineResult value : values) {
            Objects.requireNonNull(value, "lineResults must not contain null");
            if (copied.stream().anyMatch(
                    current -> current.getLineId().equals(value.getLineId()))) {
                throw new IllegalArgumentException("Calculation line IDs must be unique");
            }
            copied.add(value);
        }
        return List.copyOf(copied);
    }
}
