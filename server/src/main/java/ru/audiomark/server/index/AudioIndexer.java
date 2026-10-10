package ru.audiomark.server.index;

import ru.audiomark.server.db.Database;
import ru.audiomark.server.util.FileSupport;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

/**
 * Компонент индексирования. Каждая непосредственная дочерняя папка корневого
 * каталога рассматривается как одна книга, а аудиофайлы — как ее части.
 */
public final class AudioIndexer {
    /** Корневой каталог, подключенный к Docker-контейнеру только для чтения. */
    private final Path audioRoot;
    /** Хранилище метаданных и контрольных сумм. */
    private final Database database;

    public AudioIndexer(Path audioRoot, Database database) {
        this.audioRoot = audioRoot;
        this.database = database;
    }

    /** Обходит папки первого уровня и запускает индексирование каждой книги. */
    public void indexAll() throws IOException, SQLException {
        Files.createDirectories(audioRoot);
        try (Stream<Path> children = Files.list(audioRoot)) {
            List<Path> directories = children.filter(Files::isDirectory)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
            for (Path directory : directories) {
                indexBook(directory);
            }
        }
    }

    /** Считывает book.properties, находит обложку и регистрирует части книги. */
    private void indexBook(Path directory) throws IOException, SQLException {
        List<Path> files;
        try (Stream<Path> stream = Files.list(directory)) {
            files = stream.filter(Files::isRegularFile)
                    .filter(FileSupport::isSupportedAudio)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString(),
                            String.CASE_INSENSITIVE_ORDER))
                    .toList();
        }
        if (files.isEmpty()) {
            return;
        }

        Properties metadata = loadMetadata(directory.resolve("book.properties"));
        String defaultTitle = directory.getFileName().toString().replace('_', ' ');
        String title = metadata.getProperty("title", defaultTitle).trim();
        String authors = metadata.getProperty("authors", "Автор не указан").trim();
        String description = metadata.getProperty("description", "Описание отсутствует").trim();
        Path cover = findCover(directory);
        long bookId = database.upsertBook(FileSupport.directoryKey(directory), title, authors,
                description, cover == null ? null : cover.toAbsolutePath().toString());

        for (int index = 0; index < files.size(); index++) {
            Path file = files.get(index);
            database.upsertPart(bookId, index + 1, file.getFileName().toString(),
                    file.toAbsolutePath().toString(), FileSupport.md5(file), Files.size(file));
        }
    }

    /** Загружает необязательный файл метаданных в кодировке UTF-8. */
    private static Properties loadMetadata(Path file) throws IOException {
        Properties properties = new Properties();
        if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
        }
        return properties;
    }

    /** Ищет обложку под одним из поддерживаемых имен. */
    private static Path findCover(Path directory) {
        for (String name : List.of("cover.png", "cover.jpg", "cover.jpeg")) {
            Path candidate = directory.resolve(name);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }
}

