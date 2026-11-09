package com.woodlogix.application.material;

import java.math.BigDecimal;

public record MaterialRow(String id, String name, String color, BigDecimal thicknessMm,
                          BigDecimal lengthMm, BigDecimal widthMm, BigDecimal pricePerSheet) {
}
