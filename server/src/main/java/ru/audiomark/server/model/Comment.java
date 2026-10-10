package ru.audiomark.server.model;

/** Компонент общего комментария, связанного с частью и временной позицией. */
public record Comment(long id, long bookId, long partId, long positionMs,
                      String author, String text, String createdAt) {
}

