package org.xcore.plugin.cloud.annotation;

import io.leangen.geantyref.TypeToken;
import org.incendo.cloud.parser.ParserParameter;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface AllowNegativeDuration {
    ParserParameter<Boolean> PARAM = new ParserParameter<>("allow_negative_duration", TypeToken.get(Boolean.class));
}