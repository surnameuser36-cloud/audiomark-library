package ru.audiomark.server.db;

import ru.audiomark.server.model.AudioPart;
import ru.audiomark.server.model.Book;
import ru.audiomark.server.model.Comment;
import ru.audiomark.server.model.LikeMarker;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Компонент доступа к SQLite. Методы класса инкапсулируют схему и все SQL-запросы,
 * поэтому HTTP-обработчики не зависят от способа хранения данных.
 */
public final class Database implements AutoCloseable {
    /** Единственное соединение с локальной базой данных контейнера. */
    private final Connection connection;

    /** Открывает файл базы и создает таблицы при первом запуске. */
    public Database(Path dbPath) throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
        initialize();
    }

    /** Создает таблицы и индексы, необходимые серверу. */
    private void initialize() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS books (
                      id INTEGER PRIMARY KEY AUTOINCREMENT,
                      directory_key TEXT NOT NULL UNIQUE,
                      title TEXT NOT NULL,
                      authors TEXT NOT NULL,
                      description TEXT NOT NULL,
                      cover_path TEXT
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS parts (
                      id INTEGER PRIMARY KEY AUTOINCREMENT,
                      book_id INTEGER NOT NULL REFERENCES books(id) ON DELETE CASCADE,
                      part_number INTEGER NOT NULL,
                      file_name TEXT NOT NULL,
                      file_path TEXT NOT NULL,
                      md5 TEXT NOT NULL UNIQUE,
                      size_bytes INTEGER NOT NULL,
                      UNIQUE(book_id, part_number)
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS comments (
                      id INTEGER PRIMARY KEY AUTOINCREMENT,
                      book_id INTEGER NOT NULL REFERENCES books(id) ON DELETE CASCADE,
                      part_id INTEGER NOT NULL REFERENCES parts(id) ON DELETE CASCADE,
                      position_ms INTEGER NOT NULL,
                      author TEXT NOT NULL,
                      text TEXT NOT NULL,
                      created_at TEXT NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS likes (
                      id INTEGER PRIMARY KEY AUTOINCREMENT,
                      book_id INTEGER NOT NULL REFERENCES books(id) ON DELETE CASCADE,
                      part_id INTEGER NOT NULL REFERENCES parts(id) ON DELETE CASCADE,
                      position_ms INTEGER NOT NULL,
                      device_id TEXT NOT NULL,
                      created_at TEXT NOT NULL,
                      UNIQUE(part_id, position_ms, device_id)
                    )
                    """);
        }
    }

    /** Создает или обновляет карточку книги и возвращает ее идентификатор. */
    public synchronized long upsertBook(String key, String title, String authors,
                                        String description, String coverPath) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO books(directory_key,title,authors,description,cover_path)
                VALUES(?,?,?,?,?)
                ON CONFLICT(directory_key) DO UPDATE SET
                  title=excluded.title, authors=excluded.authors,
                  description=excluded.description, cover_path=excluded.cover_path
                """)) {
            statement.setString(1, key);
            statement.setString(2, title);
            statement.setString(3, authors);
            statement.setString(4, description);
            statement.setString(5, coverPath);
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM books WHERE directory_key=?")) {
            statement.setString(1, key);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getLong(1) : -1;
            }
        }
    }

    /** Сохраняет индексированную часть; MD5 не позволяет создать дубликат файла. */
    public synchronized void upsertPart(long bookId, int number, String name,
                                        String path, String md5, long size) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO parts(book_id,part_number,file_name,file_path,md5,size_bytes)
                VALUES(?,?,?,?,?,?)
                ON CONFLICT(md5) DO UPDATE SET
                  book_id=excluded.book_id, part_number=excluded.part_number,
                  file_name=excluded.file_name, file_path=excluded.file_path,
                  size_bytes=excluded.size_bytes
                """)) {
            statement.setLong(1, bookId);
            statement.setInt(2, number);
            statement.setString(3, name);
            statement.setString(4, path);
            statement.setString(5, md5);
            statement.setLong(6, size);
            statement.executeUpdate();
        }
    }

    /** Возвращает каталог с вычисленным числом частей. */
    public synchronized List<Book> listBooks() throws SQLException {
        List<Book> books = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("""
                     SELECT b.*, COUNT(p.id) AS part_count
                     FROM books b LEFT JOIN parts p ON p.book_id=b.id
                     GROUP BY b.id ORDER BY b.title COLLATE NOCASE
                     """)) {
            while (result.next()) {
                books.add(toBook(result));
            }
        }
        return books;
    }

    /** Возвращает одну книгу либо null, если идентификатор не существует. */
    public synchronized Book findBook(long id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT b.*, COUNT(p.id) AS part_count
                FROM books b LEFT JOIN parts p ON p.book_id=b.id
                WHERE b.id=? GROUP BY b.id
                """)) {
            statement.setLong(1, id);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? toBook(result) : null;
            }
        }
    }

    /** Возвращает части книги в порядке воспроизведения. */
    public synchronized List<AudioPart> listParts(long bookId) throws SQLException {
        List<AudioPart> parts = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM parts WHERE book_id=? ORDER BY part_number")) {
            statement.setLong(1, bookId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    parts.add(new AudioPart(result.getLong("id"), result.getLong("book_id"),
                            result.getInt("part_number"), result.getString("file_name"),
                            result.getString("file_path"), result.getString("md5"),
                            result.getLong("size_bytes")));
                }
            }
        }
        return parts;
    }

    /** Возвращает часть только в пределах указанной книги. */
    public synchronized AudioPart findPart(long bookId, long partId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM parts WHERE id=? AND book_id=?")) {
            statement.setLong(1, partId);
            statement.setLong(2, bookId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? new AudioPart(result.getLong("id"), result.getLong("book_id"),
                        result.getInt("part_number"), result.getString("file_name"),
                        result.getString("file_path"), result.getString("md5"),
                        result.getLong("size_bytes")) : null;
            }
        }
    }

    /** Добавляет общий комментарий и возвращает его идентификатор. */
    public synchronized long addComment(long bookId, long partId, long positionMs,
                                        String author, String text) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO comments(book_id,part_id,position_ms,author,text,created_at)
                VALUES(?,?,?,?,?,?)
                """, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, bookId);
            statement.setLong(2, partId);
            statement.setLong(3, Math.max(0, positionMs));
            statement.setString(4, author);
            statement.setString(5, text);
            statement.setString(6, Instant.now().toString());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : -1;
            }
        }
    }

    /** Возвращает комментарии книги в порядке временных меток. */
    public synchronized List<Comment> listComments(long bookId) throws SQLException {
        List<Comment> comments = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM comments WHERE book_id=? ORDER BY part_id, position_ms, id")) {
            statement.setLong(1, bookId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    comments.add(new Comment(result.getLong("id"), result.getLong("book_id"),
                            result.getLong("part_id"), result.getLong("position_ms"),
                            result.getString("author"), result.getString("text"),
                            result.getString("created_at")));
                }
            }
        }
        return comments;
    }

    /** Добавляет уникальную отметку и возвращает true; повтор возвращает false. */
    public synchronized boolean addLike(long bookId, long partId, long positionMs,
                                        String deviceId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT OR IGNORE INTO likes(book_id,part_id,position_ms,device_id,created_at)
                VALUES(?,?,?,?,?)
                """)) {
            statement.setLong(1, bookId);
            statement.setLong(2, partId);
            statement.setLong(3, Math.max(0, positionMs));
            statement.setString(4, deviceId);
            statement.setString(5, Instant.now().toString());
            return statement.executeUpdate() == 1;
        }
    }

    /** Возвращает число отметок для книги. */
    public synchronized int countLikes(long bookId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM likes WHERE book_id=?")) {
            statement.setLong(1, bookId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt(1) : 0;
            }
        }
    }

    /** Возвращает сгруппированные отметки для отображения на временной шкале. */
    public synchronized List<LikeMarker> listLikeMarkers(long bookId) throws SQLException {
        List<LikeMarker> markers = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT part_id, position_ms, COUNT(*) AS marker_count
                FROM likes WHERE book_id=?
                GROUP BY part_id, position_ms ORDER BY part_id, position_ms
                """)) {
            statement.setLong(1, bookId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    markers.add(new LikeMarker(result.getLong("part_id"),
                            result.getLong("position_ms"), result.getInt("marker_count")));
                }
            }
        }
        return markers;
    }

    /** Преобразует текущую строку результата SQL в модель книги. */
    private static Book toBook(ResultSet result) throws SQLException {
        return new Book(result.getLong("id"), result.getString("directory_key"),
                result.getString("title"), result.getString("authors"),
                result.getString("description"), result.getString("cover_path"),
                result.getInt("part_count"));
    }

    /** Освобождает соединение при остановке сервера. */
    @Override
    public synchronized void close() throws SQLException {
        connection.close();
    }
}

