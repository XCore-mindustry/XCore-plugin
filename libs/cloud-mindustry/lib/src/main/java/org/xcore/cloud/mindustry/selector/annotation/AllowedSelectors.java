package org.xcore.cloud.mindustry.selector.annotation;

import org.xcore.cloud.mindustry.selector.SelectorKind;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Restricts which target selectors are permitted for this command or parameter.
 */
@Target({ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface AllowedSelectors {
    SelectorKind[] value() default {SelectorKind.SELF, SelectorKind.NEAREST_PLAYER};
}
