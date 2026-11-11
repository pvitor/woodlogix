package com.woodlogix.core.model;

import com.woodlogix.core.enums.CurrencyCode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ProposalSnapshot {
    private final ProposalId proposalId;
    private final ProposalVersionNumber versionNumber;
    private final SalesProjectId salesProjectId;
    private final CustomerSnapshot customer;
    private final RecipientSnapshot recipient;
    private final CurrencyCode currency;
    private final ManualConversion conversion;
    private final List<ProposalItemSnapshot> items;
    private final DiscountSnapshot generalDiscount;
    private final ProposalTotalsSnapshot totals;
    private final InternalCostBreakdown generalCosts;
    private final CommercialTermsSnapshot commercialTerms;
    private final LocalDate issuedOn;
    private final ValidityPeriod validityPeriod;
    private final LocalDate validUntil;

    public ProposalSnapshot(
            ProposalId proposalId,
            ProposalVersionNumber versionNumber,
            SalesProjectId salesProjectId,
            CustomerSnapshot customer,
            RecipientSnapshot recipient,
            CurrencyCode currency,
            ManualConversion conversion,
            List<ProposalItemSnapshot> items,
            DiscountSnapshot generalDiscount,
            ProposalTotalsSnapshot totals,
            InternalCostBreakdown generalCosts,
            CommercialTermsSnapshot commercialTerms,
            LocalDate issuedOn,
            ValidityPeriod validityPeriod,
            LocalDate validUntil) {
        this.proposalId = Objects.requireNonNull(proposalId, "proposalId must not be null");
        this.versionNumber = Objects.requireNonNull(
                versionNumber, "versionNumber must not be null");
        this.salesProjectId = Objects.requireNonNull(
                salesProjectId, "salesProjectId must not be null");
        this.customer = Objects.requireNonNull(customer, "customer must not be null");
        this.recipient = recipient;
        this.currency = Objects.requireNonNull(currency, "currency must not be null");
        this.conversion = conversion;
        this.items = copyItems(items, currency);
        this.generalDiscount = validateDiscountCurrency(generalDiscount, currency);
        this.totals = Objects.requireNonNull(totals, "totals must not be null");
        if (totals.getFinalTotal().getCurrency() != currency) {
            throw new IllegalArgumentException("Totals must use snapshot currency");
        }
        this.generalCosts = generalCosts;
        if (generalCosts != null && generalCosts.getTotal().getCurrency() != currency) {
            throw new IllegalArgumentException("General costs must use snapshot currency");
        }
        this.commercialTerms = Objects.requireNonNull(
                commercialTerms, "commercialTerms must not be null");
        this.issuedOn = Objects.requireNonNull(issuedOn, "issuedOn must not be null");
        this.validityPeriod = Objects.requireNonNull(
                validityPeriod, "validityPeriod must not be null");
        this.validUntil = Objects.requireNonNull(validUntil, "validUntil must not be null");
        if (validUntil.isBefore(issuedOn)) {
            throw new IllegalArgumentException("validUntil must not be before issuedOn");
        }
    }

    public ProposalId getProposalId() {
        return proposalId;
    }

    public ProposalVersionNumber getVersionNumber() {
        return versionNumber;
    }

    public SalesProjectId getSalesProjectId() {
        return salesProjectId;
    }

    public CustomerSnapshot getCustomer() {
        return customer;
    }

    public RecipientSnapshot getRecipient() {
        return recipient;
    }

    public CurrencyCode getCurrency() {
        return currency;
    }

    public ManualConversion getConversion() {
        return conversion;
    }

    public List<ProposalItemSnapshot> getItems() {
        return items;
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

    public CommercialTermsSnapshot getCommercialTerms() {
        return commercialTerms;
    }

    public LocalDate getIssuedOn() {
        return issuedOn;
    }

    public ValidityPeriod getValidityPeriod() {
        return validityPeriod;
    }

    public LocalDate getValidUntil() {
        return validUntil;
    }

    private static List<ProposalItemSnapshot> copyItems(
            List<ProposalItemSnapshot> values, CurrencyCode currency) {
        Objects.requireNonNull(values, "items must not be null");
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Snapshot must contain at least one item");
        }
        List<ProposalItemSnapshot> copied = new ArrayList<>(values.size());
        for (ProposalItemSnapshot item : values) {
            Objects.requireNonNull(item, "items must not contain null");
            if (item.getUnitPriceBeforeDiscountsAndTax().getCurrency() != currency) {
                throw new IllegalArgumentException("Snapshot item values must use snapshot currency");
            }
            if (copied.stream().anyMatch(
                    current -> current.getLineId().equals(item.getLineId()))) {
                throw new IllegalArgumentException("Snapshot line IDs must be unique");
            }
            copied.add(item);
        }
        return List.copyOf(copied);
    }

    private static DiscountSnapshot validateDiscountCurrency(
            DiscountSnapshot discount, CurrencyCode currency) {
        if (discount == null) {
            return null;
        }
        CurrencyCode discountCurrency;
        if (discount instanceof FixedDiscountSnapshot fixed) {
            discountCurrency = fixed.getAppliedAmount().getCurrency();
        } else {
            discountCurrency = ((PercentageDiscountSnapshot) discount)
                    .getAppliedAmount().getCurrency();
        }
        if (discountCurrency != currency) {
            throw new IllegalArgumentException("Discount values must use snapshot currency");
        }
        return discount;
    }
}
