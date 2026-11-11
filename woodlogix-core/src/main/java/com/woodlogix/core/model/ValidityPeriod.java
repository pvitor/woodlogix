package com.woodlogix.core.model;

public final class ValidityPeriod {
    private final int days;

    private ValidityPeriod(int days) {
        this.days = days;
    }

    public static ValidityPeriod of(int days) {
        if (days != 15 && days != 30 && days != 45) {
            throw new IllegalArgumentException("Validity period must be 15, 30, or 45 days");
        }
        return new ValidityPeriod(days);
    }

    public int getDays() {
        return days;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ValidityPeriod period)) {
            return false;
        }
        return days == period.days;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(days);
    }

    @Override
    public String toString() {
        return days + " days";
    }
}
