package org.xcore.cloud.mindustry.selector.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Forbids any target selectors from being used on this command or parameter.
 * Used for critical commands such as /kick, /ban, /admin.
 */
@Target({ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface DenySelectors {
    String reason() default "This command requires an explicit, individual player name.";
}
