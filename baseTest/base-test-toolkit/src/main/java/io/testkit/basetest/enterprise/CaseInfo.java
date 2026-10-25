package io.testkit.basetest.enterprise;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Legacy metadata declaration retained for source and binary compatibility.
 *
 * @deprecated Use {@link TestCaseMetadata} with the same field values. Migrate one declaration at a
 *     time; declaring both annotations on the same class or method is rejected. Stable {@code id}
 *     values must remain unchanged during migration.
 */
@Deprecated(since = "1.1.0", forRemoval = false)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Inherited
public @interface CaseInfo {
  String id() default "";

  String owner() default "";

  String team() default "";

  String service() default "";

  CaseLevel level() default CaseLevel.REGRESSION;

  boolean destructive() default false;
}
