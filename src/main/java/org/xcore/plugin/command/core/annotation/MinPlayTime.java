package org.xcore.plugin.command.core.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MinPlayTime {
    PlayTimeLimit value() default PlayTimeLimit.CUSTOM;

    int minutes() default 0;

    String errorKey();
}