package io.testkit.basetest.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;

import java.io.IOException;

/** Prevents JSON coercion such as string "1" being accepted as an integer. */
public final class StrictTypeModule extends SimpleModule {
    public StrictTypeModule() {
        addDeserializer(String.class, scalar(String.class, JsonToken.VALUE_STRING, JsonParser::getText));
        addDeserializer(Integer.class, scalar(Integer.class, JsonToken.VALUE_NUMBER_INT, JsonParser::getIntValue));
        addDeserializer(int.class, scalar(Integer.class, JsonToken.VALUE_NUMBER_INT, JsonParser::getIntValue));
        addDeserializer(Long.class, scalar(Long.class, JsonToken.VALUE_NUMBER_INT, JsonParser::getLongValue));
        addDeserializer(long.class, scalar(Long.class, JsonToken.VALUE_NUMBER_INT, JsonParser::getLongValue));
        addDeserializer(Float.class, number(Float.class, parser -> parser.getNumberValue().floatValue()));
        addDeserializer(float.class, number(Float.class, parser -> parser.getNumberValue().floatValue()));
        addDeserializer(Double.class, number(Double.class, parser -> parser.getNumberValue().doubleValue()));
        addDeserializer(double.class, number(Double.class, parser -> parser.getNumberValue().doubleValue()));
        addDeserializer(Boolean.class, booleanValue(Boolean.class));
        addDeserializer(boolean.class, booleanValue(Boolean.class));
    }

    private static <T> StdDeserializer<T> scalar(Class<T> type, JsonToken token,
                                                  Reader<T> reader) {
        return new StdDeserializer<>(type) {
            @Override
            public T deserialize(JsonParser parser, DeserializationContext context) throws IOException {
                if (parser.currentToken() != token) throw mismatch(parser, type);
                return reader.read(parser);
            }
        };
    }

    private static <T> StdDeserializer<T> number(Class<T> type, Reader<T> reader) {
        return new StdDeserializer<>(type) {
            @Override
            public T deserialize(JsonParser parser, DeserializationContext context) throws IOException {
                if (!parser.currentToken().isNumeric()) throw mismatch(parser, type);
                return reader.read(parser);
            }
        };
    }

    private static <T> StdDeserializer<T> booleanValue(Class<T> type) {
        return new StdDeserializer<>(type) {
            @Override
            @SuppressWarnings("unchecked")
            public T deserialize(JsonParser parser, DeserializationContext context) throws IOException {
                if (parser.currentToken() != JsonToken.VALUE_TRUE
                        && parser.currentToken() != JsonToken.VALUE_FALSE) {
                    throw mismatch(parser, type);
                }
                return (T) Boolean.valueOf(parser.getBooleanValue());
            }
        };
    }

    private static JsonMappingException mismatch(JsonParser parser, Class<?> type) {
        return JsonMappingException.from(parser,
                "Expected " + type.getSimpleName() + " but found " + parser.currentToken());
    }

    @FunctionalInterface
    private interface Reader<T> {
        T read(JsonParser parser) throws IOException;
    }
}
