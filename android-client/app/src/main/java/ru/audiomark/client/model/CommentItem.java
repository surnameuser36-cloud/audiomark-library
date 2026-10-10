package ru.audiomark.client.model;

/** Компонент клиентской модели общего комментария. */
public final class CommentItem {
    public final long partId;
    public final long positionMs;
    public final String author;
    public final String text;

    public CommentItem(long partId, long positionMs, String author, String text) {
        this.partId = partId;
        this.positionMs = positionMs;
        this.author = author;
        this.text = text;
    }
}

