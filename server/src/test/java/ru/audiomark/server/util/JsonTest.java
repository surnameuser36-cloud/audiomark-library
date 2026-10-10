package ru.audiomark.server.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Функциональные тесты JSON-обмена с мобильным клиентом. */
class JsonTest {
    @Test
    void quoteEscapesControlCharacters() {
        assertEquals("\"строка\\n\\\"тест\\\"\"", Json.quote("строка\n\"тест\""));
    }

    @Test
    void stringFieldReadsEscapedText() {
        assertEquals("один\nдва", Json.stringField("{\"text\":\"один\\nдва\"}", "text", ""));
    }

    @Test
    void longFieldReadsValueAndUsesDefault() {
        assertEquals(1250, Json.longField("{\"positionMs\":1250}", "positionMs", -1));
        assertEquals(-1, Json.longField("{}", "positionMs", -1));
    }
}

