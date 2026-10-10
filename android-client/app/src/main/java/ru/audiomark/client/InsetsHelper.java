package ru.audiomark.client;

import android.annotation.TargetApi;
import android.app.Activity;
import android.graphics.Insets;
import android.os.Build;
import android.view.View;
import android.view.WindowInsets;

/**
 * Компонент безопасных отступов. Не позволяет содержимому Activity оказаться
 * под строкой состояния, вырезом камеры или панелью навигации.
 */
public final class InsetsHelper {

    /**
     * Добавляет к исходным отступам корневого контейнера размеры системных зон.
     * На Android 4.2 и 4.3 система сама размещает обычное окно ниже статус-бара.
     */
    public static void apply(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT_WATCH) {
            return;
        }
        applyFromApi20(activity);
    }

    /** Устанавливает слушатель WindowInsets на версиях, где он доступен. */
    @TargetApi(Build.VERSION_CODES.KITKAT_WATCH)
    private static void applyFromApi20(Activity activity) {
        View content = activity.findViewById(android.R.id.content);
        /** Исходные отступы сохраняются, чтобы не потерять настройки контейнера. */
        final int initialLeft = content.getPaddingLeft();
        final int initialTop = content.getPaddingTop();
        final int initialRight = content.getPaddingRight();
        final int initialBottom = content.getPaddingBottom();

        content.setOnApplyWindowInsetsListener((view, windowInsets) -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                applyFromApi30(view, windowInsets, initialLeft, initialTop,
                        initialRight, initialBottom);
            } else {
                view.setPadding(
                        initialLeft + windowInsets.getSystemWindowInsetLeft(),
                        initialTop + windowInsets.getSystemWindowInsetTop(),
                        initialRight + windowInsets.getSystemWindowInsetRight(),
                        initialBottom + windowInsets.getSystemWindowInsetBottom()
                );
            }
            return windowInsets;
        });
        content.requestApplyInsets();
    }

    /** Учитывает одновременно системные панели и физический вырез экрана. */
    @TargetApi(Build.VERSION_CODES.R)
    private static void applyFromApi30(View view, WindowInsets windowInsets,
                                       int initialLeft, int initialTop,
                                       int initialRight, int initialBottom) {
        Insets safeArea = windowInsets.getInsets(
                WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout()
        );
        view.setPadding(
                initialLeft + safeArea.left,
                initialTop + safeArea.top,
                initialRight + safeArea.right,
                initialBottom + safeArea.bottom
        );
    }

    /** Запрещает создание экземпляров служебного класса. */
    private InsetsHelper() {
    }
}
