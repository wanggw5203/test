package io.testkit.basetest.assertion;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;

/**
 * Focused structural assertions using JSON Pointer; errors exclude response values. Not JSON
 * Schema.
 */
public final class JsonContract {
  public enum Type {
    OBJECT,
    ARRAY,
    STRING,
    INTEGER,
    NUMBER,
    BOOLEAN,
    NULL
  }

  private final JsonNode root;

  private JsonContract(JsonNode root) {
    this.root = Objects.requireNonNull(root);
  }

  public static JsonContract of(JsonNode root) {
    return new JsonContract(root);
  }

  public JsonContract required(String pointer, Type type) {
    Objects.requireNonNull(type);
    JsonNode value = at(pointer);
    boolean matches =
        switch (type) {
          case OBJECT -> value.isObject();
          case ARRAY -> value.isArray();
          case STRING -> value.isTextual();
          case INTEGER -> value.isIntegralNumber();
          case NUMBER -> value.isNumber();
          case BOOLEAN -> value.isBoolean();
          case NULL -> value.isNull();
        };
    if (!matches) throw new AssertionError("Expected " + type + " at JSON Pointer: " + pointer);
    return this;
  }

  public JsonContract nonBlank(String pointer) {
    JsonNode value = at(pointer);
    if (!value.isTextual() || value.textValue().isBlank())
      throw new AssertionError("Expected non-blank string at JSON Pointer: " + pointer);
    return this;
  }

  public JsonContract arraySize(String pointer, int min, int max) {
    if (min < 0 || max < min) throw new IllegalArgumentException("Invalid array size bounds");
    JsonNode value = at(pointer);
    if (!value.isArray() || value.size() < min || value.size() > max)
      throw new AssertionError(
          "Array size outside [" + min + ", " + max + "] at JSON Pointer: " + pointer);
    return this;
  }

  private JsonNode at(String pointer) {
    JsonNode value = root.at(JsonPointer.compile(Objects.requireNonNull(pointer)));
    if (value.isMissingNode())
      throw new AssertionError("Missing required JSON Pointer: " + pointer);
    return value;
  }
}
