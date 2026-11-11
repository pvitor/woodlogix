package com.woodlogix.core.model;

import java.util.Objects;
import java.util.regex.Pattern;

public final class ProposalId {
    private static final Pattern FORMAT = Pattern.compile("PROP-\\d{4}-\\d{4}");

    private final String value;

    public ProposalId(String value) {
        Objects.requireNonNull(value, "value must not be null");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Proposal ID must match PROP-AAAA-NNNN");
        }
        if (value.substring(5, 9).equals("0000")) {
            throw new IllegalArgumentException("Proposal year must be positive");
        }
        if (value.substring(10).equals("0000")) {
            throw new IllegalArgumentException("Proposal sequence must be positive");
        }
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ProposalId proposalId)) {
            return false;
        }
        return value.equals(proposalId.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
