package ru.audiomark.client.model;

/** Компонент клиентской модели части аудиокниги. */
public final class AudioPartItem {
    public final long id;
    public final int number;
    public final String name;
    public final String audioUrl;

    public AudioPartItem(long id, int number, String name, String audioUrl) {
        this.id = id;
        this.number = number;
        this.name = name;
        this.audioUrl = audioUrl;
    }

    @Override
    public String toString() {
        return "Часть " + number + ": " + name;
    }
}

