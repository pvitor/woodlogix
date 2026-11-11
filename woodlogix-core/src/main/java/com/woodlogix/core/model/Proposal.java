package com.woodlogix.core.model;

import com.woodlogix.core.enums.CurrencyCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class Proposal {
    private final ProposalId proposalId;
    private final SalesProjectId salesProjectId;
    private final List<ProposalDraft> draftHistory;
    private final List<ProposalVersion> versionHistory;
    private ProposalDraftId currentDraftId;
    private ProposalDraftRevision currentDraftRevision;
    private ProposalVersionNumber currentIssuedVersionNumber;

    public Proposal(
            ProposalId proposalId,
            SalesProjectId salesProjectId,
            ProposalDraft initialDraft) {
        this.proposalId = Objects.requireNonNull(proposalId, "proposalId must not be null");
        this.salesProjectId = Objects.requireNonNull(
                salesProjectId, "salesProjectId must not be null");
        this.draftHistory = new ArrayList<>();
        this.versionHistory = new ArrayList<>();
        validateDraftIdentity(initialDraft);
        if (initialDraft.getStatus() != ProposalDraftStatus.EDITABLE) {
            throw new IllegalArgumentException("Initial draft must be editable");
        }
        this.draftHistory.add(initialDraft);
        this.currentDraftId = initialDraft.getDraftId();
        this.currentDraftRevision = initialDraft.getRevision();
    }

    public static Proposal restore(
            ProposalId proposalId,
            SalesProjectId salesProjectId,
            List<ProposalDraft> draftHistory,
            List<ProposalVersion> versionHistory) {
        Objects.requireNonNull(proposalId, "proposalId must not be null");
        Objects.requireNonNull(salesProjectId, "salesProjectId must not be null");
        List<ProposalDraft> drafts = copyHistory(draftHistory, "draftHistory");
        List<ProposalVersion> versions = copyHistory(versionHistory, "versionHistory");

        Map<ProposalVersionNumber, ProposalVersion> versionsByNumber =
                validateVersions(proposalId, salesProjectId, versions);
        ProposalVersion currentVersion = findCurrentVersion(versions);
        ProposalDraft currentDraft = validateDrafts(
                proposalId,
                salesProjectId,
                drafts,
                versionsByNumber,
                currentVersion);

        if (currentDraft == null && currentVersion == null) {
            throw new IllegalArgumentException(
                    "A proposal must have a draft or an emitted version");
        }

        return new Proposal(
                proposalId,
                salesProjectId,
                drafts,
                versions,
                currentDraft,
                currentVersion);
    }

    private Proposal(
            ProposalId proposalId,
            SalesProjectId salesProjectId,
            List<ProposalDraft> draftHistory,
            List<ProposalVersion> versionHistory,
            ProposalDraft currentDraft,
            ProposalVersion currentVersion) {
        this.proposalId = proposalId;
        this.salesProjectId = salesProjectId;
        this.draftHistory = List.copyOf(draftHistory);
        this.versionHistory = List.copyOf(versionHistory);
        this.currentDraftId = currentDraft == null ? null : currentDraft.getDraftId();
        this.currentDraftRevision = currentDraft == null
                ? null
                : currentDraft.getRevision();
        this.currentIssuedVersionNumber = currentVersion == null
                ? null
                : currentVersion.getVersionNumber();
    }

    public ProposalId getProposalId() {
        return proposalId;
    }

    public SalesProjectId getSalesProjectId() {
        return salesProjectId;
    }

    public List<ProposalDraft> getDraftHistory() {
        return List.copyOf(draftHistory);
    }

    public List<ProposalVersion> getVersionHistory() {
        return List.copyOf(versionHistory);
    }

    public ProposalDraft getCurrentDraft() {
        if (currentDraftId == null) {
            return null;
        }
        return findDraft(currentDraftId, currentDraftRevision);
    }

    public ProposalDraftId getCurrentDraftId() {
        return currentDraftId;
    }

    public ProposalDraftRevision getCurrentDraftRevision() {
        return currentDraftRevision;
    }

    public ProposalVersionNumber getCurrentIssuedVersionNumber() {
        return currentIssuedVersionNumber;
    }

    public ProposalVersion getCurrentVersion() {
        if (currentIssuedVersionNumber == null) {
            return null;
        }
        return findVersion(currentIssuedVersionNumber);
    }

    public ProposalDraft addDraftItem(ProposalDraftItem item) {
        return replaceCurrentDraft(getCurrentDraft().addItem(item));
    }

    public ProposalDraft replaceDraftItem(ProposalDraftItem item) {
        return replaceCurrentDraft(getCurrentDraft().replaceItem(item));
    }

    public ProposalDraft removeDraftItem(ProposalLineId lineId) {
        return replaceCurrentDraft(getCurrentDraft().removeItem(lineId));
    }

    public ProposalDraft changeCustomer(CustomerSnapshot customer) {
        return replaceCurrentDraft(getCurrentDraft().withCustomer(customer));
    }

    public ProposalDraft changeRecipient(RecipientSnapshot recipient) {
        return replaceCurrentDraft(getCurrentDraft().withRecipient(recipient));
    }

    public ProposalDraft changeConversion(ManualConversion conversion) {
        return replaceCurrentDraft(getCurrentDraft().withConversion(conversion));
    }

    public ProposalDraft changeGeneralDiscount(DiscountInput discount) {
        return replaceCurrentDraft(getCurrentDraft().withGeneralDiscount(discount));
    }

    public ProposalDraft changeValidityPeriod(ValidityPeriod validityPeriod) {
        return replaceCurrentDraft(getCurrentDraft().withValidityPeriod(validityPeriod));
    }

    public ProposalDraft changeCommercialTerms(CommercialTermsDraft commercialTerms) {
        return replaceCurrentDraft(getCurrentDraft().withCommercialTerms(commercialTerms));
    }

    public ProposalVersion emit(
            ProposalDraftId draftId,
            ProposalDraftRevision draftRevision,
            ProposalVersionNumber nextVersionNumber,
            ProposalCalculationResult calculationResult,
            LocalDate issuedOn,
            LocalDate validUntil) {
        ProposalDraft draft = requireCurrentEditableDraft(draftId, draftRevision);
        Objects.requireNonNull(nextVersionNumber, "nextVersionNumber must not be null");
        Objects.requireNonNull(calculationResult, "calculationResult must not be null");
        Objects.requireNonNull(issuedOn, "issuedOn must not be null");
        Objects.requireNonNull(validUntil, "validUntil must not be null");
        if (validUntil.isBefore(issuedOn)) {
            throw new IllegalArgumentException("validUntil must not be before issuedOn");
        }
        if (currentIssuedVersionNumber != null
                && getCurrentVersion().getStatus() != ProposalVersionStatus.ISSUED) {
            throw new IllegalStateException("Only an issued proposal can be emitted or revised");
        }
        validateNextVersion(nextVersionNumber);
        validateCalculationIdentity(calculationResult, draft);
        ProposalSnapshot snapshot = buildSnapshot(
                draft, nextVersionNumber, calculationResult, issuedOn, validUntil);

        ProposalDraft lockedDraft = draft.lock();
        replaceDraftInHistory(lockedDraft);
        currentDraftId = null;
        currentDraftRevision = null;

        if (currentIssuedVersionNumber != null) {
            replaceVersion(getCurrentVersion().withReplacement(nextVersionNumber));
        }
        ProposalVersion emitted = new ProposalVersion(
                nextVersionNumber,
                snapshot,
                ProposalVersionStatus.ISSUED,
                null,
                null);
        versionHistory.add(emitted);
        currentIssuedVersionNumber = nextVersionNumber;
        return emitted;
    }

    public ProposalDraft startRevision(ProposalDraftId newDraftId) {
        Objects.requireNonNull(newDraftId, "newDraftId must not be null");
        if (currentIssuedVersionNumber == null) {
            throw new IllegalStateException("A proposal must have an issued version to be revised");
        }
        ProposalVersion current = getCurrentVersion();
        if (current.getStatus() != ProposalVersionStatus.ISSUED
                || current.getReplacedByVersionNumber() != null) {
            throw new IllegalStateException("Only the current issued version can be revised");
        }
        if (currentDraftId != null) {
            throw new IllegalStateException("An editable draft already exists");
        }
        if (draftHistory.stream().anyMatch(draft -> draft.getDraftId().equals(newDraftId))) {
            throw new IllegalArgumentException("Draft ID has already been used");
        }
        ProposalDraft revision = copySnapshotToDraft(
                newDraftId, current.getSnapshot(), current.getVersionNumber());
        draftHistory.add(revision);
        currentDraftId = revision.getDraftId();
        currentDraftRevision = revision.getRevision();
        return revision;
    }

    public ProposalVersion acceptCurrent() {
        return acceptCurrent(null);
    }

    public ProposalVersion acceptCurrent(SaleId saleId) {
        ProposalVersion current = requireCurrentTransitionVersion();
        ProposalVersion accepted = current.withStatus(
                ProposalVersionStatus.ACCEPTED, saleId);
        replaceVersion(accepted);
        return accepted;
    }

    public ProposalVersion rejectCurrent() {
        return transitionCurrent(ProposalVersionStatus.REJECTED);
    }

    public ProposalVersion expireCurrent() {
        return transitionCurrent(ProposalVersionStatus.EXPIRED);
    }

    public ProposalVersion withdrawCurrent() {
        return transitionCurrent(ProposalVersionStatus.WITHDRAWN);
    }

    public ProposalVersion linkSale(SaleId saleId) {
        ProposalVersion current = getCurrentVersion();
        if (current == null || current.getStatus() != ProposalVersionStatus.ACCEPTED) {
            throw new IllegalStateException("Only an accepted version can be linked to a sale");
        }
        ProposalVersion linked = current.withSale(saleId);
        replaceVersion(linked);
        return linked;
    }

    private ProposalVersion transitionCurrent(ProposalVersionStatus status) {
        ProposalVersion current = requireCurrentTransitionVersion();
        ProposalVersion transitioned = current.withStatus(status, null);
        replaceVersion(transitioned);
        return transitioned;
    }

    private ProposalVersion requireCurrentTransitionVersion() {
        ProposalVersion current = getCurrentVersion();
        if (current == null
                || current.getStatus() != ProposalVersionStatus.ISSUED
                || current.getReplacedByVersionNumber() != null) {
            throw new IllegalStateException("Only the current issued version can transition");
        }
        return current;
    }

    private ProposalDraft requireCurrentEditableDraft(
            ProposalDraftId draftId, ProposalDraftRevision draftRevision) {
        Objects.requireNonNull(draftId, "draftId must not be null");
        Objects.requireNonNull(draftRevision, "draftRevision must not be null");
        ProposalDraft current = getCurrentDraft();
        if (current == null
                || !current.getDraftId().equals(draftId)
                || !current.getRevision().equals(draftRevision)
                || current.getStatus() != ProposalDraftStatus.EDITABLE) {
            throw new IllegalStateException("Draft identity or revision is no longer current");
        }
        return current;
    }

    private void validateCalculationIdentity(
            ProposalCalculationResult result, ProposalDraft draft) {
        if (!proposalId.equals(result.getProposalId())
                || !draft.getDraftId().equals(result.getDraftId())
                || !draft.getRevision().equals(result.getDraftRevision())) {
            throw new IllegalArgumentException("Calculation result does not match current draft");
        }
        if (result.getLineResults().size() != draft.getItems().size()) {
            throw new IllegalArgumentException("Calculation result must cover every draft line");
        }
        for (ProposalDraftItem item : draft.getItems()) {
            ProposalCalculationLineResult line = findLineResult(result, item.getLineId());
            validateLineResult(item, line, draft.getCurrency());
        }
        if (result.getTotals().getFinalTotal().getCurrency() != draft.getCurrency()) {
            throw new IllegalArgumentException("Calculation totals must use draft currency");
        }
        if (result.getGeneralCosts() != null
                && result.getGeneralCosts().getTotal().getCurrency()
                != draft.getCurrency()) {
            throw new IllegalArgumentException("General costs must use draft currency");
        }
        validateDiscountMatch(
                draft.getGeneralDiscount(), result.getGeneralDiscount(), draft.getCurrency());
    }

    private static ProposalCalculationLineResult findLineResult(
            ProposalCalculationResult result, ProposalLineId lineId) {
        for (ProposalCalculationLineResult line : result.getLineResults()) {
            if (line.getLineId().equals(lineId)) {
                return line;
            }
        }
        throw new IllegalArgumentException("Calculation result is missing a draft line");
    }

    private static void validateLineResult(
            ProposalDraftItem item,
            ProposalCalculationLineResult result,
            CurrencyCode currency) {
        if (result.getLineSubtotal().getCurrency() != currency
                || result.getFinalTotal().getCurrency() != currency) {
            throw new IllegalArgumentException("Line results must use draft currency");
        }
        validateDiscountMatch(item.getIndividualDiscount(),
                result.getIndividualDiscount(), currency);
        TaxInput input = item.getTax();
        TaxSnapshot output = result.getTax();
        if (input.getMode() != output.getMode()
                || input.getRate().compareTo(output.getRate()) != 0) {
            throw new IllegalArgumentException("Tax result does not match draft input");
        }
    }

    private static void validateDiscountMatch(
            DiscountInput input, DiscountSnapshot output, CurrencyCode currency) {
        if (input == null) {
            if (output != null) {
                throw new IllegalArgumentException("Discount result does not match draft input");
            }
            return;
        }
        if (output == null) {
            throw new IllegalArgumentException("Discount result does not match draft input");
        }
        if (input instanceof PercentageDiscountInput percentage
                && output instanceof PercentageDiscountSnapshot snapshot) {
            if (percentage.getPercentage().compareTo(snapshot.getPercentage()) != 0
                    || snapshot.getAppliedAmount().getCurrency() != currency) {
                throw new IllegalArgumentException("Percentage discount result does not match");
            }
            return;
        }
        if (input instanceof FixedDiscountInput fixed
                && output instanceof FixedDiscountSnapshot snapshot) {
            if (!fixed.getAmount().equals(snapshot.getInformedAmount())
                    || snapshot.getAppliedAmount().getCurrency() != currency) {
                throw new IllegalArgumentException("Fixed discount result does not match");
            }
            return;
        }
        throw new IllegalArgumentException("Discount result type does not match draft input");
    }

    private ProposalSnapshot buildSnapshot(
            ProposalDraft draft,
            ProposalVersionNumber versionNumber,
            ProposalCalculationResult result,
            LocalDate issuedOn,
            LocalDate validUntil) {
        List<ProposalItemSnapshot> items = new ArrayList<>();
        for (ProposalDraftItem item : draft.getItems()) {
            ProposalCalculationLineResult line = findLineResult(result, item.getLineId());
            items.add(new ProposalItemSnapshot(
                    item.getLineId(),
                    item.getDescription(),
                    item.getQuantity(),
                    item.getUnit(),
                    item.getUnitPriceBeforeDiscountsAndTax(),
                    line.getLineSubtotal(),
                    line.getIndividualDiscount(),
                    line.getGeneralDiscountAllocation(),
                    line.getTaxableBase(),
                    line.getTax(),
                    line.getFinalTotal(),
                    item.getCatalogReference(),
                    item.getDirectCosts()));
        }
        return new ProposalSnapshot(
                draft.getProposalId(),
                versionNumber,
                draft.getSalesProjectId(),
                draft.getCustomer(),
                draft.getRecipient(),
                draft.getCurrency(),
                draft.getConversion(),
                items,
                result.getGeneralDiscount(),
                result.getTotals(),
                result.getGeneralCosts(),
                toSnapshotTerms(draft.getCommercialTerms()),
                issuedOn,
                draft.getValidityPeriod(),
                validUntil);
    }

    private static CommercialTermsSnapshot toSnapshotTerms(
            CommercialTermsDraft terms) {
        return new CommercialTermsSnapshot(
                new PaymentTerms(terms.getPaymentTerms().getDescription()),
                terms.getLeadTime() == null
                        ? null
                        : new LeadTime(terms.getLeadTime().getDescription()),
                terms.getTransportTerms() == null
                        ? null
                        : new TransportTerms(terms.getTransportTerms().getDescription()),
                terms.getInstallationTerms() == null
                        ? null
                        : new InstallationTerms(terms.getInstallationTerms().getDescription()),
                terms.getNotes());
    }

    private static ProposalDraft copySnapshotToDraft(
            ProposalDraftId draftId,
            ProposalSnapshot snapshot,
            ProposalVersionNumber sourceVersionNumber) {
        List<ProposalDraftItem> items = new ArrayList<>();
        for (ProposalItemSnapshot item : snapshot.getItems()) {
            items.add(new ProposalDraftItem(
                    item.getLineId(),
                    item.getDescription(),
                    item.getQuantity(),
                    item.getUnit(),
                    item.getUnitPriceBeforeDiscountsAndTax(),
                    toDiscountInput(item.getIndividualDiscount()),
                    new TaxInput(item.getTax().getMode(), item.getTax().getRate()),
                    item.getCatalogReference(),
                    item.getDirectCosts()));
        }
        return new ProposalDraft(
                draftId,
                snapshot.getProposalId(),
                snapshot.getSalesProjectId(),
                sourceVersionNumber,
                ProposalDraftRevision.initial(),
                ProposalDraftStatus.EDITABLE,
                snapshot.getCustomer(),
                snapshot.getRecipient(),
                snapshot.getCurrency(),
                snapshot.getConversion(),
                items,
                toDiscountInput(snapshot.getGeneralDiscount()),
                snapshot.getValidityPeriod(),
                toDraftTerms(snapshot.getCommercialTerms()));
    }

    private static DiscountInput toDiscountInput(DiscountSnapshot discount) {
        if (discount == null) {
            return null;
        }
        if (discount instanceof PercentageDiscountSnapshot percentage) {
            return new PercentageDiscountInput(percentage.getPercentage());
        }
        FixedDiscountSnapshot fixed = (FixedDiscountSnapshot) discount;
        return new FixedDiscountInput(fixed.getInformedAmount());
    }

    private static CommercialTermsDraft toDraftTerms(
            CommercialTermsSnapshot terms) {
        return new CommercialTermsDraft(
                new PaymentTerms(terms.getPaymentTerms().getDescription()),
                terms.getLeadTime() == null
                        ? null
                        : new LeadTime(terms.getLeadTime().getDescription()),
                terms.getTransportTerms() == null
                        ? null
                        : new TransportTerms(terms.getTransportTerms().getDescription()),
                terms.getInstallationTerms() == null
                        ? null
                        : new InstallationTerms(terms.getInstallationTerms().getDescription()),
                terms.getNotes());
    }

    private void validateNextVersion(ProposalVersionNumber nextVersionNumber) {
        int expected = currentIssuedVersionNumber == null
                ? 1
                : increment(currentIssuedVersionNumber.getSequence());
        if (nextVersionNumber.getSequence() != expected) {
            throw new IllegalArgumentException("Version number must be the next sequence");
        }
        if (versionHistory.stream().anyMatch(
                version -> version.getVersionNumber().equals(nextVersionNumber))) {
            throw new IllegalArgumentException("Version number has already been used");
        }
    }

    private static int increment(int sequence) {
        if (sequence == Integer.MAX_VALUE) {
            throw new IllegalStateException("Proposal version sequence overflow");
        }
        return sequence + 1;
    }

    private void validateDraftIdentity(ProposalDraft draft) {
        Objects.requireNonNull(draft, "initialDraft must not be null");
        if (!proposalId.equals(draft.getProposalId())
                || !salesProjectId.equals(draft.getSalesProjectId())) {
            throw new IllegalArgumentException("Draft does not belong to proposal");
        }
    }

    private ProposalDraft findDraft(
            ProposalDraftId draftId, ProposalDraftRevision revision) {
        for (ProposalDraft draft : draftHistory) {
            if (draft.getDraftId().equals(draftId)
                    && draft.getRevision().equals(revision)) {
                return draft;
            }
        }
        throw new IllegalStateException("Current draft is missing from history");
    }

    private ProposalVersion findVersion(ProposalVersionNumber versionNumber) {
        for (ProposalVersion version : versionHistory) {
            if (version.getVersionNumber().equals(versionNumber)) {
                return version;
            }
        }
        throw new IllegalStateException("Current version is missing from history");
    }

    private ProposalDraft replaceCurrentDraft(ProposalDraft replacement) {
        ProposalDraft current = getCurrentDraft();
        if (current == null) {
            throw new IllegalStateException("There is no editable draft");
        }
        validateDraftIdentity(replacement);
        if (replacement.getDraftId() != current.getDraftId()
                && !replacement.getDraftId().equals(current.getDraftId())) {
            throw new IllegalArgumentException("Draft identity cannot change during editing");
        }
        if (replacement.getStatus() != ProposalDraftStatus.EDITABLE) {
            throw new IllegalArgumentException("Current draft must remain editable");
        }
        replaceDraftInHistory(replacement);
        currentDraftId = replacement.getDraftId();
        currentDraftRevision = replacement.getRevision();
        return replacement;
    }

    private void replaceDraftInHistory(ProposalDraft replacement) {
        for (int index = 0; index < draftHistory.size(); index++) {
            ProposalDraft current = draftHistory.get(index);
            if (current.getDraftId().equals(replacement.getDraftId())) {
                draftHistory.set(index, replacement);
                return;
            }
        }
        draftHistory.add(replacement);
    }

    private void replaceVersion(ProposalVersion replacement) {
        for (int index = 0; index < versionHistory.size(); index++) {
            if (versionHistory.get(index).getVersionNumber()
                    .equals(replacement.getVersionNumber())) {
                versionHistory.set(index, replacement);
                return;
            }
        }
        throw new IllegalStateException("Version is missing from history");
    }

    private static <T> List<T> copyHistory(List<T> values, String name) {
        Objects.requireNonNull(values, name + " must not be null");
        List<T> copy = new ArrayList<>(values.size());
        for (T value : values) {
            copy.add(Objects.requireNonNull(value, name + " must not contain null"));
        }
        return List.copyOf(copy);
    }

    private static Map<ProposalVersionNumber, ProposalVersion> validateVersions(
            ProposalId proposalId,
            SalesProjectId salesProjectId,
            List<ProposalVersion> versions) {
        Map<ProposalVersionNumber, ProposalVersion> byNumber = new HashMap<>();
        for (ProposalVersion version : versions) {
            if (byNumber.put(version.getVersionNumber(), version) != null) {
                throw new IllegalArgumentException("Version numbers must be unique");
            }
            ProposalSnapshot snapshot = version.getSnapshot();
            if (!proposalId.equals(snapshot.getProposalId())
                    || !salesProjectId.equals(snapshot.getSalesProjectId())) {
                throw new IllegalArgumentException(
                        "Version snapshot does not belong to proposal");
            }
        }

        if (versions.isEmpty()) {
            return byNumber;
        }

        Set<Integer> sequences = new HashSet<>();
        for (ProposalVersion version : versions) {
            if (!sequences.add(version.getVersionNumber().getSequence())) {
                throw new IllegalArgumentException("Version sequences must be unique");
            }
        }
        for (int sequence = 1; sequence <= versions.size(); sequence++) {
            if (!sequences.contains(sequence)) {
                throw new IllegalArgumentException(
                        "Version sequences must be contiguous from V01");
            }
        }

        for (ProposalVersion version : versions) {
            ProposalVersionNumber replacementNumber =
                    version.getReplacedByVersionNumber();
            if (replacementNumber == null) {
                continue;
            }
            ProposalVersion replacement = byNumber.get(replacementNumber);
            if (replacement == null
                    || replacement.getVersionNumber().getSequence()
                    != version.getVersionNumber().getSequence() + 1) {
                throw new IllegalArgumentException(
                        "Version replacement chain is invalid");
            }
        }
        return byNumber;
    }

    private static ProposalVersion findCurrentVersion(List<ProposalVersion> versions) {
        ProposalVersion current = null;
        for (ProposalVersion version : versions) {
            if (version.getReplacedByVersionNumber() == null) {
                if (current != null) {
                    throw new IllegalArgumentException(
                            "Proposal must have exactly one current version");
                }
                current = version;
            }
        }
        if (!versions.isEmpty() && current == null) {
            throw new IllegalArgumentException(
                    "Proposal must have exactly one current version");
        }
        return current;
    }

    private static ProposalDraft validateDrafts(
            ProposalId proposalId,
            SalesProjectId salesProjectId,
            List<ProposalDraft> drafts,
            Map<ProposalVersionNumber, ProposalVersion> versions,
            ProposalVersion currentVersion) {
        if (drafts.isEmpty()) {
            throw new IllegalArgumentException("Proposal must have at least one draft");
        }

        Set<ProposalDraftId> draftIds = new HashSet<>();
        Set<Integer> lockedSourceSequences = new HashSet<>();
        int lockedDrafts = 0;
        int initialLockedDrafts = 0;
        ProposalDraft editable = null;
        for (ProposalDraft draft : drafts) {
            if (!draftIds.add(draft.getDraftId())) {
                throw new IllegalArgumentException("Draft IDs must be unique");
            }
            if (!proposalId.equals(draft.getProposalId())
                    || !salesProjectId.equals(draft.getSalesProjectId())) {
                throw new IllegalArgumentException(
                        "Draft does not belong to proposal");
            }

            ProposalVersionNumber sourceVersionNumber = draft.getSourceVersionNumber();
            ProposalVersion sourceVersion = sourceVersionNumber == null
                    ? null
                    : versions.get(sourceVersionNumber);
            if (sourceVersionNumber != null
                    && (sourceVersion == null
                    || sourceVersion.getStatus() != ProposalVersionStatus.ISSUED)) {
                throw new IllegalArgumentException(
                        "Draft source version is invalid");
            }

            if (draft.getStatus() == ProposalDraftStatus.EDITABLE) {
                if (editable != null) {
                    throw new IllegalArgumentException(
                            "Proposal must have at most one editable draft");
                }
                editable = draft;
            } else if (draft.getStatus() != ProposalDraftStatus.LOCKED) {
                throw new IllegalArgumentException("Draft status is invalid");
            } else {
                lockedDrafts++;
                if (sourceVersionNumber == null) {
                    initialLockedDrafts++;
                } else if (sourceVersion.getReplacedByVersionNumber() == null
                        || !lockedSourceSequences.add(
                        sourceVersion.getVersionNumber().getSequence())) {
                    throw new IllegalArgumentException(
                            "Locked revision draft must originate from a replaced version");
                }
            }
        }

        if (currentVersion == null) {
            if (editable == null || drafts.size() != 1) {
                throw new IllegalArgumentException(
                        "A proposal without versions must have exactly one editable draft");
            }
            if (editable.getSourceVersionNumber() != null) {
                throw new IllegalArgumentException(
                        "Initial editable draft cannot have a source version");
            }
            return editable;
        }

        if (lockedDrafts != versions.size() || initialLockedDrafts != 1) {
            throw new IllegalArgumentException(
                    "Draft history must contain one locked draft per emitted version");
        }
        for (int sequence = 1; sequence < versions.size(); sequence++) {
            if (!lockedSourceSequences.contains(sequence)) {
                throw new IllegalArgumentException(
                        "Each emitted revision must have a locked source draft");
            }
        }
        if (editable != null) {
            if (currentVersion.getStatus() != ProposalVersionStatus.ISSUED
                    || currentVersion.getReplacedByVersionNumber() != null
                    || !currentVersion.getVersionNumber().equals(
                    editable.getSourceVersionNumber())) {
                throw new IllegalArgumentException(
                        "Editable draft must originate from the current issued version");
            }
        }
        return editable;
    }
}
