package ru.audiomark.server.util;

import ru.audiomark.server.model.Book;

import java.util.Locale;

/** Общие правила поиска и определения состояния книги. */
public final class BookRules {
    private BookRules() {
    }

    /** Проверяет соответствие книги поисковой строке без учета регистра. */
    public static boolean matches(Book book, String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty()
                || book.title().toLowerCase(Locale.ROOT).contains(normalized)
                || book.authors().toLowerCase(Locale.ROOT).contains(normalized);
    }

    /** Считает часть прослушанной при достижении 95 процентов длительности. */
    public static boolean isCompleted(long positionMs, long durationMs) {
        return durationMs > 0 && positionMs >= Math.round(durationMs * 0.95);
    }

    /** Форматирует миллисекунды как временную метку ЧЧ:ММ:СС или ММ:СС. */
    public static String formatPosition(long positionMs) {
        long totalSeconds = Math.max(0, positionMs) / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return hours > 0
                ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
                : String.format(Locale.ROOT, "%d:%02d", minutes, seconds);
    }
}

