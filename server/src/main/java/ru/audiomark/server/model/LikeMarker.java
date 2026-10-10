package ru.audiomark.server.model;

/** Сгруппированная отметка «Нравится» на временной позиции части. */
public record LikeMarker(long partId, long positionMs, int count) {
}

