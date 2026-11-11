package com.woodlogix.core.model;

import java.math.BigDecimal;
import java.util.Objects;

public final class ProposalDraftItem {
    private final ProposalLineId lineId;
    private final String description;
    private final BigDecimal quantity;
    private final UnitOfMeasure unit;
    private final Money unitPriceBeforeDiscountsAndTax;
    private final DiscountInput individualDiscount;
    private final TaxInput tax;
    private final CatalogReference catalogReference;
    private final InternalCostBreakdown directCosts;

    public ProposalDraftItem(
            ProposalLineId lineId,
            String description,
            BigDecimal quantity,
            UnitOfMeasure unit,
            Money unitPriceBeforeDiscountsAndTax,
            DiscountInput individualDiscount,
            TaxInput tax,
            CatalogReference catalogReference,
            InternalCostBreakdown directCosts) {
        this.lineId = Objects.requireNonNull(lineId, "lineId must not be null");
        this.description = requireText(description, "description");
        this.quantity = Objects.requireNonNull(quantity, "quantity must not be null");
        if (quantity.signum() <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        this.unit = Objects.requireNonNull(unit, "unit must not be null");
        this.unitPriceBeforeDiscountsAndTax = requireNonNegative(
                unitPriceBeforeDiscountsAndTax, "unitPriceBeforeDiscountsAndTax");
        this.individualDiscount = individualDiscount;
        this.tax = Objects.requireNonNull(tax, "tax must not be null");
        this.catalogReference = catalogReference;
        this.directCosts = directCosts;
        if (directCosts != null
                && directCosts.getTotal().getCurrency()
                != unitPriceBeforeDiscountsAndTax.getCurrency()) {
            throw new IllegalArgumentException("Direct costs must use the same currency");
        }
        if (individualDiscount instanceof FixedDiscountInput fixed
                && fixed.getAmount().getCurrency()
                != unitPriceBeforeDiscountsAndTax.getCurrency()) {
            throw new IllegalArgumentException("Discount values must use the same currency");
        }
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

    public DiscountInput getIndividualDiscount() {
        return individualDiscount;
    }

    public TaxInput getTax() {
        return tax;
    }

    public CatalogReference getCatalogReference() {
        return catalogReference;
    }

    public InternalCostBreakdown getDirectCosts() {
        return directCosts;
    }

    private static Money requireNonNegative(Money value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.getAmount().signum() < 0) {
            throw new IllegalArgumentException(field + " must not be negative");
        }
        return value;
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
