package org.xcore.cloud.mindustry.annotation;

import org.xcore.cloud.mindustry.MindustryCommandManager;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a command, or every command of a class, as usable by in-game players only. Anyone else
 * gets the {@code mindustry.sender.player_required} caption and the handler is not called.
 * <p>
 * Works with any sender type, because the check goes through the manager's sender mapper. Needs
 * {@link MindustryCommandManager#registerMindustryAnnotations}; with the builder API set
 * {@link MindustryCommandManager#PLAYER_ONLY} instead.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface PlayerOnly {
}
