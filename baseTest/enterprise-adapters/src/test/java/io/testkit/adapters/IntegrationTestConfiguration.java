package io.testkit.adapters;

/** Explicit configuration for opt-in tests against dedicated external integration resources. */
final class IntegrationTestConfiguration {
  private IntegrationTestConfiguration() {}

  static String requiredVariable(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank())
      throw new IllegalArgumentException("Required integration variable: " + name);
    return value;
  }
}
