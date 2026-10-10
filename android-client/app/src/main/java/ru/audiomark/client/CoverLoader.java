package ru.audiomark.client;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ru.audiomark.client.net.ApiClient;

/**
 * Компонент асинхронной загрузки обложек. Сетевые операции выполняются вне
 * главного потока, а полученные изображения сохраняются в памяти приложения.
 */
public final class CoverLoader {
    /** Пул фоновых потоков ограничивает число одновременных HTTP-запросов. */
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(3);
    /** Обработчик публикует результат в интерфейсе. */
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    /** Кэш уменьшает повторную загрузку при прокрутке и фильтрации списка. */
    private static final LruCache<String, Bitmap> MEMORY_CACHE =
            new LruCache<String, Bitmap>(8 * 1024) {
                /** Возвращает приблизительный размер изображения в килобайтах. */
                @Override
                protected int sizeOf(String key, Bitmap bitmap) {
                    return Math.max(1, bitmap.getByteCount() / 1024);
                }
            };

    /**
     * Показывает заглушку и асинхронно заменяет ее обложкой. Проверка тега
     * защищает переиспользуемую строку ListView от установки чужого изображения.
     */
    public void load(ImageView imageView, String relativeUrl) {
        String absoluteUrl = toAbsoluteUrl(relativeUrl);
        imageView.setTag(R.id.bookCover, absoluteUrl);
        imageView.setImageResource(R.drawable.cover_placeholder);
        if (absoluteUrl.isEmpty()) {
            return;
        }

        Bitmap cached = MEMORY_CACHE.get(absoluteUrl);
        if (cached != null) {
            imageView.setImageBitmap(cached);
            return;
        }

        EXECUTOR.execute(() -> {
            Bitmap bitmap = download(absoluteUrl);
            if (bitmap != null) {
                MEMORY_CACHE.put(absoluteUrl, bitmap);
            }
            MAIN_HANDLER.post(() -> {
                Object expectedUrl = imageView.getTag(R.id.bookCover);
                if (absoluteUrl.equals(expectedUrl) && bitmap != null) {
                    imageView.setImageBitmap(bitmap);
                }
            });
        });
    }

    /** Преобразует серверный относительный путь в полный HTTP-адрес. */
    private static String toAbsoluteUrl(String relativeUrl) {
        if (relativeUrl == null || relativeUrl.trim().isEmpty()) {
            return "";
        }
        if (relativeUrl.startsWith("http://") || relativeUrl.startsWith("https://")) {
            return relativeUrl;
        }
        return ApiClient.BASE_URL + (relativeUrl.startsWith("/") ? "" : "/") + relativeUrl;
    }

    /** Загружает и декодирует изображение либо возвращает null при ошибке. */
    private static Bitmap download(String absoluteUrl) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(absoluteUrl).openConnection();
            connection.setConnectTimeout(7_000);
            connection.setReadTimeout(15_000);
            connection.setRequestProperty("Accept", "image/*");
            int responseCode = connection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                return null;
            }
            try (InputStream input = connection.getInputStream()) {
                return BitmapFactory.decodeStream(input);
            }
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
