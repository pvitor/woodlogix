package com.woodlogix.server.persistence.entity;

import com.woodlogix.core.enums.CurrencyCode;
import com.woodlogix.core.model.ProposalDraftStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "proposal_drafts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProposalDraftEntity {
    @Id
    @Column(name = "draft_id", nullable = false, length = 100)
    private String draftId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proposal_id", nullable = false)
    private ProposalEntity proposal;

    @Column(nullable = false)
    private int position;

    @Column(name = "source_version_number", length = 20)
    private String sourceVersionNumber;

    @Column(nullable = false)
    private long revision;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProposalDraftStatus status;

    @Column(name = "customer_id", nullable = false, length = 100)
    private String customerId;

    @Column(name = "customer_display_name", nullable = false)
    private String customerDisplayName;

    @Column(name = "recipient_display_name")
    private String recipientDisplayName;

    @Column(name = "recipient_contact_display_name")
    private String recipientContactDisplayName;

    @Column(name = "recipient_contact_phone")
    private String recipientContactPhone;

    @Column(name = "recipient_contact_email")
    private String recipientContactEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CurrencyCode currency;

    @Convert(converter = ProposalBigDecimalConverter.class)
    @Column(name = "conversion_uyu_per_usd", columnDefinition = "TEXT")
    private BigDecimal conversionUyuPerUsd;

    @Column(name = "general_discount_type", length = 20)
    private String generalDiscountType;

    @Convert(converter = ProposalBigDecimalConverter.class)
    @Column(name = "general_discount_percentage", columnDefinition = "TEXT")
    private BigDecimal generalDiscountPercentage;

    @Convert(converter = ProposalBigDecimalConverter.class)
    @Column(name = "general_discount_amount", columnDefinition = "TEXT")
    private BigDecimal generalDiscountAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "general_discount_currency", length = 20)
    private CurrencyCode generalDiscountCurrency;

    @Column(name = "validity_days", nullable = false)
    private int validityDays;

    @Column(name = "payment_terms", nullable = false)
    private String paymentTerms;

    @Column(name = "lead_time")
    private String leadTime;

    @Column(name = "transport_terms")
    private String transportTerms;

    @Column(name = "installation_terms")
    private String installationTerms;

    @Column(name = "commercial_notes")
    private String commercialNotes;

    @OneToMany(mappedBy = "draft", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<ProposalDraftItemEntity> items = new ArrayList<>();

    public ProposalDraftEntity(
            String draftId,
            int position,
            String sourceVersionNumber,
            long revision,
            ProposalDraftStatus status,
            String customerId,
            String customerDisplayName,
            String recipientDisplayName,
            String recipientContactDisplayName,
            String recipientContactPhone,
            String recipientContactEmail,
            CurrencyCode currency,
            BigDecimal conversionUyuPerUsd,
            String generalDiscountType,
            BigDecimal generalDiscountPercentage,
            BigDecimal generalDiscountAmount,
            CurrencyCode generalDiscountCurrency,
            int validityDays,
            String paymentTerms,
            String leadTime,
            String transportTerms,
            String installationTerms,
            String commercialNotes) {
        this.draftId = Objects.requireNonNull(draftId, "draftId must not be null");
        this.position = position;
        this.sourceVersionNumber = sourceVersionNumber;
        this.revision = revision;
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.customerId = Objects.requireNonNull(customerId, "customerId must not be null");
        this.customerDisplayName = Objects.requireNonNull(
                customerDisplayName, "customerDisplayName must not be null");
        this.recipientDisplayName = recipientDisplayName;
        this.recipientContactDisplayName = recipientContactDisplayName;
        this.recipientContactPhone = recipientContactPhone;
        this.recipientContactEmail = recipientContactEmail;
        this.currency = Objects.requireNonNull(currency, "currency must not be null");
        this.conversionUyuPerUsd = conversionUyuPerUsd;
        this.generalDiscountType = generalDiscountType;
        this.generalDiscountPercentage = generalDiscountPercentage;
        this.generalDiscountAmount = generalDiscountAmount;
        this.generalDiscountCurrency = generalDiscountCurrency;
        this.validityDays = validityDays;
        this.paymentTerms = Objects.requireNonNull(paymentTerms, "paymentTerms must not be null");
        this.leadTime = leadTime;
        this.transportTerms = transportTerms;
        this.installationTerms = installationTerms;
        this.commercialNotes = commercialNotes;
    }

    void attachTo(ProposalEntity proposal) {
        this.proposal = Objects.requireNonNull(proposal, "proposal must not be null");
    }

    public void addItem(ProposalDraftItemEntity item) {
        Objects.requireNonNull(item, "item must not be null");
        item.attachTo(this);
        items.add(item);
    }
}
