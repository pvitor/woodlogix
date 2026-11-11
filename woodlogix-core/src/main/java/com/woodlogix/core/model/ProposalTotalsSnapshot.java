package com.woodlogix.core.model;

import com.woodlogix.core.enums.CurrencyCode;

import java.util.Objects;

public final class ProposalTotalsSnapshot {
    private final Money subtotal;
    private final Money individualDiscountTotal;
    private final Money generalDiscountTotal;
    private final Money taxableBase;
    private final Money taxTotal;
    private final Money finalTotal;

    public ProposalTotalsSnapshot(
            Money subtotal,
            Money individualDiscountTotal,
            Money generalDiscountTotal,
            Money taxableBase,
            Money taxTotal,
            Money finalTotal) {
        this.subtotal = requireNonNegative(subtotal, "subtotal");
        this.individualDiscountTotal = requireNonNegative(
                individualDiscountTotal, "individualDiscountTotal");
        this.generalDiscountTotal = requireNonNegative(
                generalDiscountTotal, "generalDiscountTotal");
        this.taxableBase = requireNonNegative(taxableBase, "taxableBase");
        this.taxTotal = requireNonNegative(taxTotal, "taxTotal");
        this.finalTotal = requireNonNegative(finalTotal, "finalTotal");
        CurrencyCode currency = subtotal.getCurrency();
        if (individualDiscountTotal.getCurrency() != currency
                || generalDiscountTotal.getCurrency() != currency
                || taxableBase.getCurrency() != currency
                || taxTotal.getCurrency() != currency
                || finalTotal.getCurrency() != currency) {
            throw new IllegalArgumentException("Proposal totals must use the same currency");
        }
    }

    public Money getSubtotal() {
        return subtotal;
    }

    public Money getIndividualDiscountTotal() {
        return individualDiscountTotal;
    }

    public Money getGeneralDiscountTotal() {
        return generalDiscountTotal;
    }

    public Money getTaxableBase() {
        return taxableBase;
    }

    public Money getTaxTotal() {
        return taxTotal;
    }

    public Money getFinalTotal() {
        return finalTotal;
    }

    private static Money requireNonNegative(Money value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.getAmount().signum() < 0) {
            throw new IllegalArgumentException(field + " must not be negative");
        }
        return value;
    }
}
