package com.woodlogix.core.model;

public sealed interface DiscountInput
        permits PercentageDiscountInput, FixedDiscountInput {
}
