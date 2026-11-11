package com.woodlogix.core.model;

public final class ProposalDraftRevision {
    private final long value;

    private ProposalDraftRevision(long value) {
        if (value <= 0) {
            throw new IllegalArgumentException("Draft revision must be positive");
        }
        this.value = value;
    }

    public static ProposalDraftRevision initial() {
        return new ProposalDraftRevision(1);
    }

    public static ProposalDraftRevision of(long value) {
        return new ProposalDraftRevision(value);
    }

    public ProposalDraftRevision next() {
        if (value == Long.MAX_VALUE) {
            throw new IllegalStateException("Draft revision overflow");
        }
        return new ProposalDraftRevision(value + 1);
    }

    public long getValue() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ProposalDraftRevision revision
                && value == revision.value;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(value);
    }

    @Override
    public String toString() {
        return Long.toString(value);
    }
}
