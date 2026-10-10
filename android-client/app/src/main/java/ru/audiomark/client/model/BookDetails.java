package ru.audiomark.client.model;

import java.util.List;

/** Компонент полной карточки книги, используемой экраном проигрывателя. */
public final class BookDetails {
    public final String title;
    public final String authors;
    public final String description;
    public final int likeCount;
    public final List<AudioPartItem> parts;

    public BookDetails(String title, String authors, String description,
                       int likeCount, List<AudioPartItem> parts) {
        this.title = title;
        this.authors = authors;
        this.description = description;
        this.likeCount = likeCount;
        this.parts = parts;
    }
}

