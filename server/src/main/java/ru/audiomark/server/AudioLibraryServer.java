package ru.audiomark.server;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import ru.audiomark.server.db.Database;
import ru.audiomark.server.index.AudioIndexer;
import ru.audiomark.server.model.AudioPart;
import ru.audiomark.server.model.Book;
import ru.audiomark.server.model.Comment;
import ru.audiomark.server.model.LikeMarker;
import ru.audiomark.server.util.Json;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * Главный компонент серверной части. Поднимает HTTP API, запускает индексатор
 * и связывает запросы клиента с базой данных и аудиофайлами.
 */
public final class AudioLibraryServer {
    /** База метаданных, комментариев и отметок. */
    private final Database database;
    /** Встроенный HTTP-сервер без отдельного контейнера приложений. */
    private final HttpServer httpServer;

    private AudioLibraryServer(Database database, int port) throws IOException {
        this.database = database;
        this.httpServer = HttpServer.create(new InetSocketAddress(port), 0);
        this.httpServer.createContext("/api", this::handleApi);
        this.httpServer.setExecutor(Executors.newFixedThreadPool(8));
    }

    /** Читает настройки окружения, индексирует каталог и запускает сервер. */
    public static void main(String[] args) throws Exception {
        Path audioDir = Path.of(environment("AUDIOMARK_AUDIO_DIR", "data/audio"));
        Path dbPath = Path.of(environment("AUDIOMARK_DB_PATH", "data/db/audiomark.db"));
        int port = Integer.parseInt(environment("AUDIOMARK_PORT", "8080"));
        Files.createDirectories(dbPath.toAbsolutePath().getParent());

        Database database = new Database(dbPath);
        new AudioIndexer(audioDir, database).indexAll();
        AudioLibraryServer application = new AudioLibraryServer(database, port);
        Runtime.getRuntime().addShutdownHook(new Thread(application::stop));
        application.httpServer.start();
        System.out.println("AudioMark server is listening on port " + port);
    }

    /** Возвращает переменную окружения или значение по умолчанию. */
    private static String environment(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    /** Маршрутизирует запросы относительно контекста /api. */
    private void handleApi(HttpExchange exchange) throws IOException {
        addCommonHeaders(exchange.getResponseHeaders());
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return;
        }
        try {
            String path = exchange.getRequestURI().getPath();
            String[] segments = path.split("/");
            if ("/api/health".equals(path) && "GET".equals(exchange.getRequestMethod())) {
                sendJson(exchange, 200, "{\"status\":\"ok\"}");
            } else if ("/api/books".equals(path) && "GET".equals(exchange.getRequestMethod())) {
                sendJson(exchange, 200, booksJson(database.listBooks()));
            } else if (segments.length >= 4 && "books".equals(segments[2])) {
                handleBook(exchange, segments, Long.parseLong(segments[3]));
            } else {
                sendError(exchange, 404, "Маршрут не найден");
            }
        } catch (NumberFormatException exception) {
            sendError(exchange, 400, "Некорректный идентификатор");
        } catch (IllegalArgumentException exception) {
            sendError(exchange, 400, exception.getMessage());
        } catch (SQLException exception) {
            exception.printStackTrace();
            sendError(exchange, 500, "Ошибка базы данных");
        } catch (Exception exception) {
            exception.printStackTrace();
            sendError(exchange, 500, "Внутренняя ошибка сервера");
        }
    }

    /** Обрабатывает маршруты конкретной книги. */
    private void handleBook(HttpExchange exchange, String[] segments, long bookId) throws Exception {
        Book book = database.findBook(bookId);
        if (book == null) {
            sendError(exchange, 404, "Книга не найдена");
            return;
        }
        String method = exchange.getRequestMethod();
        if (segments.length == 4 && "GET".equals(method)) {
            sendJson(exchange, 200, bookDetailsJson(book, database.listParts(bookId),
                    database.countLikes(bookId)));
        } else if (segments.length == 5 && "cover".equals(segments[4]) && "GET".equals(method)) {
            sendCover(exchange, book);
        } else if (segments.length == 5 && "comments".equals(segments[4])) {
            handleComments(exchange, bookId);
        } else if (segments.length == 5 && "likes".equals(segments[4])) {
            handleLikes(exchange, bookId);
        } else if (segments.length == 7 && "parts".equals(segments[4])
                && "audio".equals(segments[6]) && "GET".equals(method)) {
            sendAudio(exchange, bookId, Long.parseLong(segments[5]));
        } else {
            sendError(exchange, 404, "Маршрут книги не найден");
        }
    }

    /** Возвращает список комментариев или добавляет новый комментарий. */
    private void handleComments(HttpExchange exchange, long bookId) throws Exception {
        if ("GET".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 200, commentsJson(database.listComments(bookId)));
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Метод не поддерживается");
            return;
        }
        String body = readBody(exchange);
        long partId = Json.longField(body, "partId", -1);
        long positionMs = Json.longField(body, "positionMs", 0);
        String author = Json.stringField(body, "author", "Слушатель").trim();
        String text = Json.stringField(body, "text", "").trim();
        validatePartAndText(bookId, partId, text);
        long id = database.addComment(bookId, partId, positionMs,
                author.isEmpty() ? "Слушатель" : author, text);
        sendJson(exchange, 201, "{\"id\":" + id + "}");
    }

    /** Проверяет ссылку на часть и непустой текст комментария. */
    private void validatePartAndText(long bookId, long partId, String text) throws SQLException {
        if (database.findPart(bookId, partId) == null) {
            throw new IllegalArgumentException("Часть книги не найдена");
        }
        if (text.isEmpty() || text.length() > 500) {
            throw new IllegalArgumentException("Текст должен содержать от 1 до 500 символов");
        }
    }

    /** Добавляет отметку на временной шкале с защитой от повторов устройства. */
    private void handleLikes(HttpExchange exchange, long bookId) throws Exception {
        if ("GET".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 200, likeMarkersJson(database.listLikeMarkers(bookId)));
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Метод не поддерживается");
            return;
        }
        String body = readBody(exchange);
        long partId = Json.longField(body, "partId", -1);
        long positionMs = Json.longField(body, "positionMs", 0);
        String deviceId = Json.stringField(body, "deviceId", "").trim();
        if (database.findPart(bookId, partId) == null) {
            throw new IllegalArgumentException("Часть книги не найдена");
        }
        if (deviceId.isEmpty() || deviceId.length() > 100) {
            throw new IllegalArgumentException("Некорректный идентификатор устройства");
        }
        boolean created = database.addLike(bookId, partId, positionMs, deviceId);
        sendJson(exchange, created ? 201 : 200,
                "{\"created\":" + created + ",\"count\":" + database.countLikes(bookId) + "}");
    }

    /** Отдает обложку или 404, если обложка не задана. */
    private void sendCover(HttpExchange exchange, Book book) throws IOException {
        if (book.coverPath() == null || !Files.isRegularFile(Path.of(book.coverPath()))) {
            sendError(exchange, 404, "Обложка отсутствует");
            return;
        }
        Path file = Path.of(book.coverPath());
        String type = Files.probeContentType(file);
        exchange.getResponseHeaders().set("Content-Type", type == null ? "image/jpeg" : type);
        exchange.sendResponseHeaders(200, Files.size(file));
        try (OutputStream output = exchange.getResponseBody()) {
            Files.copy(file, output);
        }
    }

    /** Отдает аудиофайл и поддерживает байтовый Range для перемотки. */
    private void sendAudio(HttpExchange exchange, long bookId, long partId) throws Exception {
        AudioPart part = database.findPart(bookId, partId);
        if (part == null || !Files.isRegularFile(Path.of(part.filePath()))) {
            sendError(exchange, 404, "Аудиофайл не найден");
            return;
        }
        Path file = Path.of(part.filePath());
        long size = Files.size(file);
        long start = 0;
        long end = size - 1;
        String range = exchange.getRequestHeaders().getFirst("Range");
        boolean partial = range != null && range.startsWith("bytes=");
        if (partial) {
            String[] values = range.substring(6).split("-", 2);
            start = parseRangeValue(values[0], 0);
            end = values.length > 1 && !values[1].isBlank() ? parseRangeValue(values[1], end) : end;
            if (start < 0 || end < start || start >= size) {
                exchange.getResponseHeaders().set("Content-Range", "bytes */" + size);
                exchange.sendResponseHeaders(416, -1);
                exchange.close();
                return;
            }
            end = Math.min(end, size - 1);
        }
        long length = end - start + 1;
        Headers headers = exchange.getResponseHeaders();
        headers.set("Accept-Ranges", "bytes");
        headers.set("Content-Type", contentType(file));
        headers.set("Content-Length", Long.toString(length));
        if (partial) {
            headers.set("Content-Range", "bytes " + start + "-" + end + "/" + size);
        }
        exchange.sendResponseHeaders(partial ? 206 : 200, length);
        try (InputStream input = Files.newInputStream(file); OutputStream output = exchange.getResponseBody()) {
            input.skipNBytes(start);
            byte[] buffer = new byte[16_384];
            long remaining = length;
            while (remaining > 0) {
                int count = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (count < 0) break;
                output.write(buffer, 0, count);
                remaining -= count;
            }
        }
    }

    /** Преобразует границу Range, подставляя значение по умолчанию при ошибке. */
    private static long parseRangeValue(String value, long defaultValue) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }

    /** Определяет MIME-тип по расширению аудиофайла. */
    private static String contentType(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        if (name.endsWith(".mp3")) return "audio/mpeg";
        if (name.endsWith(".m4a")) return "audio/mp4";
        if (name.endsWith(".ogg")) return "audio/ogg";
        if (name.endsWith(".wav")) return "audio/wav";
        return "application/octet-stream";
    }

    /** Сериализует каталог книг. */
    private static String booksJson(List<Book> books) {
        StringBuilder json = new StringBuilder("[");
        for (int index = 0; index < books.size(); index++) {
            if (index > 0) json.append(',');
            Book book = books.get(index);
            json.append('{')
                    .append("\"id\":").append(book.id()).append(',')
                    .append("\"title\":").append(Json.quote(book.title())).append(',')
                    .append("\"authors\":").append(Json.quote(book.authors())).append(',')
                    .append("\"description\":").append(Json.quote(book.description())).append(',')
                    .append("\"partCount\":").append(book.partCount()).append(',')
                    .append("\"coverUrl\":").append(Json.quote("/api/books/" + book.id() + "/cover"))
                    .append('}');
        }
        return json.append(']').toString();
    }

    /** Сериализует карточку книги и список частей. */
    private static String bookDetailsJson(Book book, List<AudioPart> parts, int likeCount) {
        StringBuilder json = new StringBuilder("{")
                .append("\"id\":").append(book.id()).append(',')
                .append("\"title\":").append(Json.quote(book.title())).append(',')
                .append("\"authors\":").append(Json.quote(book.authors())).append(',')
                .append("\"description\":").append(Json.quote(book.description())).append(',')
                .append("\"likeCount\":").append(likeCount).append(',')
                .append("\"parts\":[");
        for (int index = 0; index < parts.size(); index++) {
            if (index > 0) json.append(',');
            AudioPart part = parts.get(index);
            json.append('{').append("\"id\":").append(part.id()).append(',')
                    .append("\"number\":").append(part.partNumber()).append(',')
                    .append("\"name\":").append(Json.quote(part.fileName())).append(',')
                    .append("\"md5\":").append(Json.quote(part.md5())).append(',')
                    .append("\"sizeBytes\":").append(part.sizeBytes()).append(',')
                    .append("\"audioUrl\":").append(Json.quote("/api/books/" + book.id()
                            + "/parts/" + part.id() + "/audio"))
                    .append('}');
        }
        return json.append("]}").toString();
    }

    /** Сериализует общие комментарии и временные метки. */
    private static String commentsJson(List<Comment> comments) {
        StringBuilder json = new StringBuilder("[");
        for (int index = 0; index < comments.size(); index++) {
            if (index > 0) json.append(',');
            Comment comment = comments.get(index);
            json.append('{').append("\"id\":").append(comment.id()).append(',')
                    .append("\"partId\":").append(comment.partId()).append(',')
                    .append("\"positionMs\":").append(comment.positionMs()).append(',')
                    .append("\"author\":").append(Json.quote(comment.author())).append(',')
                    .append("\"text\":").append(Json.quote(comment.text())).append(',')
                    .append("\"createdAt\":").append(Json.quote(comment.createdAt())).append('}');
        }
        return json.append(']').toString();
    }

    /** Сериализует сгруппированные отметки на временной шкале. */
    private static String likeMarkersJson(List<LikeMarker> markers) {
        StringBuilder json = new StringBuilder("[");
        for (int index = 0; index < markers.size(); index++) {
            if (index > 0) json.append(',');
            LikeMarker marker = markers.get(index);
            json.append('{').append("\"partId\":").append(marker.partId()).append(',')
                    .append("\"positionMs\":").append(marker.positionMs()).append(',')
                    .append("\"count\":").append(marker.count()).append('}');
        }
        return json.append(']').toString();
    }

    /** Читает тело запроса в UTF-8 с ограничением 64 Кбайт. */
    private static String readBody(HttpExchange exchange) throws IOException {
        byte[] bytes = exchange.getRequestBody().readNBytes(65_537);
        if (bytes.length > 65_536) {
            throw new IllegalArgumentException("Слишком большой запрос");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /** Отправляет JSON-ответ и закрывает обмен. */
    private static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    /** Формирует единообразный ответ об ошибке. */
    private static void sendError(HttpExchange exchange, int status, String message) throws IOException {
        sendJson(exchange, status, "{\"error\":" + Json.quote(message) + "}");
    }

    /** Добавляет CORS-заголовки для веб-представления документации и тестов API. */
    private static void addCommonHeaders(Headers headers) {
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Headers", "Content-Type, Range");
        headers.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
    }

    /** Останавливает обработчики и закрывает базу. */
    private void stop() {
        httpServer.stop(1);
        try {
            database.close();
        } catch (SQLException exception) {
            exception.printStackTrace();
        }
    }

}

