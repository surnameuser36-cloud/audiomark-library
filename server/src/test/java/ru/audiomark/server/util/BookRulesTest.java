package ru.audiomark.server.util;

import org.junit.jupiter.api.Test;
import ru.audiomark.server.model.Book;

import static org.junit.jupiter.api.Assertions.*;

/** Функциональные тесты поиска, статуса и временных меток. */
class BookRulesTest {
    private final Book book = new Book(1, "master", "Мастер и Маргарита",
            "Михаил Булгаков", "Роман", null, 3);

    @Test
    void searchMatchesTitleAndAuthorIgnoringCase() {
        assertTrue(BookRules.matches(book, "МАРГАРИТА"));
        assertTrue(BookRules.matches(book, "булгаков"));
        assertFalse(BookRules.matches(book, "толстой"));
    }

    @Test
    void completionRequiresNinetyFivePercent() {
        assertFalse(BookRules.isCompleted(94_999, 100_000));
        assertTrue(BookRules.isCompleted(95_000, 100_000));
    }

    @Test
    void positionFormattingSupportsHours() {
        assertEquals("1:01", BookRules.formatPosition(61_000));
        assertEquals("1:01:01", BookRules.formatPosition(3_661_000));
    }
}

