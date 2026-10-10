package ru.audiomark.server.model;

/** Компонент модели отдельной части аудиокниги. */
public record AudioPart(long id, long bookId, int partNumber, String fileName,
                        String filePath, String md5, long sizeBytes) {
}

