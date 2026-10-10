package ru.audiomark.server.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Компонент минимальной обработки JSON. Сервер формирует ответы и разбирает
 * плоские тела запросов без подключения отдельного веб-фреймворка.
 */
public final class Json {
    private Json() {
    }

    /** Экранирует строковую переменную перед включением в JSON. */
    public static String quote(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder result = new StringBuilder(value.length() + 2);
        result.append('"');
        for (int i = 0; i < value.length(); i++) {
            char symbol = value.charAt(i);
            switch (symbol) {
                case '"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if (symbol < 32) {
                        result.append(String.format("\\u%04x", (int) symbol));
                    } else {
                        result.append(symbol);
                    }
                }
            }
        }
        return result.append('"').toString();
    }

    /** Возвращает строковое поле плоского JSON-объекта или значение по умолчанию. */
    public static String stringField(String json, String name, String defaultValue) {
        Pattern pattern = Pattern.compile("\\\"" + Pattern.quote(name)
                + "\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"");
        Matcher matcher = pattern.matcher(json == null ? "" : json);
        return matcher.find() ? unescape(matcher.group(1)) : defaultValue;
    }

    /** Возвращает целочисленное поле плоского JSON-объекта. */
    public static long longField(String json, String name, long defaultValue) {
        Pattern pattern = Pattern.compile("\\\"" + Pattern.quote(name)
                + "\\\"\\s*:\\s*(-?\\d+)");
        Matcher matcher = pattern.matcher(json == null ? "" : json);
        if (!matcher.find()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }

    /** Восстанавливает основные управляющие последовательности JSON. */
    private static String unescape(String value) {
        StringBuilder result = new StringBuilder(value.length());
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char symbol = value.charAt(i);
            if (!escaped && symbol == '\\') {
                escaped = true;
                continue;
            }
            if (escaped) {
                switch (symbol) {
                    case 'n' -> result.append('\n');
                    case 'r' -> result.append('\r');
                    case 't' -> result.append('\t');
                    case '"' -> result.append('"');
                    case '\\' -> result.append('\\');
                    default -> result.append(symbol);
                }
                escaped = false;
            } else {
                result.append(symbol);
            }
        }
        return result.toString();
    }
}

