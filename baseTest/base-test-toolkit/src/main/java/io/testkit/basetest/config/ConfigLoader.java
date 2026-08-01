package io.testkit.basetest.config;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

public final class ConfigLoader {
    private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
            .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
            .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
            .build())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .findAndRegisterModules();
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .findAndRegisterModules();

    private ConfigLoader() {
    }

    public static <T> T read(Path path, Class<T> type) {
        Objects.requireNonNull(path, "path");
        try (InputStream input = Files.newInputStream(path)) {
            return mapper(path.getFileName().toString()).readValue(input, type);
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot read configuration: " + path, e);
        }
    }

    public static <T> T read(Path path, JavaType type) {
        Objects.requireNonNull(path, "path");
        try (InputStream input = Files.newInputStream(path)) {
            return mapper(path.getFileName().toString()).readValue(input, type);
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot read configuration: " + path, e);
        }
    }

    public static <T> T readResource(String resource, Class<T> type) {
        String normalized = resource.startsWith("/") ? resource.substring(1) : resource;
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        try (InputStream input = loader.getResourceAsStream(normalized)) {
            if (input == null) {
                throw new IllegalArgumentException("Classpath resource not found: " + resource);
            }
            return mapper(resource).readValue(input, type);
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot read classpath resource: " + resource, e);
        }
    }

    public static ObjectMapper mapper(String sourceName) {
        String lower = sourceName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".yaml") || lower.endsWith(".yml")) {
            return YAML;
        }
        if (lower.endsWith(".json") || lower.endsWith(".json5")) {
            return JSON;
        }
        throw new IllegalArgumentException("Supported formats are YAML, JSON and JSON5: " + sourceName);
    }

    public static ObjectMapper jsonMapper() {
        return JSON.copy();
    }

    public static ObjectMapper strictJsonMapper() {
        return JSON.copy().registerModule(new StrictTypeModule());
    }
}
