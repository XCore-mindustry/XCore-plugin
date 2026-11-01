package org.xcore.plugin.cloud.annotation;

import io.leangen.geantyref.TypeToken;
import org.incendo.cloud.parser.ParserParameter;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface DefaultUnit {
    ParserParameter<TimeUnit> PARAM = new ParserParameter<>("default_unit", TypeToken.get(TimeUnit.class));

    TimeUnit value();
}
