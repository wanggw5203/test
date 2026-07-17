package io.testkit.basetest.config;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public record TestEnvironment(String name, Map<String, String> attributes) {
    public TestEnvironment {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("environment name must not be blank");
        }
        attributes = Map.copyOf(attributes == null ? Map.of() : attributes);
    }

    public static TestEnvironment current() {
        String name = firstNonBlank(
                System.getProperty("test.env"),
                System.getenv("TEST_ENV"),
                "local");
        Map<String, String> attributes = new LinkedHashMap<>();
        System.getenv().forEach((key, value) -> {
            if (key.startsWith("TEST_ATTR_")) {
                String normalized = key.substring("TEST_ATTR_".length())
                        .toLowerCase(Locale.ROOT)
                        .replace('_', '.');
                attributes.put(normalized, value);
            }
        });
        System.getProperties().stringPropertyNames().stream()
                .filter(key -> key.startsWith("test.attr."))
                .forEach(key -> attributes.put(key.substring("test.attr.".length()),
                        System.getProperty(key)));
        return new TestEnvironment(name, attributes);
    }

    public Optional<String> attribute(String key) {
        return Optional.ofNullable(attributes.get(key));
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        throw new IllegalArgumentException("No non-blank value supplied");
    }
}
