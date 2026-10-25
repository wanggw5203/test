package io.testkit.basetest.enterprise;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.Objects;

/**
 * Validated logical test identity used for admission, sharding and reporting.
 *
 * <p>IDs contain 1-128 ASCII letters, digits, dots, underscores or hyphens and start with a letter
 * or digit. Ownership and service fields use the shorter {@link RunMetadata} identifier contract.
 * DataProvider invocation identity is separate from this method-level definition.
 *
 * @param id stable logical ID, unchanged when a Java method is renamed
 * @param owner accountable owner identifier
 * @param team accountable team identifier
 * @param service logical target service identifier
 * @param level smoke or regression classification
 * @param destructive whether explicit destructive-operation permission is needed
 */
public record CaseDefinition(
    String id, String owner, String team, String service, CaseLevel level, boolean destructive) {
  public CaseDefinition {
    validateId(id);
    RunMetadata.token(owner, "owner");
    RunMetadata.token(team, "team");
    RunMetadata.token(service, "service");
    Objects.requireNonNull(level, "level");
  }

  /**
   * Resolves method metadata over the nearest annotated class declaration.
   *
   * <p>New {@link TestCaseMetadata} and legacy {@link CaseInfo} declarations share the same
   * precedence rules. Both forms on one declaration are rejected, including when values happen to
   * agree. Class inheritance selects the closest annotated class, allowing one class at a time to
   * migrate.
   *
   * @throws IllegalArgumentException if required metadata is missing, invalid or declared using
   *     both annotation forms
   */
  public static CaseDefinition resolve(Method method, Class<?> testClass) {
    Objects.requireNonNull(method, "method");
    Objects.requireNonNull(testClass, "testClass");
    Metadata local = Metadata.from(method);
    Metadata type = classMetadata(testClass);
    if (local == null && type == null) {
      throw new IllegalArgumentException(
          "Enterprise tests require @TestCaseMetadata: " + method.getName());
    }

    String id =
        local != null && !local.id().isBlank()
            ? local.id()
            : type == null || type.id().isBlank() ? "" : type.id() + "." + method.getName();
    return new CaseDefinition(
        id,
        choose(local == null ? "" : local.owner(), type == null ? "" : type.owner()),
        choose(local == null ? "" : local.team(), type == null ? "" : type.team()),
        choose(local == null ? "" : local.service(), type == null ? "" : type.service()),
        local == null ? type.level() : local.level(),
        (local != null && local.destructive()) || (type != null && type.destructive()));
  }

  static void validateId(String id) {
    if (id == null || !id.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")) {
      throw new IllegalArgumentException("Missing or invalid logical case ID");
    }
  }

  private static Metadata classMetadata(Class<?> testClass) {
    for (Class<?> current = testClass; current != null; current = current.getSuperclass()) {
      Metadata metadata = Metadata.from(current);
      if (metadata != null) {
        return metadata;
      }
    }
    return null;
  }

  private static String choose(String local, String type) {
    return local.isBlank() ? type : local;
  }

  @SuppressWarnings("deprecation")
  private record Metadata(
      String id, String owner, String team, String service, CaseLevel level, boolean destructive) {
    private static Metadata from(AnnotatedElement element) {
      TestCaseMetadata current = element.getDeclaredAnnotation(TestCaseMetadata.class);
      CaseInfo legacy = element.getDeclaredAnnotation(CaseInfo.class);
      if (current != null && legacy != null) {
        throw new IllegalArgumentException(
            "Declare only @TestCaseMetadata; do not combine it with @CaseInfo");
      }
      if (current != null) {
        return new Metadata(
            current.id(),
            current.owner(),
            current.team(),
            current.service(),
            current.level(),
            current.destructive());
      }
      return legacy == null
          ? null
          : new Metadata(
              legacy.id(),
              legacy.owner(),
              legacy.team(),
              legacy.service(),
              legacy.level(),
              legacy.destructive());
    }
  }
}
