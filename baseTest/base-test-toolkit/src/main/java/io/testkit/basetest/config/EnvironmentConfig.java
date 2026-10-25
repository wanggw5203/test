package io.testkit.basetest.config;

import java.nio.file.Path;
import java.util.*;
import java.util.regex.*;

/** Common -> named profile -> TEST_ATTR_* -> test.attr.*. Unknown profiles fail closed. */
public record EnvironmentConfig(String name, Map<String, String> values) {
  public EnvironmentConfig {
    values = Map.copyOf(values);
  }

  public String require(String key) {
    String value = values.get(key);
    if (value == null || value.isBlank())
      throw new IllegalArgumentException("Missing configuration key: " + key);
    return value;
  }

  public int positiveInt(String key) {
    int value = Integer.parseInt(require(key));
    if (value <= 0) throw new IllegalArgumentException("Expected positive configuration: " + key);
    return value;
  }

  public static EnvironmentConfig load(Path file) {
    Map<String, String> properties = new HashMap<>();
    System.getProperties()
        .stringPropertyNames()
        .forEach(k -> properties.put(k, System.getProperty(k)));
    String profile =
        properties.getOrDefault("test.env", System.getenv().getOrDefault("TEST_ENV", "local"));
    return load(file, profile, System.getenv(), properties);
  }

  public static EnvironmentConfig load(
      Path file, String profile, Map<String, String> env, Map<String, String> properties) {
    return load(file, profile, env, properties, Set.of());
  }

  /** Non-empty capabilities selects only those top-level sections before resolving secrets. */
  public static EnvironmentConfig load(
      Path file,
      String profile,
      Map<String, String> env,
      Map<String, String> properties,
      Set<String> capabilities) {
    Objects.requireNonNull(capabilities);
    capabilities.forEach(
        c -> {
          if (c == null || !c.matches("[a-z][a-z0-9-]*"))
            throw new IllegalArgumentException("Expected top-level capability name");
        });
    var root = ConfigLoader.read(file, com.fasterxml.jackson.databind.JsonNode.class);
    var selected = root.path("environments").path(profile);
    if (!selected.isObject()) throw new IllegalArgumentException("Unknown environment: " + profile);
    Map<String, String> values = new LinkedHashMap<>();
    flatten(root.path("common"), "", values);
    flatten(selected, "", values);
    env.forEach(
        (key, value) -> {
          if (key.startsWith("TEST_ATTR_"))
            values.put(key.substring(10).toLowerCase(Locale.ROOT).replace('_', '.'), value);
        });
    properties.forEach(
        (key, value) -> {
          if (key.startsWith("test.attr.")) values.put(key.substring(10), value);
        });
    if (!capabilities.isEmpty())
      values
          .keySet()
          .removeIf(
              key ->
                  capabilities.stream().noneMatch(c -> key.equals(c) || key.startsWith(c + ".")));
    values.replaceAll((key, value) -> resolve(value, env, key));
    return new EnvironmentConfig(profile, values);
  }

  private static void flatten(
      com.fasterxml.jackson.databind.JsonNode node, String prefix, Map<String, String> output) {
    if (node.isMissingNode()) return;
    if (!node.isObject())
      throw new IllegalArgumentException("Expected configuration object: " + prefix);
    node.fields()
        .forEachRemaining(
            e -> {
              String key = prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey();
              if (e.getValue().isObject()) flatten(e.getValue(), key, output);
              else if (e.getValue().isValueNode() && !e.getValue().isNull())
                output.put(key, e.getValue().asText());
              else throw new IllegalArgumentException("Expected scalar configuration: " + key);
            });
  }

  private static String resolve(String value, Map<String, String> env, String key) {
    Matcher matcher = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)}").matcher(value);
    StringBuffer output = new StringBuffer();
    while (matcher.find()) {
      String replacement = env.get(matcher.group(1));
      if (replacement == null)
        throw new IllegalArgumentException(
            "Missing environment variable for configuration key: " + key);
      matcher.appendReplacement(output, Matcher.quoteReplacement(replacement));
    }
    matcher.appendTail(output);
    return output.toString();
  }

  @Override
  public String toString() {
    return "EnvironmentConfig[name=" + name + ", keys=" + values.keySet() + "]";
  }
}
