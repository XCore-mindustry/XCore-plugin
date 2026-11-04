package org.xcore.cloud.mindustry.parser;

import org.incendo.cloud.context.CommandInput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerNameInputTest {

    private static final List<String> NAMES = List.of("[gold]Big Bob", "Big Bob Jr", "alice");

    @Test
    @DisplayName("the longest run of words that is a known name is read, and the rest is left")
    void longestKnownName() {
        CommandInput input = CommandInput.of("Big Bob Jr hello there");

        assertEquals("Big Bob Jr", PlayerNameInput.read(input, NAMES));
        assertEquals("hello there", input.remainingInput().strip());
    }

    @Test
    @DisplayName("color tags and case are ignored when matching")
    void ignoresColorsAndCase() {
        CommandInput input = CommandInput.of("big BOB rest");

        assertEquals("big BOB", PlayerNameInput.read(input, NAMES));
        assertEquals("rest", input.remainingInput().strip());
    }

    @Test
    @DisplayName("an unknown name is one word, as before")
    void unknownNameIsOneWord() {
        CommandInput input = CommandInput.of("Nobody Here rest");

        assertEquals("Nobody", PlayerNameInput.read(input, NAMES));
        assertEquals("Here rest", input.remainingInput().strip());
    }

    @Test
    @DisplayName("a quoted name is read up to the closing quote, known or not")
    void quotedName() {
        CommandInput input = CommandInput.of("\"Some New Guy\" rest");

        assertEquals("Some New Guy", PlayerNameInput.read(input, NAMES));
        assertEquals("rest", input.remainingInput().strip());
    }

    @Test
    @DisplayName("an unclosed quote is read as one word")
    void unclosedQuote() {
        CommandInput input = CommandInput.of("\"Some rest");

        assertEquals("\"Some", PlayerNameInput.read(input, NAMES));
        assertEquals("rest", input.remainingInput().strip());
    }

    @Test
    @DisplayName("the name is returned as typed, repeated spaces included, and matches ignore spacing")
    void spacingIsKept() {
        CommandInput input = CommandInput.of("Big  Bob rest");

        assertEquals("Big  Bob", PlayerNameInput.read(input, List.of("Big  Bob")));
        assertEquals("rest", input.remainingInput().strip());
        assertEquals("Big  Bob", PlayerNameInput.read(CommandInput.of("\"Big  Bob\""), List.of()));
        assertTrue(PlayerNameInput.matches("[gold]Big  Bob [white]", "big bob"));
    }
}
