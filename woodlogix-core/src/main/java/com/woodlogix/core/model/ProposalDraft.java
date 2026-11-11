package com.woodlogix.core.model;

import com.woodlogix.core.enums.CurrencyCode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ProposalDraft {
    private final ProposalDraftId draftId;
    private final ProposalId proposalId;
    private final SalesProjectId salesProjectId;
    private final ProposalVersionNumber sourceVersionNumber;
    private final ProposalDraftRevision revision;
    private final ProposalDraftStatus status;
    private final CustomerSnapshot customer;
    private final RecipientSnapshot recipient;
    private final CurrencyCode currency;
    private final ManualConversion conversion;
    private final List<ProposalDraftItem> items;
    private final DiscountInput generalDiscount;
    private final ValidityPeriod validityPeriod;
    private final CommercialTermsDraft commercialTerms;

    public ProposalDraft(
            ProposalDraftId draftId,
            ProposalId proposalId,
            SalesProjectId salesProjectId,
            ProposalVersionNumber sourceVersionNumber,
            ProposalDraftStatus status,
            CustomerSnapshot customer,
            RecipientSnapshot recipient,
            CurrencyCode currency,
            ManualConversion conversion,
            List<ProposalDraftItem> items,
            DiscountInput generalDiscount,
            ValidityPeriod validityPeriod,
            CommercialTermsDraft commercialTerms) {
        this(
                draftId,
                proposalId,
                salesProjectId,
                sourceVersionNumber,
                ProposalDraftRevision.initial(),
                status,
                customer,
                recipient,
                currency,
                conversion,
                items,
                generalDiscount,
                validityPeriod,
                commercialTerms);
    }

    public ProposalDraft(
            ProposalDraftId draftId,
            ProposalId proposalId,
            SalesProjectId salesProjectId,
            ProposalVersionNumber sourceVersionNumber,
            ProposalDraftRevision revision,
            ProposalDraftStatus status,
            CustomerSnapshot customer,
            RecipientSnapshot recipient,
            CurrencyCode currency,
            ManualConversion conversion,
            List<ProposalDraftItem> items,
            DiscountInput generalDiscount,
            ValidityPeriod validityPeriod,
            CommercialTermsDraft commercialTerms) {
        this.draftId = Objects.requireNonNull(draftId, "draftId must not be null");
        this.proposalId = Objects.requireNonNull(proposalId, "proposalId must not be null");
        this.salesProjectId = Objects.requireNonNull(
                salesProjectId, "salesProjectId must not be null");
        this.sourceVersionNumber = sourceVersionNumber;
        this.revision = Objects.requireNonNull(revision, "revision must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.customer = Objects.requireNonNull(customer, "customer must not be null");
        this.recipient = recipient;
        this.currency = Objects.requireNonNull(currency, "currency must not be null");
        this.conversion = conversion;
        this.items = copyItems(items, currency);
        this.generalDiscount = validateDiscountCurrency(generalDiscount, currency);
        this.validityPeriod = Objects.requireNonNull(
                validityPeriod, "validityPeriod must not be null");
        this.commercialTerms = Objects.requireNonNull(
                commercialTerms, "commercialTerms must not be null");
    }

    public ProposalDraftId getDraftId() {
        return draftId;
    }

    public ProposalId getProposalId() {
        return proposalId;
    }

    public SalesProjectId getSalesProjectId() {
        return salesProjectId;
    }

    public ProposalVersionNumber getSourceVersionNumber() {
        return sourceVersionNumber;
    }

    public ProposalDraftRevision getRevision() {
        return revision;
    }

    public ProposalDraftStatus getStatus() {
        return status;
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

    public List<ProposalDraftItem> getItems() {
        return items;
    }

    public DiscountInput getGeneralDiscount() {
        return generalDiscount;
    }

    public ValidityPeriod getValidityPeriod() {
        return validityPeriod;
    }

    public CommercialTermsDraft getCommercialTerms() {
        return commercialTerms;
    }

    public ProposalDraft addItem(ProposalDraftItem item) {
        ensureEditable();
        Objects.requireNonNull(item, "item must not be null");
        ensureUniqueLineId(item, items);
        List<ProposalDraftItem> updated = new ArrayList<>(items);
        updated.add(item);
        return withItemsInternal(updated, revision.next());
    }

    public ProposalDraft replaceItem(ProposalDraftItem item) {
        ensureEditable();
        Objects.requireNonNull(item, "item must not be null");
        List<ProposalDraftItem> updated = new ArrayList<>(items);
        int index = indexOf(item.getLineId(), updated);
        if (index < 0) {
            throw new IllegalArgumentException("Draft item does not exist");
        }
        if (sameItem(updated.get(index), item)) {
            return this;
        }
        updated.set(index, item);
        return withItemsInternal(updated, revision.next());
    }

    public ProposalDraft removeItem(ProposalLineId lineId) {
        ensureEditable();
        Objects.requireNonNull(lineId, "lineId must not be null");
        List<ProposalDraftItem> updated = new ArrayList<>(items);
        int index = indexOf(lineId, updated);
        if (index < 0) {
            throw new IllegalArgumentException("Draft item does not exist");
        }
        updated.remove(index);
        return withItemsInternal(updated, revision.next());
    }

    public ProposalDraft withItems(List<ProposalDraftItem> updatedItems) {
        ensureEditable();
        Objects.requireNonNull(updatedItems, "items must not be null");
        if (sameItems(updatedItems)) {
            return this;
        }
        return withItemsInternal(updatedItems, revision.next());
    }

    public ProposalDraft withCustomer(CustomerSnapshot updatedCustomer) {
        ensureEditable();
        if (sameCustomer(customer, updatedCustomer)) {
            return this;
        }
        return copy(updatedCustomer, recipient, conversion, items, generalDiscount,
                validityPeriod, commercialTerms, status, revision.next());
    }

    public ProposalDraft withRecipient(RecipientSnapshot updatedRecipient) {
        ensureEditable();
        if (sameRecipient(recipient, updatedRecipient)) {
            return this;
        }
        return copy(customer, updatedRecipient, conversion, items, generalDiscount,
                validityPeriod, commercialTerms, status, revision.next());
    }

    public ProposalDraft withConversion(ManualConversion updatedConversion) {
        ensureEditable();
        if (sameConversion(conversion, updatedConversion)) {
            return this;
        }
        return copy(customer, recipient, updatedConversion, items, generalDiscount,
                validityPeriod, commercialTerms, status, revision.next());
    }

    public ProposalDraft withGeneralDiscount(DiscountInput updatedDiscount) {
        ensureEditable();
        if (sameDiscountInput(generalDiscount, updatedDiscount)) {
            return this;
        }
        return copy(customer, recipient, conversion, items, updatedDiscount,
                validityPeriod, commercialTerms, status, revision.next());
    }

    public ProposalDraft withValidityPeriod(ValidityPeriod updatedValidityPeriod) {
        ensureEditable();
        if (Objects.equals(validityPeriod, updatedValidityPeriod)) {
            return this;
        }
        return copy(customer, recipient, conversion, items, generalDiscount,
                updatedValidityPeriod, commercialTerms, status, revision.next());
    }

    public ProposalDraft withCommercialTerms(CommercialTermsDraft updatedCommercialTerms) {
        ensureEditable();
        if (sameTerms(commercialTerms, updatedCommercialTerms)) {
            return this;
        }
        return copy(customer, recipient, conversion, items, generalDiscount,
                validityPeriod, updatedCommercialTerms, status, revision.next());
    }

    public ProposalDraft lock() {
        if (status == ProposalDraftStatus.LOCKED) {
            return this;
        }
        return copy(customer, recipient, conversion, items, generalDiscount,
                validityPeriod, commercialTerms, ProposalDraftStatus.LOCKED, revision);
    }

    private ProposalDraft withItemsInternal(
            List<ProposalDraftItem> updatedItems, ProposalDraftRevision updatedRevision) {
        return copy(customer, recipient, conversion, updatedItems, generalDiscount,
                validityPeriod, commercialTerms, status, updatedRevision);
    }

    private ProposalDraft copy(
            CustomerSnapshot updatedCustomer,
            RecipientSnapshot updatedRecipient,
            ManualConversion updatedConversion,
            List<ProposalDraftItem> updatedItems,
            DiscountInput updatedDiscount,
            ValidityPeriod updatedValidityPeriod,
            CommercialTermsDraft updatedCommercialTerms,
            ProposalDraftStatus updatedStatus,
            ProposalDraftRevision updatedRevision) {
        return new ProposalDraft(
                draftId,
                proposalId,
                salesProjectId,
                sourceVersionNumber,
                updatedRevision,
                updatedStatus,
                updatedCustomer,
                updatedRecipient,
                currency,
                updatedConversion,
                updatedItems,
                updatedDiscount,
                updatedValidityPeriod,
                updatedCommercialTerms);
    }

    private boolean sameItems(List<ProposalDraftItem> updatedItems) {
        if (updatedItems.size() != items.size()) {
            return false;
        }
        for (int index = 0; index < items.size(); index++) {
            if (!sameItem(items.get(index), updatedItems.get(index))) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameItem(ProposalDraftItem first, ProposalDraftItem second) {
        if (first == second) {
            return true;
        }
        if (first == null || second == null) {
            return false;
        }
        return same(first.getLineId().getValue(), second.getLineId().getValue())
                && same(first.getDescription(), second.getDescription())
                && same(first.getQuantity(), second.getQuantity())
                && same(first.getUnit().getCode(), second.getUnit().getCode())
                && same(first.getUnitPriceBeforeDiscountsAndTax(),
                second.getUnitPriceBeforeDiscountsAndTax())
                && sameDiscountInput(
                first.getIndividualDiscount(), second.getIndividualDiscount())
                && sameTax(first.getTax(), second.getTax())
                && sameCatalog(first.getCatalogReference(), second.getCatalogReference())
                && sameCosts(first.getDirectCosts(), second.getDirectCosts());
    }

    private static boolean sameCustomer(
            CustomerSnapshot first, CustomerSnapshot second) {
        return first == second
                || first != null
                && second != null
                && same(first.getCustomerId(), second.getCustomerId())
                && same(first.getDisplayName(), second.getDisplayName());
    }

    private static boolean sameRecipient(
            RecipientSnapshot first, RecipientSnapshot second) {
        return first == second
                || first != null
                && second != null
                && same(first.getDisplayName(), second.getDisplayName())
                && sameContact(first.getContact(), second.getContact());
    }

    private static boolean sameContact(ContactSnapshot first, ContactSnapshot second) {
        return first == second
                || first != null
                && second != null
                && same(first.getDisplayName(), second.getDisplayName())
                && same(first.getPhone(), second.getPhone())
                && same(first.getEmail(), second.getEmail());
    }

    private static boolean sameConversion(
            ManualConversion first, ManualConversion second) {
        return first == second
                || first != null
                && second != null
                && same(first.getUyuPerUsd(), second.getUyuPerUsd());
    }

    private static boolean sameDiscountInput(
            DiscountInput first, DiscountInput second) {
        if (first == second) {
            return true;
        }
        if (first == null || second == null || first.getClass() != second.getClass()) {
            return false;
        }
        if (first instanceof PercentageDiscountInput firstPercentage
                && second instanceof PercentageDiscountInput secondPercentage) {
            return firstPercentage.getPercentage()
                    .compareTo(secondPercentage.getPercentage()) == 0;
        }
        FixedDiscountInput firstFixed = (FixedDiscountInput) first;
        FixedDiscountInput secondFixed = (FixedDiscountInput) second;
        return same(firstFixed.getAmount(), secondFixed.getAmount());
    }

    private static boolean sameTax(TaxInput first, TaxInput second) {
        return first == second
                || first != null
                && second != null
                && first.getMode() == second.getMode()
                && same(first.getRate(), second.getRate());
    }

    private static boolean sameCatalog(
            CatalogReference first, CatalogReference second) {
        return first == second
                || first != null
                && second != null
                && first.getCatalogType() == second.getCatalogType()
                && same(first.getCatalogId(), second.getCatalogId());
    }

    private static boolean sameCosts(
            InternalCostBreakdown first, InternalCostBreakdown second) {
        return first == second
                || first != null
                && second != null
                && same(first.getMaterials(), second.getMaterials())
                && same(first.getEdgeBanding(), second.getEdgeBanding())
                && same(first.getHardware(), second.getHardware())
                && same(first.getLabor(), second.getLabor())
                && same(first.getMachines(), second.getMachines())
                && same(first.getExtras(), second.getExtras());
    }

    private static boolean sameTerms(
            CommercialTermsDraft first, CommercialTermsDraft second) {
        return first == second
                || first != null
                && second != null
                && same(first.getPaymentTerms().getDescription(),
                second.getPaymentTerms().getDescription())
                && sameDescription(first.getLeadTime(), second.getLeadTime())
                && sameDescription(first.getTransportTerms(), second.getTransportTerms())
                && sameDescription(first.getInstallationTerms(), second.getInstallationTerms())
                && same(first.getNotes(), second.getNotes());
    }

    private static boolean sameDescription(Object first, Object second) {
        if (first == second) {
            return true;
        }
        if (first == null || second == null) {
            return false;
        }
        if (first instanceof LeadTime firstLeadTime
                && second instanceof LeadTime secondLeadTime) {
            return same(firstLeadTime.getDescription(), secondLeadTime.getDescription());
        }
        if (first instanceof TransportTerms firstTransport
                && second instanceof TransportTerms secondTransport) {
            return same(firstTransport.getDescription(), secondTransport.getDescription());
        }
        if (first instanceof InstallationTerms firstInstallation
                && second instanceof InstallationTerms secondInstallation) {
            return same(firstInstallation.getDescription(),
                    secondInstallation.getDescription());
        }
        return false;
    }

    private static boolean same(Money first, Money second) {
        return first == second
                || first != null
                && second != null
                && first.getCurrency() == second.getCurrency()
                && first.getAmount().compareTo(second.getAmount()) == 0;
    }

    private static boolean same(Object first, Object second) {
        return first == second
                || (first != null
                && second != null
                && first instanceof String firstString
                && second instanceof String secondString
                && firstString.equals(secondString))
                || (first instanceof BigDecimal firstDecimal
                && second instanceof BigDecimal secondDecimal
                && firstDecimal.compareTo(secondDecimal) == 0);
    }

    private void ensureEditable() {
        if (status != ProposalDraftStatus.EDITABLE) {
            throw new IllegalStateException("Locked drafts cannot be changed");
        }
    }

    private static List<ProposalDraftItem> copyItems(
            List<ProposalDraftItem> values, CurrencyCode currency) {
        Objects.requireNonNull(values, "items must not be null");
        List<ProposalDraftItem> copied = new ArrayList<>(values.size());
        for (ProposalDraftItem item : values) {
            Objects.requireNonNull(item, "items must not contain null");
            ensureItemCurrency(item, currency);
            ensureUniqueLineId(item, copied);
            copied.add(item);
        }
        return List.copyOf(copied);
    }

    private static void ensureUniqueLineId(
            ProposalDraftItem item, List<ProposalDraftItem> existing) {
        for (ProposalDraftItem current : existing) {
            if (current.getLineId().equals(item.getLineId())) {
                throw new IllegalArgumentException("Draft line IDs must be unique");
            }
        }
    }

    private static int indexOf(ProposalLineId lineId, List<ProposalDraftItem> values) {
        for (int index = 0; index < values.size(); index++) {
            if (values.get(index).getLineId().equals(lineId)) {
                return index;
            }
        }
        return -1;
    }

    private static void ensureItemCurrency(ProposalDraftItem item, CurrencyCode currency) {
        if (item.getUnitPriceBeforeDiscountsAndTax().getCurrency() != currency) {
            throw new IllegalArgumentException("Draft item values must use draft currency");
        }
    }

    private static DiscountInput validateDiscountCurrency(
            DiscountInput discount, CurrencyCode currency) {
        if (discount instanceof FixedDiscountInput fixed
                && fixed.getAmount().getCurrency() != currency) {
            throw new IllegalArgumentException("Discount values must use draft currency");
        }
        return discount;
    }
}
