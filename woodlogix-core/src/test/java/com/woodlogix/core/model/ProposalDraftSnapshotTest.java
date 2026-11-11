package com.woodlogix.core.model;

import com.woodlogix.core.enums.CurrencyCode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProposalDraftSnapshotTest {
    private static final CurrencyCode CURRENCY = CurrencyCode.UYU;

    @Test
    void createsEmptyEditableDraftAndKeepsItemsImmutable() {
        ProposalDraft draft = newDraft(List.of());

        assertEquals(ProposalDraftStatus.EDITABLE, draft.getStatus());
        assertEquals(List.of(), draft.getItems());
        assertThrows(UnsupportedOperationException.class, () ->
                draft.getItems().add(validDraftItem("line-2")));
    }

    @Test
    void explicitDraftOperationsReturnNewDraftsAndLockChanges() {
        ProposalDraft draft = newDraft(List.of());
        ProposalDraft withItem = draft.addItem(validDraftItem("line-1"));
        ProposalDraft locked = withItem.lock();

        assertEquals(0, draft.getItems().size());
        assertEquals(1, withItem.getItems().size());
        assertEquals(ProposalDraftStatus.LOCKED, locked.getStatus());
        assertThrows(IllegalStateException.class, () ->
                locked.addItem(validDraftItem("line-2")));
        assertThrows(IllegalStateException.class, () ->
                locked.withGeneralDiscount(null));
    }

    @Test
    void validatesDraftCurrencyAndRequiredTerms() {
        assertThrows(NullPointerException.class, () ->
                new CommercialTermsDraft(null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () ->
                newDraft(List.of(new ProposalDraftItem(
                        new ProposalLineId("line-1"),
                        "Item",
                        BigDecimal.ONE,
                        new UnitOfMeasure("un"),
                        new Money(BigDecimal.ONE, CurrencyCode.USD),
                        null,
                        new TaxInput(TaxMode.EXCLUDED, BigDecimal.ZERO),
                        null,
                        null))));
    }

    @Test
    void internalCostsDeriveTotalAndRejectDifferentCurrencies() {
        InternalCostBreakdown costs = costs(CURRENCY);

        assertEquals(new Money(new BigDecimal("21"), CURRENCY), costs.getTotal());
        assertThrows(IllegalArgumentException.class, () ->
                new InternalCostBreakdown(
                        money("1", CurrencyCode.UYU),
                        money("1", CurrencyCode.USD),
                        money("1", CurrencyCode.UYU),
                        money("1", CurrencyCode.UYU),
                        money("1", CurrencyCode.UYU),
                        money("1", CurrencyCode.UYU)));
    }

    @Test
    void rejectsSnapshotWithoutItemsAndCopiesItemList() {
        assertThrows(IllegalArgumentException.class, () ->
                newSnapshot(List.of()));

        List<ProposalItemSnapshot> source = new ArrayList<>();
        source.add(validSnapshotItem("line-1"));
        ProposalSnapshot snapshot = newSnapshot(source);
        source.clear();

        assertEquals(1, snapshot.getItems().size());
        assertThrows(UnsupportedOperationException.class, () ->
                snapshot.getItems().clear());
    }

    @Test
    void snapshotPreservesOptionalFieldsAndRejectsIncompatibleMoney() {
        ProposalItemSnapshot item = validSnapshotItem("line-1");
        ProposalSnapshot snapshot = newSnapshot(List.of(item));

        assertEquals(CURRENCY, snapshot.getCurrency());
        assertEquals(null, snapshot.getRecipient());
        assertEquals(null, snapshot.getConversion());
        assertEquals(null, snapshot.getGeneralCosts());
        assertEquals(ProposalVersionNumber.class, snapshot.getVersionNumber().getClass());

        assertThrows(IllegalArgumentException.class, () ->
                newSnapshotWithCurrency(CurrencyCode.USD, List.of(item)));
    }

    private static ProposalDraft newDraft(List<ProposalDraftItem> items) {
        return new ProposalDraft(
                new ProposalDraftId("draft-1"),
                new ProposalId("PROP-2026-0001"),
                new SalesProjectId("project-1"),
                null,
                ProposalDraftStatus.EDITABLE,
                new CustomerSnapshot("customer-1", "Customer"),
                null,
                CURRENCY,
                null,
                items,
                null,
                ValidityPeriod.of(30),
                termsDraft());
    }

    private static ProposalSnapshot newSnapshot(List<ProposalItemSnapshot> items) {
        return newSnapshotWithCurrency(CURRENCY, items);
    }

    private static ProposalSnapshot newSnapshotWithCurrency(
            CurrencyCode currency, List<ProposalItemSnapshot> items) {
        return new ProposalSnapshot(
                new ProposalId("PROP-2026-0001"),
                new ProposalVersionNumber("V01"),
                new SalesProjectId("project-1"),
                new CustomerSnapshot("customer-1", "Customer"),
                null,
                currency,
                null,
                items,
                null,
                totals(currency),
                null,
                termsSnapshot(),
                LocalDate.of(2026, 11, 11),
                ValidityPeriod.of(30),
                LocalDate.of(2026, 12, 10));
    }

    private static ProposalDraftItem validDraftItem(String lineId) {
        return new ProposalDraftItem(
                new ProposalLineId(lineId),
                "Item",
                BigDecimal.ONE,
                new UnitOfMeasure("un"),
                money("10", CURRENCY),
                null,
                new TaxInput(TaxMode.EXCLUDED, new BigDecimal("22")),
                new CatalogReference(CatalogType.MATERIAL, "material-1"),
                costs(CURRENCY));
    }

    private static ProposalItemSnapshot validSnapshotItem(String lineId) {
        Money amount = money("10", CURRENCY);
        TaxSnapshot tax = new TaxSnapshot(
                TaxMode.EXCLUDED,
                new BigDecimal("22"),
                amount,
                money("2.2", CURRENCY));
        return new ProposalItemSnapshot(
                new ProposalLineId(lineId),
                "Item",
                BigDecimal.ONE,
                new UnitOfMeasure("un"),
                amount,
                amount,
                null,
                money("0", CURRENCY),
                amount,
                tax,
                money("12.2", CURRENCY),
                null,
                costs(CURRENCY));
    }

    private static ProposalTotalsSnapshot totals(CurrencyCode currency) {
        Money zero = money("0", currency);
        return new ProposalTotalsSnapshot(
                money("10", currency),
                zero,
                zero,
                money("10", currency),
                money("2.2", currency),
                money("12.2", currency));
    }

    private static InternalCostBreakdown costs(CurrencyCode currency) {
        return new InternalCostBreakdown(
                money("1", currency),
                money("2", currency),
                money("3", currency),
                money("4", currency),
                money("5", currency),
                money("6", currency));
    }

    private static CommercialTermsDraft termsDraft() {
        return new CommercialTermsDraft(
                new PaymentTerms("Transfer"),
                new LeadTime("30 days"),
                null,
                null,
                null);
    }

    private static CommercialTermsSnapshot termsSnapshot() {
        return new CommercialTermsSnapshot(
                new PaymentTerms("Transfer"),
                new LeadTime("30 days"),
                null,
                null,
                null);
    }

    private static Money money(String amount, CurrencyCode currency) {
        return new Money(new BigDecimal(amount), currency);
    }
}
