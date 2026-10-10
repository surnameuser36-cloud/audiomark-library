package ru.audiomark.client.model;

/** Компонент клиентской модели краткой карточки книги. */
public final class BookItem {
    /** Серверный идентификатор книги. */
    public final long id;
    /** Отображаемое название. */
    public final String title;
    /** Строка авторов. */
    public final String authors;
    /** Краткое описание. */
    public final String description;
    /** Количество аудиочастей. */
    public final int partCount;
    /** Относительный URL обложки на сервере. */
    public final String coverUrl;

    /** Создает краткую модель книги, полученную из каталога сервера. */
    public BookItem(long id, String title, String authors, String description,
                    int partCount, String coverUrl) {
        this.id = id;
        this.title = title;
        this.authors = authors;
        this.description = description;
        this.partCount = partCount;
        this.coverUrl = coverUrl;
    }
}

