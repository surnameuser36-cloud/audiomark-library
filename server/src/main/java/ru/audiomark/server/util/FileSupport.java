package ru.audiomark.server.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;

/** Компонент операций над аудиофайлами и контрольными суммами. */
public final class FileSupport {
    /** Набор расширений, которые индексатор считает аудиофайлами. */
    private static final Set<String> AUDIO_EXTENSIONS = Set.of("mp3", "m4a", "aac", "ogg", "wav");

    private FileSupport() {
    }

    /** Вычисляет MD5 файла порциями, не загружая его целиком в память. */
    public static String md5(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[8192];
            try (InputStream input = Files.newInputStream(file)) {
                int count;
                while ((count = input.read(buffer)) >= 0) {
                    if (count > 0) {
                        digest.update(buffer, 0, count);
                    }
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("MD5 недоступен", exception);
        }
    }

    /** Проверяет расширение файла без учета регистра. */
    public static boolean isSupportedAudio(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot >= 0 && AUDIO_EXTENSIONS.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    /** Преобразует имя папки в устойчивый ключ книги. */
    public static String directoryKey(Path directory) {
        return directory.getFileName().toString().trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}._-]+", "-");
    }
}

