package com.woodlogix.server.persistence;

import com.woodlogix.core.enums.MaterialType;
import com.woodlogix.server.persistence.entity.LocalDateTimeText;
import com.woodlogix.server.persistence.entity.MaterialTypeConverter;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PersistenceConvertersTest {
    @Test
    void materialTypeConverterPreservesNamesAndLegacyAlias() {
        MaterialTypeConverter converter = new MaterialTypeConverter();

        assertEquals("MDF", converter.convertToDatabaseColumn(MaterialType.MDF));
        assertEquals(MaterialType.MDF, converter.convertToEntityAttribute("MDF"));
        assertEquals(MaterialType.PLYWOOD, converter.convertToEntityAttribute("COMPENSADO"));
        assertNull(converter.convertToDatabaseColumn(null));
        assertNull(converter.convertToEntityAttribute(null));
    }

    @Test
    void localDateTimeTextPreservesIsoAndLegacySpaceSeparatedValues() {
        LocalDateTime value = LocalDateTime.of(2026, 11, 9, 22, 8, 48, 412_000_000);

        assertEquals(value, LocalDateTimeText.parse(LocalDateTimeText.format(value)));
        assertEquals(value, LocalDateTimeText.parse("2026-11-09 22:08:48.412"));
        assertNull(LocalDateTimeText.parse(null));
        assertNull(LocalDateTimeText.parse(" "));
        assertThrows(IllegalStateException.class,
                () -> LocalDateTimeText.parse("not-a-date"));
    }
}
