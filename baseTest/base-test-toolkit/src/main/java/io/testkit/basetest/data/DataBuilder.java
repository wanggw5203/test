package io.testkit.basetest.data;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks a reusable data-construction method that can be exposed as a TestNG test adapter. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface DataBuilder {
    String description() default "";
    String[] groups() default {};
    boolean enabled() default true;
    int priority() default 0;
    String dataProvider() default "";
    Class<?> dataProviderClass() default Object.class;
}
