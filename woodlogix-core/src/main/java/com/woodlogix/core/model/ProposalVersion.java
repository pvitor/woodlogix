package com.woodlogix.core.model;

import java.util.Objects;

public final class ProposalVersion {
    private final ProposalVersionNumber versionNumber;
    private final ProposalSnapshot snapshot;
    private final ProposalVersionStatus status;
    private final ProposalVersionNumber replacedByVersionNumber;
    private final SaleId saleId;

    public ProposalVersion(
            ProposalVersionNumber versionNumber,
            ProposalSnapshot snapshot,
            ProposalVersionStatus status,
            ProposalVersionNumber replacedByVersionNumber,
            SaleId saleId) {
        this.versionNumber = Objects.requireNonNull(
                versionNumber, "versionNumber must not be null");
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot must not be null");
        if (!versionNumber.equals(snapshot.getVersionNumber())) {
            throw new IllegalArgumentException("Version number must match snapshot");
        }
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.replacedByVersionNumber = replacedByVersionNumber;
        this.saleId = saleId;
        if (replacedByVersionNumber != null
                && replacedByVersionNumber.equals(versionNumber)) {
            throw new IllegalArgumentException("A version cannot replace itself");
        }
        if (replacedByVersionNumber != null
                && status != ProposalVersionStatus.ISSUED) {
            throw new IllegalArgumentException(
                    "Only an issued version can have a replacement");
        }
        if (saleId != null && status != ProposalVersionStatus.ACCEPTED) {
            throw new IllegalArgumentException("Sale can only be linked to an accepted version");
        }
    }

    public ProposalVersionNumber getVersionNumber() {
        return versionNumber;
    }

    public ProposalSnapshot getSnapshot() {
        return snapshot;
    }

    public ProposalVersionStatus getStatus() {
        return status;
    }

    public ProposalVersionNumber getReplacedByVersionNumber() {
        return replacedByVersionNumber;
    }

    public SaleId getSaleId() {
        return saleId;
    }

    ProposalVersion withReplacement(ProposalVersionNumber replacement) {
        if (status != ProposalVersionStatus.ISSUED
                || replacedByVersionNumber != null) {
            throw new IllegalStateException("Only an unreplaced issued version can be replaced");
        }
        return new ProposalVersion(
                versionNumber,
                snapshot,
                status,
                Objects.requireNonNull(replacement, "replacement must not be null"),
                saleId);
    }

    ProposalVersion withStatus(ProposalVersionStatus newStatus, SaleId newSaleId) {
        return new ProposalVersion(
                versionNumber,
                snapshot,
                Objects.requireNonNull(newStatus, "newStatus must not be null"),
                replacedByVersionNumber,
                newSaleId);
    }

    ProposalVersion withSale(SaleId newSaleId) {
        if (status != ProposalVersionStatus.ACCEPTED) {
            throw new IllegalStateException("Sale can only be linked to an accepted version");
        }
        if (saleId != null) {
            throw new IllegalStateException("A version can only be linked to one sale");
        }
        return new ProposalVersion(
                versionNumber,
                snapshot,
                status,
                replacedByVersionNumber,
                Objects.requireNonNull(newSaleId, "saleId must not be null"));
    }
}
