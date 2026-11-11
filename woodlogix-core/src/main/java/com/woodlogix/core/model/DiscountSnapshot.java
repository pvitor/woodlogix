package com.woodlogix.core.model;

public sealed interface DiscountSnapshot
        permits PercentageDiscountSnapshot, FixedDiscountSnapshot {
}
