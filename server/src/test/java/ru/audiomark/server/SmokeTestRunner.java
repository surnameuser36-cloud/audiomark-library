package ru.audiomark.server;

import ru.audiomark.server.model.Book;
import ru.audiomark.server.util.BookRules;
import ru.audiomark.server.util.FileSupport;
import ru.audiomark.server.util.Json;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Автономный запуск основных тестов без Maven. Используется для быстрой
 * проверки чистой бизнес-логики в окружении, где JUnit еще не загружен.
 */
public final class SmokeTestRunner {
    /** Количество успешно выполненных проверок. */
    private static int passed;

    private SmokeTestRunner() {
    }

    /** Последовательно выполняет восемь независимых функциональных проверок. */
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("audiomark-test-");
        Path first = Files.writeString(directory.resolve("first.mp3"), "same-data");
        Path second = Files.writeString(directory.resolve("second.mp3"), "same-data");
        Path third = Files.writeString(directory.resolve("third.mp3"), "other-data");

        check(FileSupport.md5(first).equals(FileSupport.md5(second)), "одинаковый MD5");
        check(!FileSupport.md5(first).equals(FileSupport.md5(third)), "различный MD5");
        check(FileSupport.isSupportedAudio(Path.of("PART.MP3")), "расширение MP3");
        check(!FileSupport.isSupportedAudio(Path.of("cover.png")), "отклонение PNG");
        check(Json.stringField("{\"text\":\"один\\nдва\"}", "text", "")
                .equals("один\nдва"), "чтение JSON");
        check(Json.longField("{\"positionMs\":1250}", "positionMs", -1) == 1250,
                "числовое поле JSON");
        Book book = new Book(1, "key", "Мастер и Маргарита", "Михаил Булгаков",
                "Роман", null, 3);
        check(BookRules.matches(book, "БУЛГАКОВ"), "поиск без учета регистра");
        check(BookRules.isCompleted(95_000, 100_000), "граница завершения");

        System.out.println("Успешно выполнено проверок: " + passed);
    }

    /** Завершает процесс с ошибкой, если условие очередной проверки ложно. */
    private static void check(boolean condition, String name) {
        if (!condition) {
            throw new AssertionError("Проверка не пройдена: " + name);
        }
        passed++;
    }
}

