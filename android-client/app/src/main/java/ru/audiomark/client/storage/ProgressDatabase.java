package ru.audiomark.client.storage;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Компонент локальной базы клиента. Хранит последнюю часть, позицию и признак
 * полного прослушивания независимо от серверной учетной записи.
 */
public final class ProgressDatabase extends SQLiteOpenHelper {
    private static final String DB_NAME = "audiomark_progress.db";
    private static final int DB_VERSION = 1;

    public ProgressDatabase(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    /** Создает таблицу локального состояния книг. */
    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE progress (book_id INTEGER PRIMARY KEY, part_id INTEGER NOT NULL, "
                + "position_ms INTEGER NOT NULL, completed INTEGER NOT NULL DEFAULT 0)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Первая версия схемы не требует миграции.
    }

    /** Создает или заменяет запись прогресса. */
    public void save(long bookId, long partId, long positionMs, boolean completed) {
        ContentValues values = new ContentValues();
        values.put("book_id", bookId);
        values.put("part_id", partId);
        values.put("position_ms", Math.max(0, positionMs));
        values.put("completed", completed ? 1 : 0);
        getWritableDatabase().insertWithOnConflict("progress", null, values,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    /** Возвращает сохраненную позицию либо начальное состояние. */
    public Progress load(long bookId) {
        try (Cursor cursor = getReadableDatabase().query("progress",
                new String[]{"part_id", "position_ms", "completed"}, "book_id=?",
                new String[]{Long.toString(bookId)}, null, null, null)) {
            if (cursor.moveToFirst()) {
                return new Progress(cursor.getLong(0), cursor.getLong(1), cursor.getInt(2) == 1);
            }
        }
        return new Progress(-1, 0, false);
    }

    /** Проверяет, находится ли книга в категории «Прослушано». */
    public boolean isCompleted(long bookId) {
        return load(bookId).completed;
    }

    /** Неизменяемый объект результата чтения прогресса. */
    public static final class Progress {
        public final long partId;
        public final long positionMs;
        public final boolean completed;

        public Progress(long partId, long positionMs, boolean completed) {
            this.partId = partId;
            this.positionMs = positionMs;
            this.completed = completed;
        }
    }
}

