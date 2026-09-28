package org.xcore.testkit.fixtures.junit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Configuration annotation for tests using {@link HeadlessWorldExtension}.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface WithHeadlessWorld {
    int width() default 32;
    int height() default 32;
}
