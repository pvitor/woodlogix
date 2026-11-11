package com.woodlogix.core.model;

import com.woodlogix.core.enums.CurrencyCode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommercialTypesTest {
    @Test
    void createsMoneyWithoutRounding() {
        BigDecimal amount = new BigDecimal("12.34567");

        Money money = new Money(amount, CurrencyCode.UYU);

        assertEquals(amount, money.getAmount());
        assertEquals(CurrencyCode.UYU, money.getCurrency());
    }

    @Test
    void comparesMoneyNumericallyRegardlessOfScale() {
        Money oneDecimal = new Money(new BigDecimal("10.0"), CurrencyCode.UYU);
        Money twoDecimals = new Money(new BigDecimal("10.00"), CurrencyCode.UYU);

        assertEquals(oneDecimal, twoDecimals);
        assertEquals(oneDecimal.hashCode(), twoDecimals.hashCode());
        assertEquals(new BigDecimal("10.0"), oneDecimal.getAmount());
        assertEquals(new BigDecimal("10.00"), twoDecimals.getAmount());
    }

    @Test
    void performsMoneyOperationsOnlyWithinSameCurrency() {
        Money uyu = new Money(new BigDecimal("10.25"), CurrencyCode.UYU);
        Money otherUyu = new Money(new BigDecimal("2.10"), CurrencyCode.UYU);

        assertEquals(new Money(new BigDecimal("12.35"), CurrencyCode.UYU), uyu.add(otherUyu));
        assertEquals(new Money(new BigDecimal("8.15"), CurrencyCode.UYU), uyu.subtract(otherUyu));
        assertEquals(new Money(new BigDecimal("20.50"), CurrencyCode.UYU),
                uyu.multiply(new BigDecimal("2")));
        assertThrows(IllegalArgumentException.class, () ->
                uyu.add(new Money(BigDecimal.ONE, CurrencyCode.USD)));
    }

    @Test
    void rejectsMissingMoneyData() {
        assertThrows(NullPointerException.class, () ->
                new Money(null, CurrencyCode.UYU));
        assertThrows(NullPointerException.class, () ->
                new Money(BigDecimal.ONE, null));
        assertThrows(NullPointerException.class, () ->
                new Money(BigDecimal.ONE, CurrencyCode.UYU).multiply(null));
    }

    @Test
    void validatesProposalIdentifiers() {
        assertEquals("PROP-2026-0001", new ProposalId("PROP-2026-0001").getValue());
        assertThrows(IllegalArgumentException.class, () -> new ProposalId("PROP-26-0001"));
        assertThrows(IllegalArgumentException.class, () -> new ProposalId("PROP-2026-0000"));
        assertThrows(IllegalArgumentException.class, () -> new ProposalId("PROP-0000-0001"));

        assertEquals(2, new ProposalVersionNumber("V02").getSequence());
        assertEquals(99, new ProposalVersionNumber("V99").getSequence());
        assertEquals(100, new ProposalVersionNumber("V100").getSequence());
        assertThrows(IllegalArgumentException.class, () -> new ProposalVersionNumber("V1"));
        assertThrows(IllegalArgumentException.class, () -> new ProposalVersionNumber("V001"));
        assertThrows(IllegalArgumentException.class, () -> new ProposalVersionNumber("V0001"));
        assertThrows(IllegalArgumentException.class, () -> new ProposalVersionNumber("V00"));
        assertThrows(IllegalArgumentException.class, () -> new ProposalVersionNumber("V0"));
        assertThrows(IllegalArgumentException.class, () -> new ProposalVersionNumber("V-1"));
    }

    @Test
    void validatesLineIdsAndNormalizesUnits() {
        assertEquals("line-1", new ProposalLineId("line-1").getValue());
        assertThrows(IllegalArgumentException.class, () -> new ProposalLineId(" "));
        assertEquals("UNID", new UnitOfMeasure(" unid ").getCode());
        assertThrows(IllegalArgumentException.class, () -> new UnitOfMeasure(" "));
    }

    @Test
    void acceptsOnlyConfiguredValidityPeriods() {
        assertEquals(15, ValidityPeriod.of(15).getDays());
        assertEquals(30, ValidityPeriod.of(30).getDays());
        assertEquals(45, ValidityPeriod.of(45).getDays());
        assertThrows(IllegalArgumentException.class, () -> ValidityPeriod.of(0));
        assertThrows(IllegalArgumentException.class, () -> ValidityPeriod.of(60));
    }

    @Test
    void validatesCustomerSnapshot() {
        CustomerSnapshot customer = new CustomerSnapshot("customer-1", "Customer");

        assertEquals("customer-1", customer.getCustomerId());
        assertEquals("Customer", customer.getDisplayName());
        assertThrows(IllegalArgumentException.class, () ->
                new CustomerSnapshot("customer-1", " "));
    }

    @Test
    void createsItemSnapshotAndPreservesDecimalValues() {
        ProposalItemSnapshot item = new ProposalItemSnapshot(
                new ProposalLineId("line-1"),
                "MDF panel",
                new BigDecimal("1.250"),
                new UnitOfMeasure("un"),
                new Money(new BigDecimal("10.1234"), CurrencyCode.USD),
                new Money(new BigDecimal("12.65425"), CurrencyCode.USD),
                new PercentageDiscountSnapshot(
                        new BigDecimal("2.50"),
                        new Money(new BigDecimal("0.2500"), CurrencyCode.USD)),
                new Money(new BigDecimal("0.1000"), CurrencyCode.USD),
                new Money(new BigDecimal("12.30425"), CurrencyCode.USD),
                new TaxSnapshot(
                        TaxMode.EXCLUDED,
                        new BigDecimal("18"),
                        new Money(new BigDecimal("12.30425"), CurrencyCode.USD),
                        new Money(new BigDecimal("2.21476"), CurrencyCode.USD)),
                new Money(new BigDecimal("14.51901"), CurrencyCode.USD),
                new CatalogReference(CatalogType.MATERIAL, "mdf-18-white"));

        assertEquals(new BigDecimal("1.250"), item.getQuantity());
        assertEquals(new BigDecimal("10.1234"),
                item.getUnitPriceBeforeDiscountsAndTax().getAmount());
        assertEquals(new BigDecimal("12.65425"), item.getLineSubtotal().getAmount());
        PercentageDiscountSnapshot discount =
                (PercentageDiscountSnapshot) item.getIndividualDiscount();
        assertEquals(new BigDecimal("2.50"), discount.getPercentage());
        assertEquals(new BigDecimal("0.2500"), discount.getAppliedAmount().getAmount());
        assertEquals(new BigDecimal("0.1000"), item.getGeneralDiscountAllocation().getAmount());
        assertEquals(new BigDecimal("12.30425"), item.getTaxableBase().getAmount());
        assertEquals(TaxMode.EXCLUDED, item.getTax().getMode());
        assertEquals(new BigDecimal("18"), item.getTax().getRate());
        assertEquals(new BigDecimal("2.21476"), item.getTax().getTaxAmount().getAmount());
        assertEquals(new BigDecimal("14.51901"), item.getFinalTotal().getAmount());
        assertEquals(CatalogType.MATERIAL, item.getCatalogReference().getCatalogType());
        assertThrows(IllegalArgumentException.class, () ->
                new ProposalItemSnapshot(
                        new ProposalLineId("line-2"),
                        "Invalid",
                        BigDecimal.ZERO,
                        new UnitOfMeasure("un"),
                        new Money(BigDecimal.ONE, CurrencyCode.USD),
                        new Money(BigDecimal.ONE, CurrencyCode.USD),
                        null,
                        new Money(BigDecimal.ZERO, CurrencyCode.USD),
                        new Money(BigDecimal.ONE, CurrencyCode.USD),
                        new TaxSnapshot(
                                TaxMode.INCLUDED,
                                BigDecimal.ZERO,
                                new Money(BigDecimal.ONE, CurrencyCode.USD),
                                new Money(BigDecimal.ZERO, CurrencyCode.USD)),
                        new Money(BigDecimal.ONE, CurrencyCode.USD),
                        null));
        assertThrows(IllegalArgumentException.class, () ->
                new ProposalItemSnapshot(
                        new ProposalLineId("line-3"),
                        "Invalid",
                        BigDecimal.ONE,
                        new UnitOfMeasure("un"),
                        new Money(BigDecimal.ONE, CurrencyCode.USD),
                        new Money(BigDecimal.ONE, CurrencyCode.USD),
                        new FixedDiscountSnapshot(
                                new Money(BigDecimal.ONE, CurrencyCode.USD),
                                new Money(BigDecimal.ONE, CurrencyCode.USD)),
                        new Money(BigDecimal.ZERO, CurrencyCode.USD),
                        new Money(BigDecimal.ONE, CurrencyCode.USD),
                        new TaxSnapshot(
                                TaxMode.INCLUDED,
                                BigDecimal.ZERO,
                                new Money(BigDecimal.ONE, CurrencyCode.USD),
                                new Money(BigDecimal.ZERO, CurrencyCode.USD)),
                        new Money(BigDecimal.ONE, CurrencyCode.UYU),
                        null));
    }

    @Test
    void supportsFixedDiscountsAndOptionalCatalogReferences() {
        FixedDiscountInput input = new FixedDiscountInput(
                new Money(new BigDecimal("5.00"), CurrencyCode.UYU));
        FixedDiscountSnapshot snapshot = new FixedDiscountSnapshot(
                input.getAmount(),
                new Money(new BigDecimal("4.50"), CurrencyCode.UYU));
        TaxInput taxInput = new TaxInput(TaxMode.INCLUDED, new BigDecimal("22"));
        TaxSnapshot includedTax = new TaxSnapshot(
                taxInput.getMode(),
                taxInput.getRate(),
                new Money(new BigDecimal("100"), CurrencyCode.UYU),
                new Money(new BigDecimal("22"), CurrencyCode.UYU));

        assertEquals(new BigDecimal("5.00"), input.getAmount().getAmount());
        assertEquals(new BigDecimal("4.50"), snapshot.getAppliedAmount().getAmount());
        assertEquals(TaxMode.INCLUDED, includedTax.getMode());
        assertEquals(new BigDecimal("22"), includedTax.getRate());
        assertThrows(NullPointerException.class, () ->
                new CatalogReference(null, "material-1"));
        assertThrows(IllegalArgumentException.class, () ->
                new CatalogReference(CatalogType.HARDWARE, " "));
    }

    @Test
    void validatesPercentageDiscountBoundsWithoutChangingScale() {
        assertEquals(new BigDecimal("0"), new PercentageDiscountInput(
                new BigDecimal("0")).getPercentage());
        assertEquals(new BigDecimal("100"), new PercentageDiscountInput(
                new BigDecimal("100")).getPercentage());
        assertEquals(new BigDecimal("100.00"), new PercentageDiscountInput(
                new BigDecimal("100.00")).getPercentage());
        assertEquals(new BigDecimal("0"), new PercentageDiscountSnapshot(
                new BigDecimal("0"),
                new Money(BigDecimal.ONE, CurrencyCode.UYU)).getPercentage());
        assertEquals(new BigDecimal("100.00"), new PercentageDiscountSnapshot(
                new BigDecimal("100.00"),
                new Money(BigDecimal.ONE, CurrencyCode.UYU)).getPercentage());

        assertThrows(IllegalArgumentException.class, () ->
                new PercentageDiscountInput(new BigDecimal("100.01")));
        assertThrows(IllegalArgumentException.class, () ->
                new PercentageDiscountInput(new BigDecimal("100.0100")));
        assertThrows(IllegalArgumentException.class, () ->
                new PercentageDiscountInput(new BigDecimal("101")));
        assertThrows(IllegalArgumentException.class, () ->
                new PercentageDiscountSnapshot(
                        new BigDecimal("100.01"),
                        new Money(BigDecimal.ONE, CurrencyCode.UYU)));
        assertThrows(IllegalArgumentException.class, () ->
                new PercentageDiscountSnapshot(
                        new BigDecimal("100.0100"),
                        new Money(BigDecimal.ONE, CurrencyCode.UYU)));
        assertThrows(IllegalArgumentException.class, () ->
                new PercentageDiscountSnapshot(
                        new BigDecimal("101"),
                        new Money(BigDecimal.ONE, CurrencyCode.UYU)));
    }

    @Test
    void allowsAnItemWithoutIndividualDiscountOrCatalogReference() {
        ProposalItemSnapshot item = new ProposalItemSnapshot(
                new ProposalLineId("line-no-discount"),
                "Service",
                BigDecimal.ONE,
                new UnitOfMeasure("un"),
                new Money(new BigDecimal("100"), CurrencyCode.UYU),
                new Money(new BigDecimal("100"), CurrencyCode.UYU),
                null,
                new Money(BigDecimal.ZERO, CurrencyCode.UYU),
                new Money(new BigDecimal("100"), CurrencyCode.UYU),
                new TaxSnapshot(
                        TaxMode.INCLUDED,
                        BigDecimal.ZERO,
                        new Money(new BigDecimal("100"), CurrencyCode.UYU),
                        new Money(BigDecimal.ZERO, CurrencyCode.UYU)),
                new Money(new BigDecimal("100"), CurrencyCode.UYU),
                null);

        assertNull(item.getIndividualDiscount());
        assertNull(item.getCatalogReference());
    }

    @Test
    void validatesTaxAndDiscountCurrencies() {
        assertThrows(IllegalArgumentException.class, () ->
                new FixedDiscountSnapshot(
                        new Money(BigDecimal.ONE, CurrencyCode.USD),
                        new Money(BigDecimal.ONE, CurrencyCode.UYU)));
        assertThrows(IllegalArgumentException.class, () ->
                new TaxSnapshot(
                        TaxMode.INCLUDED,
                        BigDecimal.ONE,
                        new Money(BigDecimal.ONE, CurrencyCode.USD),
                        new Money(BigDecimal.ONE, CurrencyCode.UYU)));
        assertThrows(IllegalArgumentException.class, () ->
                new PercentageDiscountInput(new BigDecimal("-1")));
        assertThrows(IllegalArgumentException.class, () ->
                new TaxInput(TaxMode.INCLUDED, new BigDecimal("-1")));
    }

    @Test
    void commercialValueObjectsAreImmutable() {
        assertFinalFields(Money.class);
        assertFinalFields(ProposalId.class);
        assertFinalFields(ProposalVersionNumber.class);
        assertFinalFields(ProposalLineId.class);
        assertFinalFields(UnitOfMeasure.class);
        assertFinalFields(ValidityPeriod.class);
        assertFinalFields(CustomerSnapshot.class);
        assertFinalFields(ProposalItemSnapshot.class);
        assertFinalFields(PercentageDiscountInput.class);
        assertFinalFields(FixedDiscountInput.class);
        assertFinalFields(PercentageDiscountSnapshot.class);
        assertFinalFields(FixedDiscountSnapshot.class);
        assertFinalFields(TaxInput.class);
        assertFinalFields(TaxSnapshot.class);
        assertFinalFields(CatalogReference.class);
        assertTrue(Modifier.isFinal(Money.class.getModifiers()));
    }

    private static void assertFinalFields(Class<?> type) {
        for (Field field : type.getDeclaredFields()) {
            if (!field.isSynthetic()) {
                assertTrue(Modifier.isFinal(field.getModifiers()),
                        () -> type.getSimpleName() + "." + field.getName() + " must be final");
            }
        }
    }
}
