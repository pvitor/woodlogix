package com.woodlogix.core.model;

import com.woodlogix.core.enums.CurrencyCode;

import java.math.BigDecimal;
import java.util.Objects;

public final class ProposalItemSnapshot {
    private final ProposalLineId lineId;
    private final String description;
    private final BigDecimal quantity;
    private final UnitOfMeasure unit;
    private final Money unitPriceBeforeDiscountsAndTax;
    private final Money lineSubtotal;
    private final DiscountSnapshot individualDiscount;
    private final Money generalDiscountAllocation;
    private final Money taxableBase;
    private final TaxSnapshot tax;
    private final Money finalTotal;
    private final CatalogReference catalogReference;
    private final InternalCostBreakdown directCosts;

    public ProposalItemSnapshot(
            ProposalLineId lineId,
            String description,
            BigDecimal quantity,
            UnitOfMeasure unit,
            Money unitPriceBeforeDiscountsAndTax,
            Money lineSubtotal,
            DiscountSnapshot individualDiscount,
            Money generalDiscountAllocation,
            Money taxableBase,
            TaxSnapshot tax,
            Money finalTotal,
            CatalogReference catalogReference
    ) {
        this(
                lineId,
                description,
                quantity,
                unit,
                unitPriceBeforeDiscountsAndTax,
                lineSubtotal,
                individualDiscount,
                generalDiscountAllocation,
                taxableBase,
                tax,
                finalTotal,
                catalogReference,
                null);
    }

    public ProposalItemSnapshot(
            ProposalLineId lineId,
            String description,
            BigDecimal quantity,
            UnitOfMeasure unit,
            Money unitPriceBeforeDiscountsAndTax,
            Money lineSubtotal,
            DiscountSnapshot individualDiscount,
            Money generalDiscountAllocation,
            Money taxableBase,
            TaxSnapshot tax,
            Money finalTotal,
            CatalogReference catalogReference,
            InternalCostBreakdown directCosts
    ) {
        this.lineId = Objects.requireNonNull(lineId, "lineId must not be null");
        this.description = requireText(description, "description");
        this.quantity = Objects.requireNonNull(quantity, "quantity must not be null");
        if (quantity.signum() <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        this.unit = Objects.requireNonNull(unit, "unit must not be null");
        this.unitPriceBeforeDiscountsAndTax = requireMoney(
                unitPriceBeforeDiscountsAndTax, "unitPriceBeforeDiscountsAndTax");
        this.lineSubtotal = requireMoney(lineSubtotal, "lineSubtotal");
        this.individualDiscount = individualDiscount;
        this.generalDiscountAllocation = requireMoney(
                generalDiscountAllocation, "generalDiscountAllocation");
        this.taxableBase = requireMoney(taxableBase, "taxableBase");
        this.tax = Objects.requireNonNull(tax, "tax must not be null");
        this.finalTotal = requireMoney(finalTotal, "finalTotal");
        this.catalogReference = catalogReference;
        this.directCosts = directCosts;
        requireSameCurrency();
    }

    public ProposalLineId getLineId() {
        return lineId;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public UnitOfMeasure getUnit() {
        return unit;
    }

    public Money getUnitPriceBeforeDiscountsAndTax() {
        return unitPriceBeforeDiscountsAndTax;
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

    public CatalogReference getCatalogReference() {
        return catalogReference;
    }

    public InternalCostBreakdown getDirectCosts() {
        return directCosts;
    }

    private static Money requireMoney(Money value, String field) {
        return Objects.requireNonNull(value, field + " must not be null");
    }

    private void requireSameCurrency() {
        CurrencyCode currency = unitPriceBeforeDiscountsAndTax.getCurrency();
        if (lineSubtotal.getCurrency() != currency
                || generalDiscountAllocation.getCurrency() != currency
                || taxableBase.getCurrency() != currency
                || finalTotal.getCurrency() != currency) {
            throw new IllegalArgumentException("Item values must use the same currency");
        }
        if (individualDiscount != null
                && discountCurrency(individualDiscount) != currency) {
            throw new IllegalArgumentException("Discount values must use the same currency");
        }
        if (tax.getTaxableBase().getCurrency() != currency
                || tax.getTaxAmount().getCurrency() != currency) {
            throw new IllegalArgumentException("Tax values must use the same currency");
        }
        if (directCosts != null && directCosts.getTotal().getCurrency() != currency) {
            throw new IllegalArgumentException("Direct costs must use the same currency");
        }
    }

    private static CurrencyCode discountCurrency(DiscountSnapshot discount) {
        if (discount instanceof FixedDiscountSnapshot fixed) {
            return fixed.getAppliedAmount().getCurrency();
        }
        return ((PercentageDiscountSnapshot) discount).getAppliedAmount().getCurrency();
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
