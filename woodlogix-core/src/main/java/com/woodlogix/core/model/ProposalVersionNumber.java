package com.woodlogix.core.model;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ProposalVersionNumber {
    private static final Pattern FORMAT = Pattern.compile("V(0[1-9]|[1-9]\\d{1,})");

    private final String value;
    private final int sequence;

    public ProposalVersionNumber(String value) {
        Objects.requireNonNull(value, "value must not be null");
        Matcher matcher = FORMAT.matcher(value);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Proposal version must match V01, V02, etc.");
        }
        try {
            this.sequence = Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Proposal version sequence is too large", exception);
        }
        if (sequence <= 0) {
            throw new IllegalArgumentException("Proposal version sequence must be positive");
        }
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public int getSequence() {
        return sequence;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ProposalVersionNumber version)) {
            return false;
        }
        return value.equals(version.value);
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
