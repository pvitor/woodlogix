package com.woodlogix.core.model;

import com.woodlogix.core.enums.CurrencyCode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProposalTest {
    private static final CurrencyCode CURRENCY = CurrencyCode.UYU;

    @Test
    void createsProposalWithEditableInitialDraft() {
        Proposal proposal = proposalWithDraft("draft-1", List.of(draftItem("line-1")));

        assertEquals(new ProposalId("PROP-2026-0001"), proposal.getProposalId());
        assertEquals(ProposalDraftStatus.EDITABLE,
                proposal.getCurrentDraft().getStatus());
        assertEquals(ProposalDraftRevision.initial(),
                proposal.getCurrentDraftRevision());
        assertNull(proposal.getCurrentIssuedVersionNumber());
        assertEquals(List.of(), proposal.getVersionHistory());
    }

    @Test
    void incrementsDraftRevisionOnlyWhenAnEditCreatesNewContent() {
        ProposalDraft draft = newDraft("draft-1", List.of(draftItem("line-1")));

        ProposalDraft unchanged = draft.withItems(draft.getItems());
        ProposalDraft changed = draft.addItem(draftItem("line-2"));
        ProposalDraft changedAgain = changed.withValidityPeriod(ValidityPeriod.of(45));
        ProposalDraft locked = changedAgain.lock();

        assertEquals(draft.getRevision(), unchanged.getRevision());
        assertEquals(2, changed.getRevision().getValue());
        assertEquals(3, changedAgain.getRevision().getValue());
        assertEquals(changedAgain.getRevision(), locked.getRevision());
        assertThrows(IllegalStateException.class, () ->
                locked.addItem(draftItem("line-3")));
    }

    @Test
    void comparesEveryDraftEditOperationBySemanticContent() {
        ProposalDraft draft = newDraft("draft-1", List.of(draftItem("line-1")));

        ProposalDraft equivalentItems = draft.withItems(
                List.of(equivalentDraftItem("line-1")));
        assertSame(draft, equivalentItems);
        assertEquals(1, equivalentItems.getRevision().getValue());
        assertSame(draft, draft.replaceItem(equivalentDraftItem("line-1")));
        assertEquals(2, draft.replaceItem(changedDraftItem("line-1"))
                .getRevision().getValue());

        ProposalDraft customerEquivalent = draft.withCustomer(
                new CustomerSnapshot("customer-1", "Customer"));
        assertSame(draft, customerEquivalent);
        assertEquals(2, draft.withCustomer(
                new CustomerSnapshot("customer-1", "Changed")).getRevision().getValue());

        RecipientSnapshot recipient = new RecipientSnapshot(
                "Recipient", new ContactSnapshot("Contact", "123", "contact@example.com"));
        ProposalDraft withRecipient = draft.withRecipient(recipient);
        assertSame(withRecipient, withRecipient.withRecipient(new RecipientSnapshot(
                "Recipient", new ContactSnapshot("Contact", "123", "contact@example.com"))));
        assertEquals(3, withRecipient.withRecipient(
                new RecipientSnapshot("Changed", null)).getRevision().getValue());

        ProposalDraft withConversion = draft.withConversion(
                new ManualConversion(new BigDecimal("40.00")));
        assertSame(withConversion, withConversion.withConversion(
                new ManualConversion(new BigDecimal("40.000"))));
        assertEquals(3, withConversion.withConversion(
                new ManualConversion(new BigDecimal("41"))).getRevision().getValue());

        ProposalDraft withDiscount = draft.withGeneralDiscount(
                new PercentageDiscountInput(new BigDecimal("10.00")));
        assertSame(withDiscount, withDiscount.withGeneralDiscount(
                new PercentageDiscountInput(new BigDecimal("10"))));
        assertEquals(3, withDiscount.withGeneralDiscount(
                new PercentageDiscountInput(new BigDecimal("11"))).getRevision().getValue());

        ProposalDraft withTerms = draft.withCommercialTerms(termsWithNotes("note"));
        assertSame(withTerms, withTerms.withCommercialTerms(termsWithNotes("note")));
        assertEquals(3, withTerms.withCommercialTerms(
                termsWithNotes("changed")).getRevision().getValue());

        ProposalDraft withValidity = draft.withValidityPeriod(ValidityPeriod.of(30));
        assertSame(draft, withValidity);
        assertEquals(2, draft.withValidityPeriod(ValidityPeriod.of(45))
                .getRevision().getValue());
    }

    @Test
    void draftRevisionOverflowDoesNotWrapOrReuseARevision() {
        ProposalDraftRevision maximum = ProposalDraftRevision.of(Long.MAX_VALUE);

        assertThrows(IllegalStateException.class, maximum::next);
        assertEquals(Long.MAX_VALUE, maximum.getValue());
    }

    @Test
    void emitsOnlyCalculationForCurrentDraftRevision() {
        Proposal proposal = proposalWithDraft("draft-1", List.of(draftItem("line-1")));
        ProposalDraft current = proposal.getCurrentDraft();
        ProposalCalculationResult result = calculationFor(current, "line-1");

        proposal.addDraftItem(draftItem("line-2"));

        assertThrows(IllegalStateException.class, () ->
                proposal.emit(
                        current.getDraftId(),
                        current.getRevision(),
                        new ProposalVersionNumber("V01"),
                        result,
                        LocalDate.of(2026, 11, 11),
                        LocalDate.of(2026, 12, 10)));
    }

    @Test
    void emitsSnapshotFromCurrentDraftAndLocksIt() {
        Proposal proposal = proposalWithDraft("draft-1", List.of(draftItem("line-1")));
        ProposalDraft draft = proposal.getCurrentDraft();

        ProposalVersion emitted = proposal.emit(
                draft.getDraftId(),
                draft.getRevision(),
                new ProposalVersionNumber("V01"),
                calculationFor(draft, "line-1"),
                LocalDate.of(2026, 11, 11),
                LocalDate.of(2026, 12, 10));

        assertEquals(1, proposal.getDraftHistory().size());
        assertEquals(ProposalDraftStatus.LOCKED,
                proposal.getDraftHistory().get(0).getStatus());
        assertNull(proposal.getCurrentDraft());
        assertEquals(new ProposalVersionNumber("V01"),
                proposal.getCurrentIssuedVersionNumber());
        assertEquals("Item", emitted.getSnapshot().getItems().get(0).getDescription());
    }

    @Test
    void rejectsCalculationFromAnotherProposalOrWithWrongLines() {
        Proposal first = proposalWithDraft("draft-1", List.of(draftItem("line-1")));
        Proposal second = proposalWithDraft("draft-2", List.of(draftItem("line-1")));
        ProposalDraft firstDraft = first.getCurrentDraft();
        ProposalCalculationResult foreign = calculationFor(
                second.getCurrentDraft(), "line-1");

        assertThrows(IllegalArgumentException.class, () ->
                first.emit(
                        firstDraft.getDraftId(),
                        firstDraft.getRevision(),
                        new ProposalVersionNumber("V01"),
                        foreign,
                        LocalDate.of(2026, 11, 11),
                        LocalDate.of(2026, 12, 10)));

        ProposalCalculationResult missing = calculationFor(firstDraft, "other-line");
        assertThrows(IllegalArgumentException.class, () ->
                first.emit(
                        firstDraft.getDraftId(),
                        firstDraft.getRevision(),
                        new ProposalVersionNumber("V01"),
                        missing,
                        LocalDate.of(2026, 11, 11),
                        LocalDate.of(2026, 12, 10)));
    }

    @Test
    void revisionCopiesInputsButNotCalculatedResults() {
        Proposal proposal = proposalWithDraft("draft-1", List.of(draftItem("line-1")));
        ProposalDraft initial = proposal.getCurrentDraft();
        proposal.emit(
                initial.getDraftId(),
                initial.getRevision(),
                new ProposalVersionNumber("V01"),
                calculationFor(initial, "line-1"),
                LocalDate.of(2026, 11, 11),
                LocalDate.of(2026, 12, 10));

        ProposalDraft revision = proposal.startRevision(new ProposalDraftId("draft-2"));

        assertEquals(new ProposalVersionNumber("V01"),
                revision.getSourceVersionNumber());
        assertEquals(new BigDecimal("10"),
                revision.getItems().get(0).getUnitPriceBeforeDiscountsAndTax().getAmount());
        assertEquals(TaxMode.EXCLUDED, revision.getItems().get(0).getTax().getMode());
        assertNull(revision.getItems().get(0).getIndividualDiscount());
        assertEquals(ProposalDraftRevision.initial(), revision.getRevision());
    }

    @Test
    void substitutesVersionsAndAllowsTransitionsOnlyOnCurrentVersion() {
        Proposal proposal = proposalWithDraft("draft-1", List.of(draftItem("line-1")));
        emitCurrent(proposal, "V01");
        proposal.startRevision(new ProposalDraftId("draft-2"));
        emitCurrent(proposal, "V02");

        ProposalVersion first = proposal.getVersionHistory().get(0);
        assertEquals(ProposalVersionStatus.ISSUED, first.getStatus());
        assertEquals(new ProposalVersionNumber("V02"),
                first.getReplacedByVersionNumber());
        assertEquals(new ProposalVersionNumber("V02"),
                proposal.getCurrentIssuedVersionNumber());

        ProposalVersion accepted = proposal.acceptCurrent(new SaleId("sale-1"));
        assertEquals(ProposalVersionStatus.ACCEPTED, accepted.getStatus());
        assertEquals(new SaleId("sale-1"), accepted.getSaleId());
        assertEquals(new ProposalVersionNumber("V02"),
                proposal.getCurrentIssuedVersionNumber());
        assertThrows(IllegalStateException.class, () ->
                proposal.startRevision(new ProposalDraftId("draft-3")));
        assertThrows(IllegalStateException.class, () ->
                proposal.linkSale(new SaleId("sale-2")));
    }

    @Test
    void terminalTransitionsAndVersionSequenceAreEnforced() {
        Proposal proposal = proposalWithDraft("draft-1", List.of(draftItem("line-1")));
        emitCurrent(proposal, "V01");
        proposal.rejectCurrent();
        assertThrows(IllegalStateException.class, proposal::acceptCurrent);
        assertThrows(IllegalStateException.class, proposal::expireCurrent);
        assertThrows(IllegalStateException.class, proposal::withdrawCurrent);
        assertThrows(IllegalStateException.class, () ->
                proposal.startRevision(new ProposalDraftId("draft-2")));

        Proposal other = proposalWithDraft("draft-3", List.of(draftItem("line-1")));
        ProposalDraft draft = other.getCurrentDraft();
        assertThrows(IllegalArgumentException.class, () ->
                other.emit(
                        draft.getDraftId(),
                        draft.getRevision(),
                        new ProposalVersionNumber("V02"),
                        calculationFor(draft, "line-1"),
                        LocalDate.of(2026, 11, 11),
                        LocalDate.of(2026, 12, 10)));
    }

    @Test
    void acceptsWithoutSaleAndAllowsOnlyOneSaleLink() {
        Proposal proposal = proposalWithDraft("draft-1", List.of(draftItem("line-1")));
        emitCurrent(proposal, "V01");

        ProposalVersion accepted = proposal.acceptCurrent();
        assertEquals(ProposalVersionStatus.ACCEPTED, accepted.getStatus());
        assertNull(accepted.getSaleId());

        proposal.linkSale(new SaleId("sale-1"));
        assertThrows(IllegalStateException.class, () ->
                        proposal.linkSale(new SaleId("sale-2")));
    }

    @Test
    void doesNotExposeMutableAggregateHistories() {
        Proposal proposal = proposalWithDraft("draft-1", List.of(draftItem("line-1")));

        assertThrows(UnsupportedOperationException.class, () ->
                        proposal.getDraftHistory().clear());
        assertThrows(UnsupportedOperationException.class, () ->
                        proposal.getVersionHistory().clear());
    }

    @Test
    void restoresInitialProposalAndDerivesCurrentDraft() {
        Proposal original = proposalWithDraft("draft-1", List.of(draftItem("line-1")));

        Proposal restored = Proposal.restore(
                original.getProposalId(),
                original.getSalesProjectId(),
                original.getDraftHistory(),
                original.getVersionHistory());

        assertEquals(original.getCurrentDraftId(), restored.getCurrentDraftId());
        assertEquals(original.getCurrentDraftRevision(), restored.getCurrentDraftRevision());
        assertNull(restored.getCurrentIssuedVersionNumber());
        assertNotNull(restored.getCurrentDraft());
    }

    @Test
    void restoresIssuedProposalAndRevisionInProgress() {
        Proposal issued = proposalWithDraft("draft-1", List.of(draftItem("line-1")));
        emitCurrent(issued, "V01");
        ProposalDraft revision = issued.startRevision(new ProposalDraftId("draft-2"));

        Proposal restored = restoreOf(issued);

        assertEquals(revision.getDraftId(), restored.getCurrentDraftId());
        assertEquals(ProposalDraftStatus.EDITABLE,
                restored.getCurrentDraft().getStatus());
        assertEquals(new ProposalVersionNumber("V01"),
                restored.getCurrentIssuedVersionNumber());
    }

    @Test
    void restoresReplacedVersionsAndTerminalCurrentStates() {
        Proposal proposal = proposalWithDraft("draft-1", List.of(draftItem("line-1")));
        emitCurrent(proposal, "V01");
        proposal.startRevision(new ProposalDraftId("draft-2"));
        emitCurrent(proposal, "V02");

        Proposal restored = restoreOf(proposal);
        assertEquals(new ProposalVersionNumber("V02"),
                restored.getCurrentIssuedVersionNumber());
        assertEquals(new ProposalVersionNumber("V02"),
                restored.getVersionHistory().get(0).getReplacedByVersionNumber());

        proposal.acceptCurrent(new SaleId("sale-replaced"));
        Proposal acceptedReplacement = restoreOf(proposal);
        assertEquals(ProposalVersionStatus.ACCEPTED,
                acceptedReplacement.getVersionHistory().get(1).getStatus());

        Proposal accepted = proposalWithDraft("draft-3", List.of(draftItem("line-1")));
        emitCurrent(accepted, "V01");
        accepted.acceptCurrent(new SaleId("sale-1"));
        Proposal acceptedRestored = restoreOf(accepted);
        assertEquals(ProposalVersionStatus.ACCEPTED,
                acceptedRestored.getVersionHistory().get(0).getStatus());
        assertEquals(new SaleId("sale-1"),
                acceptedRestored.getVersionHistory().get(0).getSaleId());
    }

    @Test
    void restoresOtherTerminalStatesWithoutEditableDraft() {
        for (ProposalVersionStatus terminal : List.of(
                ProposalVersionStatus.REJECTED,
                ProposalVersionStatus.EXPIRED,
                ProposalVersionStatus.WITHDRAWN)) {
            Proposal proposal = proposalWithDraft(
                    "draft-" + terminal.name(),
                    List.of(draftItem("line-1")));
            emitCurrent(proposal, "V01");
            switch (terminal) {
                case REJECTED -> proposal.rejectCurrent();
                case EXPIRED -> proposal.expireCurrent();
                case WITHDRAWN -> proposal.withdrawCurrent();
                default -> throw new AssertionError("Unexpected status");
            }

            assertEquals(terminal, restoreOf(proposal)
                    .getVersionHistory().get(0).getStatus());
        }
    }

    @Test
    void restoreCopiesInputListsDefensively() {
        Proposal original = proposalWithDraft("draft-1", List.of(draftItem("line-1")));
        List<ProposalDraft> drafts = new ArrayList<>(original.getDraftHistory());
        List<ProposalVersion> versions = new ArrayList<>(original.getVersionHistory());
        Proposal restored = Proposal.restore(
                original.getProposalId(),
                original.getSalesProjectId(),
                drafts,
                versions);

        drafts.clear();
        versions.clear();

        assertEquals(1, restored.getDraftHistory().size());
        assertEquals(0, restored.getVersionHistory().size());
    }

    @Test
    void rejectsInvalidDraftHistoriesAndRelationships() {
        Proposal original = proposalWithDraft("draft-1", List.of(draftItem("line-1")));
        ProposalDraft draft = original.getCurrentDraft();

        assertThrows(IllegalArgumentException.class, () -> Proposal.restore(
                original.getProposalId(),
                original.getSalesProjectId(),
                List.of(draft, draft),
                List.of()));

        Proposal other = proposalWithIdentity(
                new ProposalId("PROP-2026-0002"), new SalesProjectId("project-2"), "draft-2");
        assertThrows(IllegalArgumentException.class, () -> Proposal.restore(
                original.getProposalId(),
                original.getSalesProjectId(),
                List.of(other.getCurrentDraft()),
                List.of()));

        Proposal emitted = proposalWithDraft("draft-3", List.of(draftItem("line-1")));
        emitCurrent(emitted, "V01");
        ProposalDraft missingSource = copyDraft(
                emitted.getDraftHistory().get(0),
                ProposalDraftStatus.EDITABLE,
                new ProposalVersionNumber("V99"));
        assertThrows(IllegalArgumentException.class, () -> Proposal.restore(
                emitted.getProposalId(),
                emitted.getSalesProjectId(),
                List.of(missingSource),
                emitted.getVersionHistory()));

        Proposal terminal = proposalWithDraft("draft-4", List.of(draftItem("line-1")));
        emitCurrent(terminal, "V01");
        terminal.rejectCurrent();
        ProposalDraft editableTerminal = copyDraft(
                terminal.getDraftHistory().get(0),
                ProposalDraftStatus.EDITABLE,
                null);
        assertThrows(IllegalArgumentException.class, () -> Proposal.restore(
                terminal.getProposalId(),
                terminal.getSalesProjectId(),
                List.of(editableTerminal),
                terminal.getVersionHistory()));
    }

    @Test
    void rejectsInvalidVersionSequencesChainsAndSnapshots() {
        Proposal proposal = proposalWithDraft("draft-1", List.of(draftItem("line-1")));
        emitCurrent(proposal, "V01");
        proposal.startRevision(new ProposalDraftId("draft-2"));
        emitCurrent(proposal, "V02");
        ProposalVersion first = proposal.getVersionHistory().get(0);
        ProposalVersion second = proposal.getVersionHistory().get(1);

        assertThrows(IllegalArgumentException.class, () -> Proposal.restore(
                proposal.getProposalId(),
                proposal.getSalesProjectId(),
                proposal.getDraftHistory(),
                List.of(second, second)));
        assertThrows(IllegalArgumentException.class, () -> Proposal.restore(
                proposal.getProposalId(),
                proposal.getSalesProjectId(),
                proposal.getDraftHistory(),
                List.of(second)));

        ProposalVersion brokenChain = new ProposalVersion(
                first.getVersionNumber(),
                first.getSnapshot(),
                ProposalVersionStatus.ISSUED,
                new ProposalVersionNumber("V03"),
                null);
        assertThrows(IllegalArgumentException.class, () -> Proposal.restore(
                proposal.getProposalId(),
                proposal.getSalesProjectId(),
                proposal.getDraftHistory(),
                List.of(brokenChain, second)));

        Proposal foreign = proposalWithIdentity(
                new ProposalId("PROP-2026-0003"), new SalesProjectId("project-3"), "draft-3");
        emitCurrent(foreign, "V01");
        assertThrows(IllegalArgumentException.class, () -> Proposal.restore(
                proposal.getProposalId(),
                proposal.getSalesProjectId(),
                proposal.getDraftHistory(),
                List.of(foreign.getVersionHistory().get(0), second)));

        ProposalVersion acceptedHistorical = new ProposalVersion(
                first.getVersionNumber(),
                first.getSnapshot(),
                ProposalVersionStatus.ACCEPTED,
                null,
                null);
        assertThrows(IllegalArgumentException.class, () -> Proposal.restore(
                proposal.getProposalId(),
                proposal.getSalesProjectId(),
                proposal.getDraftHistory(),
                List.of(acceptedHistorical, second)));
    }

    private static void emitCurrent(Proposal proposal, String version) {
        ProposalDraft draft = proposal.getCurrentDraft();
        proposal.emit(
                draft.getDraftId(),
                draft.getRevision(),
                new ProposalVersionNumber(version),
                calculationFor(draft, "line-1"),
                LocalDate.of(2026, 11, 11),
                LocalDate.of(2026, 12, 10));
    }

    private static Proposal proposalWithDraft(
            String draftId, List<ProposalDraftItem> items) {
        return proposalWithIdentity(
                new ProposalId("PROP-2026-0001"),
                new SalesProjectId("project-1"),
                draftId);
    }

    private static Proposal proposalWithIdentity(
            ProposalId proposalId, SalesProjectId salesProjectId, String draftId) {
        return new Proposal(
                proposalId,
                salesProjectId,
                newDraft(draftId, proposalId, salesProjectId,
                        null, ProposalDraftStatus.EDITABLE,
                        List.of(draftItem("line-1"))));
    }

    private static ProposalDraft newDraft(
            String draftId, List<ProposalDraftItem> items) {
        return newDraft(
                draftId,
                new ProposalId("PROP-2026-0001"),
                new SalesProjectId("project-1"),
                null,
                ProposalDraftStatus.EDITABLE,
                items);
    }

    private static ProposalDraft newDraft(
            String draftId,
            ProposalId proposalId,
            SalesProjectId salesProjectId,
            ProposalVersionNumber sourceVersionNumber,
            ProposalDraftStatus status,
            List<ProposalDraftItem> items) {
        return new ProposalDraft(
                new ProposalDraftId(draftId),
                proposalId,
                salesProjectId,
                sourceVersionNumber,
                status,
                new CustomerSnapshot("customer-1", "Customer"),
                null,
                CURRENCY,
                null,
                items,
                null,
                ValidityPeriod.of(30),
                terms());
    }

    private static Proposal restoreOf(Proposal proposal) {
        return Proposal.restore(
                proposal.getProposalId(),
                proposal.getSalesProjectId(),
                proposal.getDraftHistory(),
                proposal.getVersionHistory());
    }

    private static ProposalDraft copyDraft(
            ProposalDraft draft,
            ProposalDraftStatus status,
            ProposalVersionNumber sourceVersionNumber) {
        return new ProposalDraft(
                draft.getDraftId(),
                draft.getProposalId(),
                draft.getSalesProjectId(),
                sourceVersionNumber,
                draft.getRevision(),
                status,
                draft.getCustomer(),
                draft.getRecipient(),
                draft.getCurrency(),
                draft.getConversion(),
                draft.getItems(),
                draft.getGeneralDiscount(),
                draft.getValidityPeriod(),
                draft.getCommercialTerms());
    }

    private static ProposalDraftItem draftItem(String lineId) {
        return new ProposalDraftItem(
                new ProposalLineId(lineId),
                "Item",
                BigDecimal.ONE,
                new UnitOfMeasure("un"),
                money("10"),
                null,
                new TaxInput(TaxMode.EXCLUDED, new BigDecimal("22")),
                null,
                null);
    }

    private static ProposalDraftItem changedDraftItem(String lineId) {
        return new ProposalDraftItem(
                new ProposalLineId(lineId),
                "Changed item",
                BigDecimal.ONE,
                new UnitOfMeasure("un"),
                money("10"),
                null,
                new TaxInput(TaxMode.EXCLUDED, new BigDecimal("22")),
                null,
                null);
    }

    private static ProposalDraftItem equivalentDraftItem(String lineId) {
        return new ProposalDraftItem(
                new ProposalLineId(lineId),
                "Item",
                new BigDecimal("1.00"),
                new UnitOfMeasure("UN"),
                money("10.00"),
                null,
                new TaxInput(TaxMode.EXCLUDED, new BigDecimal("22.00")),
                null,
                null);
    }

    private static ProposalCalculationResult calculationFor(
            ProposalDraft draft, String lineId) {
        Money amount = money("10");
        ProposalCalculationLineResult line = new ProposalCalculationLineResult(
                new ProposalLineId(lineId),
                amount,
                null,
                money("0"),
                amount,
                new TaxSnapshot(
                        TaxMode.EXCLUDED,
                        new BigDecimal("22"),
                        amount,
                        money("2.2")),
                money("12.2"));
        return new ProposalCalculationResult(
                draft.getProposalId(),
                draft.getDraftId(),
                draft.getRevision(),
                List.of(line),
                null,
                new ProposalTotalsSnapshot(
                        amount,
                        money("0"),
                        money("0"),
                        amount,
                        money("2.2"),
                        money("12.2")),
                null);
    }

    private static CommercialTermsDraft terms() {
        return termsWithNotes(null);
    }

    private static CommercialTermsDraft termsWithNotes(String notes) {
        return new CommercialTermsDraft(
                new PaymentTerms("Transfer"),
                new LeadTime("30 days"),
                null,
                null,
                notes);
    }

    private static Money money(String amount) {
        return new Money(new BigDecimal(amount), CURRENCY);
    }
}
