package ru.audiomark.client.net;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ru.audiomark.client.model.AudioPartItem;
import ru.audiomark.client.model.BookDetails;
import ru.audiomark.client.model.BookItem;
import ru.audiomark.client.model.CommentItem;

/**
 * Компонент сетевого доступа. Выполняет HTTP-запросы и преобразует JSON в
 * клиентские модели. Вызовы должны выполняться в фоновом потоке.
 */
public final class ApiClient {
    /** Адрес компьютера-хоста, доступный из стандартного Android Emulator. */
    public static final String BASE_URL = "http://10.0.2.2:8080";
    /** Кодировка обмена, доступная на Android 4.2. */
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private ApiClient() {
    }

    /** Загружает краткий каталог книг. */
    public static List<BookItem> loadBooks() throws Exception {
        JSONArray array = new JSONArray(get("/api/books"));
        List<BookItem> books = new ArrayList<>();
        for (int index = 0; index < array.length(); index++) {
            JSONObject item = array.getJSONObject(index);
            books.add(new BookItem(item.getLong("id"), item.getString("title"),
                    item.getString("authors"), item.optString("description"),
                    item.optInt("partCount"), item.optString("coverUrl")));
        }
        return books;
    }

    /** Загружает карточку и части выбранной книги. */
    public static BookDetails loadBook(long bookId) throws Exception {
        JSONObject object = new JSONObject(get("/api/books/" + bookId));
        JSONArray array = object.getJSONArray("parts");
        List<AudioPartItem> parts = new ArrayList<>();
        for (int index = 0; index < array.length(); index++) {
            JSONObject item = array.getJSONObject(index);
            parts.add(new AudioPartItem(item.getLong("id"), item.getInt("number"),
                    item.getString("name"), item.getString("audioUrl")));
        }
        return new BookDetails(object.getString("title"), object.getString("authors"),
                object.optString("description"), object.optInt("likeCount"), parts);
    }

    /** Загружает общие комментарии выбранной книги. */
    public static List<CommentItem> loadComments(long bookId) throws Exception {
        JSONArray array = new JSONArray(get("/api/books/" + bookId + "/comments"));
        List<CommentItem> comments = new ArrayList<>();
        for (int index = 0; index < array.length(); index++) {
            JSONObject item = array.getJSONObject(index);
            comments.add(new CommentItem(item.getLong("partId"), item.getLong("positionMs"),
                    item.getString("author"), item.getString("text")));
        }
        JSONArray likes = new JSONArray(get("/api/books/" + bookId + "/likes"));
        for (int index = 0; index < likes.length(); index++) {
            JSONObject item = likes.getJSONObject(index);
            int count = item.optInt("count", 1);
            comments.add(new CommentItem(item.getLong("partId"), item.getLong("positionMs"),
                    "♥", count + " отметок"));
        }
        Collections.sort(comments, (first, second) -> {
            int partOrder = first.partId < second.partId ? -1 : (first.partId == second.partId ? 0 : 1);
            return partOrder != 0 ? partOrder
                    : (first.positionMs < second.positionMs ? -1
                    : (first.positionMs == second.positionMs ? 0 : 1));
        });
        return comments;
    }

    /** Отправляет комментарий, привязанный к текущей позиции. */
    public static void addComment(long bookId, long partId, long positionMs,
                                  String author, String text) throws Exception {
        JSONObject body = new JSONObject();
        body.put("partId", partId);
        body.put("positionMs", positionMs);
        body.put("author", author);
        body.put("text", text);
        post("/api/books/" + bookId + "/comments", body.toString());
    }

    /** Отправляет отметку «Нравится» для временной точки. */
    public static int addLike(long bookId, long partId, long positionMs,
                              String deviceId) throws Exception {
        JSONObject body = new JSONObject();
        body.put("partId", partId);
        body.put("positionMs", positionMs);
        body.put("deviceId", deviceId);
        return new JSONObject(post("/api/books/" + bookId + "/likes", body.toString()))
                .optInt("count");
    }

    /** Выполняет GET-запрос и возвращает тело ответа. */
    private static String get(String path) throws IOException {
        HttpURLConnection connection = open(path);
        connection.setRequestMethod("GET");
        return readResponse(connection);
    }

    /** Выполняет POST-запрос с JSON-телом. */
    private static String post(String path, String json) throws IOException {
        HttpURLConnection connection = open(path);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setDoOutput(true);
        byte[] bytes = json.getBytes(UTF_8);
        connection.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(bytes);
        }
        return readResponse(connection);
    }

    /** Создает соединение с общими тайм-аутами. */
    private static HttpURLConnection open(String path) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(BASE_URL + path).openConnection();
        connection.setConnectTimeout(7000);
        connection.setReadTimeout(15000);
        return connection;
    }

    /** Проверяет HTTP-код и читает ответ в UTF-8. */
    private static String readResponse(HttpURLConnection connection) throws IOException {
        int code = connection.getResponseCode();
        InputStream stream = code >= 200 && code < 300
                ? connection.getInputStream() : connection.getErrorStream();
        StringBuilder text = new StringBuilder();
        if (stream != null) {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stream, UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    text.append(line);
                }
            }
        }
        connection.disconnect();
        if (code < 200 || code >= 300) {
            throw new IOException("HTTP " + code + ": " + text);
        }
        return text.toString();
    }
}

