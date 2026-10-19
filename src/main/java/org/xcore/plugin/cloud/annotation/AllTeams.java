package org.xcore.plugin.cloud.annotation;

import org.incendo.cloud.parser.ParserParameter;
import org.xcore.cloud.mindustry.MindustryCommandManager;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Lets a {@link mindustry.game.Team} argument accept every team id, not only the base teams.
 */
@Target({ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface AllTeams {
    ParserParameter<Boolean> PARAM = MindustryCommandManager.ALL_TEAMS;
}
