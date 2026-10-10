package ru.audiomark.server.model;

/**
 * Компонент модели книги. Объединяет метаданные, путь к обложке и количество
 * частей, найденных индексатором.
 */
public record Book(long id, String directoryKey, String title, String authors,
                   String description, String coverPath, int partCount) {
}

