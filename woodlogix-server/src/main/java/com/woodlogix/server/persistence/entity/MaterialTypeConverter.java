package com.woodlogix.server.persistence.entity;

import com.woodlogix.core.enums.MaterialType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class MaterialTypeConverter implements AttributeConverter<MaterialType, String> {
    @Override
    public String convertToDatabaseColumn(MaterialType materialType) {
        return materialType == null ? null : materialType.name();
    }

    @Override
    public MaterialType convertToEntityAttribute(String value) {
        if ("COMPENSADO".equals(value)) {
            return MaterialType.PLYWOOD;
        }
        return value == null ? null : MaterialType.valueOf(value);
    }
}
