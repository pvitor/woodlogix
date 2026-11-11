package com.woodlogix.server.persistence;

import com.woodlogix.application.proposal.ProposalPersistencePort;
import com.woodlogix.application.proposal.ProposalProjectPort;
import com.woodlogix.application.proposal.ProposalProjectReference;
import com.woodlogix.application.proposal.ProposalRecord;
import com.woodlogix.application.proposal.ProposalSummaryRow;
import com.woodlogix.core.enums.CurrencyCode;
import com.woodlogix.core.model.CatalogReference;
import com.woodlogix.core.model.CatalogType;
import com.woodlogix.core.model.CommercialTermsDraft;
import com.woodlogix.core.model.ContactSnapshot;
import com.woodlogix.core.model.CustomerSnapshot;
import com.woodlogix.core.model.DiscountInput;
import com.woodlogix.core.model.FixedDiscountInput;
import com.woodlogix.core.model.InstallationTerms;
import com.woodlogix.core.model.InternalCostBreakdown;
import com.woodlogix.core.model.LeadTime;
import com.woodlogix.core.model.ManualConversion;
import com.woodlogix.core.model.Money;
import com.woodlogix.core.model.PaymentTerms;
import com.woodlogix.core.model.PercentageDiscountInput;
import com.woodlogix.core.model.Proposal;
import com.woodlogix.core.model.ProposalDraft;
import com.woodlogix.core.model.ProposalDraftItem;
import com.woodlogix.core.model.ProposalDraftStatus;
import com.woodlogix.core.model.ProposalDraftId;
import com.woodlogix.core.model.ProposalDraftRevision;
import com.woodlogix.core.model.ProposalId;
import com.woodlogix.core.model.ProposalLineId;
import com.woodlogix.core.model.ProposalVersionNumber;
import com.woodlogix.core.model.RecipientSnapshot;
import com.woodlogix.core.model.SalesProjectId;
import com.woodlogix.core.model.TaxInput;
import com.woodlogix.core.model.TaxMode;
import com.woodlogix.core.model.TransportTerms;
import com.woodlogix.core.model.UnitOfMeasure;
import com.woodlogix.core.model.ValidityPeriod;
import com.woodlogix.server.persistence.entity.ProposalDraftEntity;
import com.woodlogix.server.persistence.entity.ProposalDraftItemEntity;
import com.woodlogix.server.persistence.entity.ProposalEntity;
import com.woodlogix.server.persistence.entity.SalesProjectEntity;
import com.woodlogix.server.persistence.repository.ProposalRepository;
import com.woodlogix.server.persistence.repository.SalesProjectRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ProposalPersistenceAdapter implements ProposalPersistencePort, ProposalProjectPort {
    private static final String RESERVE_ID_SQL = """
            INSERT INTO proposal_id_sequences (proposal_year, next_sequence)
            VALUES (:year, 2)
            ON CONFLICT (proposal_year) DO UPDATE SET
                next_sequence = proposal_id_sequences.next_sequence + 1
            WHERE proposal_id_sequences.next_sequence <= 9999
            RETURNING next_sequence - 1
            """;

    private final ProposalRepository proposals;
    private final SalesProjectRepository projects;
    private final EntityManager entityManager;

    @Override
    public ProposalId reserveNextId(int year) {
        Object result;
        try {
            result = entityManager.createNativeQuery(RESERVE_ID_SQL)
                    .setParameter("year", year)
                    .getSingleResult();
        } catch (NoResultException exception) {
            throw new IllegalStateException(
                    "Proposal ID sequence exhausted or inconsistent for year " + year,
                    exception);
        }
        int sequence = ((Number) result).intValue();
        if (sequence < 1 || sequence > 9999) {
            throw new IllegalStateException("Reserved proposal sequence is out of range");
        }
        return new ProposalId("PROP-%04d-%04d".formatted(year, sequence));
    }

    @Override
    public ProposalRecord saveNew(Proposal proposal) {
        if (!proposal.getVersionHistory().isEmpty()) {
            throw new IllegalStateException(
                    "ProposalVersion persistence is not supported in this operation");
        }
        SalesProjectEntity project = projects.getReferenceById(
                proposal.getSalesProjectId().getValue());
        ProposalEntity entity = new ProposalEntity(
                proposal.getProposalId().getValue(), project);
        List<ProposalDraft> drafts = proposal.getDraftHistory();
        for (int position = 0; position < drafts.size(); position++) {
            entity.addDraft(toEntity(drafts.get(position), position));
        }
        ProposalEntity saved = proposals.saveAndFlush(entity);
        return new ProposalRecord(proposal, saved.getPersistenceVersion());
    }

    @Override
    public Optional<ProposalRecord> findById(ProposalId proposalId) {
        return proposals.findById(proposalId.getValue())
                .map(entity -> new ProposalRecord(
                        toDomain(entity), entity.getPersistenceVersion()));
    }

    @Override
    public List<ProposalSummaryRow> findAllBySalesProjectId(SalesProjectId salesProjectId) {
        return proposals.findAllBySalesProjectIdOrderById(salesProjectId.getValue())
                .stream()
                .map(entity -> new ProposalSummaryRow(
                        new ProposalId(entity.getId()),
                        new SalesProjectId(entity.getSalesProject().getId())))
                .toList();
    }

    @Override
    public Optional<ProposalProjectReference> findById(SalesProjectId salesProjectId) {
        return projects.findWithCustomerAndEnvironmentById(salesProjectId.getValue())
                .map(project -> new ProposalProjectReference(
                        new SalesProjectId(project.getId()),
                        new CustomerSnapshot(
                                project.getCustomer().getId(),
                                project.getCustomer().getFullName())));
    }

    private ProposalDraftEntity toEntity(ProposalDraft draft, int position) {
        DiscountColumns discount = discountColumns(draft.getGeneralDiscount());
        RecipientColumns recipient = recipientColumns(draft.getRecipient());
        CommercialTermsDraft terms = draft.getCommercialTerms();
        ProposalDraftEntity entity = new ProposalDraftEntity(
                draft.getDraftId().getValue(),
                position,
                valueOf(draft.getSourceVersionNumber()),
                draft.getRevision().getValue(),
                draft.getStatus(),
                draft.getCustomer().getCustomerId(),
                draft.getCustomer().getDisplayName(),
                recipient.displayName,
                recipient.contactDisplayName,
                recipient.phone,
                recipient.email,
                draft.getCurrency(),
                draft.getConversion() == null
                        ? null : draft.getConversion().getUyuPerUsd(),
                discount.type,
                discount.percentage,
                discount.amount,
                discount.currency,
                draft.getValidityPeriod().getDays(),
                terms.getPaymentTerms().getDescription(),
                terms.getLeadTime() == null ? null : terms.getLeadTime().getDescription(),
                terms.getTransportTerms() == null
                        ? null : terms.getTransportTerms().getDescription(),
                terms.getInstallationTerms() == null
                        ? null : terms.getInstallationTerms().getDescription(),
                terms.getNotes());
        List<ProposalDraftItem> items = draft.getItems();
        for (int itemPosition = 0; itemPosition < items.size(); itemPosition++) {
            entity.addItem(toEntity(
                    items.get(itemPosition), draft.getDraftId(), itemPosition));
        }
        return entity;
    }

    private ProposalDraftItemEntity toEntity(
            ProposalDraftItem item, ProposalDraftId draftId, int position) {
        DiscountColumns discount = discountColumns(item.getIndividualDiscount());
        Money price = item.getUnitPriceBeforeDiscountsAndTax();
        TaxInput tax = item.getTax();
        CatalogReference catalog = item.getCatalogReference();
        CostColumns costs = costColumns(item.getDirectCosts());
        return new ProposalDraftItemEntity(
                draftId.getValue(),
                item.getLineId().getValue(),
                position,
                item.getDescription(),
                item.getQuantity(),
                item.getUnit().getCode(),
                price.getAmount(),
                price.getCurrency().name(),
                discount.type,
                discount.percentage,
                discount.amount,
                discount.currency == null ? null : discount.currency.name(),
                tax.getMode().name(),
                tax.getRate(),
                catalog == null ? null : catalog.getCatalogType().name(),
                catalog == null ? null : catalog.getCatalogId(),
                costs.materialsAmount,
                costs.materialsCurrency,
                costs.edgeBandingAmount,
                costs.edgeBandingCurrency,
                costs.hardwareAmount,
                costs.hardwareCurrency,
                costs.laborAmount,
                costs.laborCurrency,
                costs.machinesAmount,
                costs.machinesCurrency,
                costs.extrasAmount,
                costs.extrasCurrency);
    }

    private Proposal toDomain(ProposalEntity entity) {
        List<ProposalDraftEntity> drafts = entity.getDrafts().stream()
                .sorted(Comparator.comparingInt(ProposalDraftEntity::getPosition))
                .toList();
        for (int index = 0; index < drafts.size(); index++) {
            int position = drafts.get(index).getPosition();
            if (position != index) {
                throw new IllegalStateException(
                        "Proposal draft positions must be contiguous and zero-based");
            }
        }
        List<ProposalDraft> domainDrafts = drafts.stream()
                .map(this::toDomain)
                .toList();
        return Proposal.restore(
                new ProposalId(entity.getId()),
                new SalesProjectId(entity.getSalesProject().getId()),
                domainDrafts,
                List.of());
    }

    private ProposalDraft toDomain(ProposalDraftEntity entity) {
        RecipientSnapshot recipient = recipient(
                entity.getRecipientDisplayName(),
                entity.getRecipientContactDisplayName(),
                entity.getRecipientContactPhone(),
                entity.getRecipientContactEmail());
        DiscountInput discount = discount(
                entity.getGeneralDiscountType(),
                entity.getGeneralDiscountPercentage(),
                entity.getGeneralDiscountAmount(),
                entity.getGeneralDiscountCurrency() == null
                        ? null : entity.getGeneralDiscountCurrency().name());
        List<ProposalDraftItemEntity> itemEntities = entity.getItems().stream()
                .sorted(Comparator.comparingInt(ProposalDraftItemEntity::getPosition))
                .toList();
        List<ProposalDraftItem> items = new ArrayList<>();
        for (int index = 0; index < itemEntities.size(); index++) {
            ProposalDraftItemEntity item = itemEntities.get(index);
            if (item.getPosition() != index) {
                throw new IllegalStateException(
                        "Proposal draft item positions must be contiguous");
            }
            items.add(toDomain(item));
        }
        return new ProposalDraft(
                new ProposalDraftId(entity.getDraftId()),
                new ProposalId(entity.getProposal().getId()),
                new SalesProjectId(entity.getProposal().getSalesProject().getId()),
                entity.getSourceVersionNumber() == null
                        ? null : new ProposalVersionNumber(entity.getSourceVersionNumber()),
                ProposalDraftRevision.of(entity.getRevision()),
                entity.getStatus(),
                new CustomerSnapshot(entity.getCustomerId(), entity.getCustomerDisplayName()),
                recipient,
                entity.getCurrency(),
                entity.getConversionUyuPerUsd() == null
                        ? null : new ManualConversion(entity.getConversionUyuPerUsd()),
                items,
                discount,
                ValidityPeriod.of(entity.getValidityDays()),
                new CommercialTermsDraft(
                        new PaymentTerms(entity.getPaymentTerms()),
                        entity.getLeadTime() == null ? null : new LeadTime(entity.getLeadTime()),
                        entity.getTransportTerms() == null
                                ? null : new TransportTerms(entity.getTransportTerms()),
                        entity.getInstallationTerms() == null
                                ? null : new InstallationTerms(entity.getInstallationTerms()),
                        entity.getCommercialNotes()));
    }

    private ProposalDraftItem toDomain(ProposalDraftItemEntity entity) {
        DiscountInput discount = discount(
                entity.getIndividualDiscountType(),
                entity.getIndividualDiscountPercentage(),
                entity.getIndividualDiscountAmount(),
                entity.getIndividualDiscountCurrency());
        CatalogReference catalog;
        if (entity.getCatalogType() == null && entity.getCatalogId() == null) {
            catalog = null;
        } else if (entity.getCatalogType() == null || entity.getCatalogId() == null) {
            throw new IllegalStateException("Catalog reference columns are inconsistent");
        } else {
            catalog = new CatalogReference(
                    CatalogType.valueOf(entity.getCatalogType()), entity.getCatalogId());
        }
        InternalCostBreakdown costs = costs(entity);
        return new ProposalDraftItem(
                new ProposalLineId(entity.getLineId()),
                entity.getDescription(),
                entity.getQuantity(),
                new UnitOfMeasure(entity.getUnitCode()),
                new Money(
                        entity.getUnitPriceAmount(),
                        CurrencyCode.valueOf(entity.getUnitPriceCurrency())),
                discount,
                new TaxInput(TaxMode.valueOf(entity.getTaxMode()), entity.getTaxRate()),
                catalog,
                costs);
    }

    private static DiscountInput discount(
            String type, BigDecimal percentage, BigDecimal amount, String currency) {
        if (type == null) {
            if (percentage != null || amount != null || currency != null) {
                throw new IllegalStateException("Discount columns are inconsistent");
            }
            return null;
        }
        return switch (type) {
            case "PERCENTAGE" -> {
                if (percentage == null || amount != null || currency != null) {
                    throw new IllegalStateException("Percentage discount columns are inconsistent");
                }
                yield new PercentageDiscountInput(percentage);
            }
            case "FIXED" -> {
                if (amount == null || currency == null || percentage != null) {
                    throw new IllegalStateException("Fixed discount columns are inconsistent");
                }
                yield new FixedDiscountInput(
                        new Money(amount, CurrencyCode.valueOf(currency)));
            }
            default -> throw new IllegalStateException("Unknown discount type: " + type);
        };
    }

    private static DiscountColumns discountColumns(DiscountInput discount) {
        if (discount == null) {
            return new DiscountColumns(null, null, null, null);
        }
        if (discount instanceof PercentageDiscountInput percentage) {
            return new DiscountColumns("PERCENTAGE", percentage.getPercentage(), null, null);
        }
        FixedDiscountInput fixed = (FixedDiscountInput) discount;
        return new DiscountColumns(
                "FIXED", null, fixed.getAmount().getAmount(), fixed.getAmount().getCurrency());
    }

    private static CostColumns costColumns(InternalCostBreakdown costs) {
        if (costs == null) {
            return CostColumns.empty();
        }
        return new CostColumns(
                costs.getMaterials().getAmount(), costs.getMaterials().getCurrency().name(),
                costs.getEdgeBanding().getAmount(), costs.getEdgeBanding().getCurrency().name(),
                costs.getHardware().getAmount(), costs.getHardware().getCurrency().name(),
                costs.getLabor().getAmount(), costs.getLabor().getCurrency().name(),
                costs.getMachines().getAmount(), costs.getMachines().getCurrency().name(),
                costs.getExtras().getAmount(), costs.getExtras().getCurrency().name());
    }

    private static InternalCostBreakdown costs(ProposalDraftItemEntity entity) {
        boolean any = entity.getMaterialsAmount() != null
                || entity.getEdgeBandingAmount() != null
                || entity.getHardwareAmount() != null
                || entity.getLaborAmount() != null
                || entity.getMachinesAmount() != null
                || entity.getExtrasAmount() != null;
        if (!any) {
            return null;
        }
        if (entity.getMaterialsAmount() == null || entity.getMaterialsCurrency() == null
                || entity.getEdgeBandingAmount() == null
                || entity.getEdgeBandingCurrency() == null
                || entity.getHardwareAmount() == null
                || entity.getHardwareCurrency() == null
                || entity.getLaborAmount() == null || entity.getLaborCurrency() == null
                || entity.getMachinesAmount() == null || entity.getMachinesCurrency() == null
                || entity.getExtrasAmount() == null || entity.getExtrasCurrency() == null) {
            throw new IllegalStateException("Direct cost columns are incomplete");
        }
        return new InternalCostBreakdown(
                money(entity.getMaterialsAmount(), entity.getMaterialsCurrency()),
                money(entity.getEdgeBandingAmount(), entity.getEdgeBandingCurrency()),
                money(entity.getHardwareAmount(), entity.getHardwareCurrency()),
                money(entity.getLaborAmount(), entity.getLaborCurrency()),
                money(entity.getMachinesAmount(), entity.getMachinesCurrency()),
                money(entity.getExtrasAmount(), entity.getExtrasCurrency()));
    }

    private static Money money(BigDecimal amount, String currency) {
        return new Money(amount, CurrencyCode.valueOf(currency));
    }

    private static RecipientColumns recipientColumns(RecipientSnapshot recipient) {
        if (recipient == null) {
            return new RecipientColumns(null, null, null, null);
        }
        ContactSnapshot contact = recipient.getContact();
        return new RecipientColumns(
                recipient.getDisplayName(),
                contact == null ? null : contact.getDisplayName(),
                contact == null ? null : contact.getPhone(),
                contact == null ? null : contact.getEmail());
    }

    private static RecipientSnapshot recipient(
            String displayName, String contactDisplayName, String phone, String email) {
        if (displayName == null) {
            if (contactDisplayName != null || phone != null || email != null) {
                throw new IllegalStateException("Recipient columns are inconsistent");
            }
            return null;
        }
        ContactSnapshot contact = contactDisplayName == null && phone == null && email == null
                ? null : new ContactSnapshot(contactDisplayName, phone, email);
        return new RecipientSnapshot(displayName, contact);
    }

    private static String valueOf(ProposalVersionNumber value) {
        return value == null ? null : value.getValue();
    }

    private record DiscountColumns(
            String type, BigDecimal percentage, BigDecimal amount, CurrencyCode currency) {
    }

    private record RecipientColumns(
            String displayName, String contactDisplayName, String phone, String email) {
    }

    private record CostColumns(
            BigDecimal materialsAmount, String materialsCurrency,
            BigDecimal edgeBandingAmount, String edgeBandingCurrency,
            BigDecimal hardwareAmount, String hardwareCurrency,
            BigDecimal laborAmount, String laborCurrency,
            BigDecimal machinesAmount, String machinesCurrency,
            BigDecimal extrasAmount, String extrasCurrency) {
        static CostColumns empty() {
            return new CostColumns(null, null, null, null, null, null, null, null, null, null, null, null);
        }
    }
}
