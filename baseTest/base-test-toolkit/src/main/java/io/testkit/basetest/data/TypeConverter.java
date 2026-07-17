package io.testkit.basetest.data;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.testkit.basetest.config.ConfigLoader;

import java.lang.reflect.Type;

public final class TypeConverter {
    private static final ObjectMapper MAPPER = ConfigLoader.jsonMapper();

    private TypeConverter() {
    }

    public static Object convert(Object value, Type targetType) {
        if (value == null) {
            return null;
        }
        JavaType javaType = MAPPER.getTypeFactory().constructType(targetType);
        return MAPPER.convertValue(value, javaType);
    }
}
