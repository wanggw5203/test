package io.testkit.basetest.enterprise;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the stable identity, ownership and execution classification of a test.
 *
 * <p>Method owner, team and service values override nonblank class defaults. A method ID is used
 * literally; otherwise the class ID is suffixed with the Java method name. Method-level
 * classification takes precedence when a method annotation is present, while a destructive class
 * cannot be made safe by a method override. Resolution is performed by {@link
 * CaseDefinition#resolve}. Prefer explicit method IDs to keep identity stable through Java
 * refactoring.
 *
 * <p>Do not combine this annotation with {@link CaseInfo} on the same element. The two forms may be
 * used on different elements during gradual migration.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Inherited
public @interface TestCaseMetadata {
  /** Stable logical ID, or class prefix when declared on a test class. */
  String id() default "";

  /** Accountable owner identifier; use a durable role or team directory ID. */
  String owner() default "";

  /** Owning team identifier, matching the execution team. */
  String team() default "";

  /** Logical target service identifier, matching the execution policy. */
  String service() default "";

  /** Execution classification used by the {@code testkit.level} selector. */
  CaseLevel level() default CaseLevel.REGRESSION;

  /** Whether execution requires explicit destructive-operation permission. */
  boolean destructive() default false;
}
