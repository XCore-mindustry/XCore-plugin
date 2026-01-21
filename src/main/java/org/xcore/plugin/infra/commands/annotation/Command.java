package org.xcore.plugin.infra.commands.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Command {
    String name();
    String params() default "";
    String description() default "";
    String[] aliases() default {};
}