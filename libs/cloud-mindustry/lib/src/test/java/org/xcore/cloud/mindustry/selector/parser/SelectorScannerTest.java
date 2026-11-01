package org.xcore.cloud.mindustry.selector.parser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.cloud.mindustry.selector.exception.SelectorSyntaxException;

import static org.junit.jupiter.api.Assertions.*;

class SelectorScannerTest {

    @Test
    @DisplayName("Parse integers correctly including signs and bounds")
    void parseInt_parsesCorrectly() {
        SelectorScanner scanner = new SelectorScanner("42 -100 +7 0");
        assertEquals(42, scanner.parseInt());
        assertEquals(-100, scanner.parseInt());
        assertEquals(7, scanner.parseInt());
        assertEquals(0, scanner.parseInt());
    }

    @Test
    @DisplayName("Parse floats correctly without allocation")
    void parseFloat_parsesCorrectly() {
        SelectorScanner scanner = new SelectorScanner("3.14 -0.5 +123.456 10");
        assertEquals(3.14f, scanner.parseFloat(), 0.001f);
        assertEquals(-0.5f, scanner.parseFloat(), 0.001f);
        assertEquals(123.456f, scanner.parseFloat(), 0.001f);
        assertEquals(10f, scanner.parseFloat(), 0.001f);
    }

    @Test
    @DisplayName("Parse identifiers and values with quotes")
    void parseIdentifierAndQuotedValues() {
        SelectorScanner scanner = new SelectorScanner("team=\"Crux Team\" type=flare");
        assertEquals("team", scanner.parseIdentifier());
        scanner.require('=', "expected =");
        assertEquals("Crux Team", scanner.parseValue());

        assertEquals("type", scanner.parseIdentifier());
        scanner.require('=', "expected =");
        assertEquals("flare", scanner.parseValue());
    }

    @Test
    @DisplayName("Unclosed quoted string throws SelectorSyntaxException with cursor position")
    void unclosedQuotes_throwsSyntaxException() {
        SelectorScanner scanner = new SelectorScanner("name=\"unterminated");
        scanner.parseIdentifier();
        scanner.require('=', "expected =");
        assertThrows(SelectorSyntaxException.class, scanner::parseValue);
    }
}
