package com.agentframework.data.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.sql.Array;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * JPA Converter for PostgreSQL TEXT[] arrays to Java List<String>.
 */
@Converter
public class StringArrayConverter implements AttributeConverter<List<String>, Object> {

    @Override
    public Object convertToDatabaseColumn(List<String> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return new String[]{};
        }
        return attribute.toArray(new String[0]);
    }

    @Override
    public List<String> convertToEntityAttribute(Object dbData) {
        if (dbData == null) {
            return new ArrayList<>();
        }
        
        if (dbData instanceof Array) {
            try {
                String[] array = (String[]) ((Array) dbData).getArray();
                return new ArrayList<>(Arrays.asList(array));
            } catch (SQLException e) {
                throw new RuntimeException("Failed to convert SQL Array to List", e);
            }
        }
        
        if (dbData instanceof String[]) {
            return new ArrayList<>(Arrays.asList((String[]) dbData));
        }
        
        return new ArrayList<>();
    }
}
