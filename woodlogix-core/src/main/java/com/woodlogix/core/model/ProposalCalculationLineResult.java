package com.woodlogix.core.model;

import com.woodlogix.core.enums.CurrencyCode;

import java.util.Objects;

public final class ProposalCalculationLineResult {
    private final ProposalLineId lineId;
    private final Money lineSubtotal;
    private final DiscountSnapshot individualDiscount;
    private final Money generalDiscountAllocation;
    private final Money taxableBase;
    private final TaxSnapshot tax;
    private final Money finalTotal;

    public ProposalCalculationLineResult(
            ProposalLineId lineId,
            Money lineSubtotal,
            DiscountSnapshot individualDiscount,
            Money generalDiscountAllocation,
            Money taxableBase,
            TaxSnapshot tax,
            Money finalTotal) {
        this.lineId = Objects.requireNonNull(lineId, "lineId must not be null");
        this.lineSubtotal = requireNonNegative(lineSubtotal, "lineSubtotal");
        this.individualDiscount = individualDiscount;
        this.generalDiscountAllocation = requireNonNegative(
                generalDiscountAllocation, "generalDiscountAllocation");
        this.taxableBase = requireNonNegative(taxableBase, "taxableBase");
        this.tax = Objects.requireNonNull(tax, "tax must not be null");
        this.finalTotal = requireNonNegative(finalTotal, "finalTotal");
        requireSameCurrency();
    }

    public ProposalLineId getLineId() {
        return lineId;
    }

    public Money getLineSubtotal() {
        return lineSubtotal;
    }

    public DiscountSnapshot getIndividualDiscount() {
        return individualDiscount;
    }

    public Money getGeneralDiscountAllocation() {
        return generalDiscountAllocation;
    }

    public Money getTaxableBase() {
        return taxableBase;
    }

    public TaxSnapshot getTax() {
        return tax;
    }

    public Money getFinalTotal() {
        return finalTotal;
    }

    private void requireSameCurrency() {
        CurrencyCode currency = lineSubtotal.getCurrency();
        if (generalDiscountAllocation.getCurrency() != currency
                || taxableBase.getCurrency() != currency
                || finalTotal.getCurrency() != currency
                || tax.getTaxableBase().getCurrency() != currency
                || tax.getTaxAmount().getCurrency() != currency) {
            throw new IllegalArgumentException("Calculation values must use the same currency");
        }
        if (individualDiscount != null
                && discountCurrency(individualDiscount) != currency) {
            throw new IllegalArgumentException("Discount values must use the same currency");
        }
    }

    private static CurrencyCode discountCurrency(DiscountSnapshot discount) {
        if (discount instanceof FixedDiscountSnapshot fixed) {
            return fixed.getAppliedAmount().getCurrency();
        }
        return ((PercentageDiscountSnapshot) discount)
                .getAppliedAmount().getCurrency();
    }

    private static Money requireNonNegative(Money value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.getAmount().signum() < 0) {
            throw new IllegalArgumentException(field + " must not be negative");
        }
        return value;
    }
}
