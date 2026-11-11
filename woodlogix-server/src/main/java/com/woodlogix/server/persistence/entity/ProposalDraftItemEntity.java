package com.woodlogix.server.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Objects;

@Entity
@Table(name = "proposal_draft_items",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_proposal_draft_item_position",
                columnNames = {"draft_id", "position"}))
@IdClass(ProposalDraftItemKey.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProposalDraftItemEntity {
    @Id
    @Column(name = "draft_id", nullable = false, length = 100)
    private String draftId;

    @Id
    @Column(name = "line_id", nullable = false, length = 100)
    private String lineId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "draft_id", nullable = false, insertable = false, updatable = false)
    private ProposalDraftEntity draft;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private String description;

    @Convert(converter = ProposalBigDecimalConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private BigDecimal quantity;

    @Column(name = "unit_code", nullable = false)
    private String unitCode;

    @Convert(converter = ProposalBigDecimalConverter.class)
    @Column(name = "unit_price_amount", nullable = false, columnDefinition = "TEXT")
    private BigDecimal unitPriceAmount;

    @Column(name = "unit_price_currency", nullable = false, length = 20)
    private String unitPriceCurrency;

    @Column(name = "individual_discount_type", length = 20)
    private String individualDiscountType;

    @Convert(converter = ProposalBigDecimalConverter.class)
    @Column(name = "individual_discount_percentage", columnDefinition = "TEXT")
    private BigDecimal individualDiscountPercentage;

    @Convert(converter = ProposalBigDecimalConverter.class)
    @Column(name = "individual_discount_amount", columnDefinition = "TEXT")
    private BigDecimal individualDiscountAmount;

    @Column(name = "individual_discount_currency", length = 20)
    private String individualDiscountCurrency;

    @Column(name = "tax_mode", nullable = false, length = 20)
    private String taxMode;

    @Convert(converter = ProposalBigDecimalConverter.class)
    @Column(name = "tax_rate", nullable = false, columnDefinition = "TEXT")
    private BigDecimal taxRate;

    @Column(name = "catalog_type", length = 30)
    private String catalogType;

    @Column(name = "catalog_id", length = 100)
    private String catalogId;

    @Convert(converter = ProposalBigDecimalConverter.class)
    @Column(name = "materials_amount", columnDefinition = "TEXT")
    private BigDecimal materialsAmount;
    @Column(name = "materials_currency", length = 20)
    private String materialsCurrency;
    @Convert(converter = ProposalBigDecimalConverter.class)
    @Column(name = "edge_banding_amount", columnDefinition = "TEXT")
    private BigDecimal edgeBandingAmount;
    @Column(name = "edge_banding_currency", length = 20)
    private String edgeBandingCurrency;
    @Convert(converter = ProposalBigDecimalConverter.class)
    @Column(name = "hardware_amount", columnDefinition = "TEXT")
    private BigDecimal hardwareAmount;
    @Column(name = "hardware_currency", length = 20)
    private String hardwareCurrency;
    @Convert(converter = ProposalBigDecimalConverter.class)
    @Column(name = "labor_amount", columnDefinition = "TEXT")
    private BigDecimal laborAmount;
    @Column(name = "labor_currency", length = 20)
    private String laborCurrency;
    @Convert(converter = ProposalBigDecimalConverter.class)
    @Column(name = "machines_amount", columnDefinition = "TEXT")
    private BigDecimal machinesAmount;
    @Column(name = "machines_currency", length = 20)
    private String machinesCurrency;
    @Convert(converter = ProposalBigDecimalConverter.class)
    @Column(name = "extras_amount", columnDefinition = "TEXT")
    private BigDecimal extrasAmount;
    @Column(name = "extras_currency", length = 20)
    private String extrasCurrency;

    public ProposalDraftItemEntity(
            String draftId,
            String lineId,
            int position,
            String description,
            BigDecimal quantity,
            String unitCode,
            BigDecimal unitPriceAmount,
            String unitPriceCurrency,
            String individualDiscountType,
            BigDecimal individualDiscountPercentage,
            BigDecimal individualDiscountAmount,
            String individualDiscountCurrency,
            String taxMode,
            BigDecimal taxRate,
            String catalogType,
            String catalogId,
            BigDecimal materialsAmount,
            String materialsCurrency,
            BigDecimal edgeBandingAmount,
            String edgeBandingCurrency,
            BigDecimal hardwareAmount,
            String hardwareCurrency,
            BigDecimal laborAmount,
            String laborCurrency,
            BigDecimal machinesAmount,
            String machinesCurrency,
            BigDecimal extrasAmount,
            String extrasCurrency) {
        this.draftId = Objects.requireNonNull(draftId, "draftId must not be null");
        this.lineId = Objects.requireNonNull(lineId, "lineId must not be null");
        this.position = position;
        this.description = description;
        this.quantity = quantity;
        this.unitCode = unitCode;
        this.unitPriceAmount = unitPriceAmount;
        this.unitPriceCurrency = unitPriceCurrency;
        this.individualDiscountType = individualDiscountType;
        this.individualDiscountPercentage = individualDiscountPercentage;
        this.individualDiscountAmount = individualDiscountAmount;
        this.individualDiscountCurrency = individualDiscountCurrency;
        this.taxMode = taxMode;
        this.taxRate = taxRate;
        this.catalogType = catalogType;
        this.catalogId = catalogId;
        this.materialsAmount = materialsAmount;
        this.materialsCurrency = materialsCurrency;
        this.edgeBandingAmount = edgeBandingAmount;
        this.edgeBandingCurrency = edgeBandingCurrency;
        this.hardwareAmount = hardwareAmount;
        this.hardwareCurrency = hardwareCurrency;
        this.laborAmount = laborAmount;
        this.laborCurrency = laborCurrency;
        this.machinesAmount = machinesAmount;
        this.machinesCurrency = machinesCurrency;
        this.extrasAmount = extrasAmount;
        this.extrasCurrency = extrasCurrency;
    }

    void attachTo(ProposalDraftEntity draft) {
        this.draft = Objects.requireNonNull(draft, "draft must not be null");
    }
}
