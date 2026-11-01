package org.xcore.testkit.fixtures.junit;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Gives each test a fresh {@link org.xcore.testkit.fixtures.HeadlessWorld}, closed after it.
 *
 * <p>The annotation registers {@link HeadlessWorldExtension} itself, so a separate
 * {@code @ExtendWith(HeadlessWorldExtension.class)} is not needed (and is harmless where it is
 * still present). A method-level annotation wins over the class-level one; a {@code @Nested}
 * class without its own annotation uses its enclosing class's.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@ExtendWith(HeadlessWorldExtension.class)
public @interface WithHeadlessWorld {
    int width() default 32;
    int height() default 32;

    /** Same as {@link org.xcore.testkit.fixtures.HeadlessWorld.Builder#generating(boolean)}. */
    boolean generating() default false;
}
