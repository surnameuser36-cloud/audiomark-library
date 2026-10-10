package ru.audiomark.client;

import android.app.Activity;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import ru.audiomark.client.model.AudioPartItem;
import ru.audiomark.client.model.BookDetails;
import ru.audiomark.client.model.CommentItem;
import ru.audiomark.client.net.ApiClient;
import ru.audiomark.client.storage.ProgressDatabase;

/**
 * Компонент воспроизведения. Управляет MediaPlayer, локальным прогрессом,
 * выбором части, комментариями и отметками на текущей позиции.
 */
public final class PlayerActivity extends Activity {
    private final Handler handler = new Handler();
    private final List<AudioPartItem> parts = new ArrayList<>();
    private MediaPlayer player;
    private ProgressDatabase progressDatabase;
    private ProgressDatabase.Progress savedProgress;
    private long bookId;
    private AudioPartItem currentPart;
    private boolean prepared;
    private boolean seekingByUser;
    /** Последняя позиция, записанная в SQLite; ограничивает частоту операций. */
    private long lastPersistedPosition = -5_000;
    private Spinner partSpinner;
    private SeekBar seekBar;
    private TextView timeText;
    private TextView titleText;
    private TextView authorText;
    private TextView descriptionText;
    private LinearLayout commentsContainer;
    private EditText commentInput;
    private Button playButton;
    private Button likeButton;

    /** Периодически обновляет ползунок и сохраняет позицию. */
    private final Runnable progressTask = new Runnable() {
        @Override public void run() {
            if (prepared && player != null) {
                int position = player.getCurrentPosition();
                int duration = player.getDuration();
                if (!seekingByUser) seekBar.setProgress(position);
                updateTime(position, duration);
                if (Math.abs(position - lastPersistedPosition) >= 5_000) {
                    saveProgress(position, duration);
                    lastPersistedPosition = position;
                }
            }
            handler.postDelayed(this, 1000);
        }
    };

    /** Связывает компоненты и загружает карточку книги. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);
        InsetsHelper.apply(this);
        bookId = getIntent().getLongExtra("bookId", -1);
        progressDatabase = new ProgressDatabase(this);
        savedProgress = progressDatabase.load(bookId);
        bindViews();
        configureControls();
        loadBook();
        handler.post(progressTask);
    }

    /** Находит визуальные компоненты по идентификаторам ресурсов. */
    private void bindViews() {
        titleText = findViewById(R.id.playerTitle);
        authorText = findViewById(R.id.playerAuthor);
        descriptionText = findViewById(R.id.playerDescription);
        partSpinner = findViewById(R.id.partSpinner);
        seekBar = findViewById(R.id.playerSeek);
        timeText = findViewById(R.id.timeText);
        commentsContainer = findViewById(R.id.commentsContainer);
        commentInput = findViewById(R.id.commentInput);
        playButton = findViewById(R.id.playButton);
        likeButton = findViewById(R.id.likeButton);
    }

    /** Назначает обработчики кнопок, выбора части и перемотки. */
    private void configureControls() {
        playButton.setOnClickListener(view -> togglePlayback());
        likeButton.setOnClickListener(view -> sendLike());
        findViewById(R.id.commentButton).setOnClickListener(view -> sendComment());
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                if (fromUser && prepared) updateTime(value, player.getDuration());
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { seekingByUser = true; }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                if (prepared) player.seekTo(bar.getProgress());
                seekingByUser = false;
            }
        });
    }

    /** Загружает части и метаданные книги в фоновом потоке. */
    private void loadBook() {
        new AsyncTask<Void, Void, BookDetails>() {
            private Exception error;
            @Override protected BookDetails doInBackground(Void... values) {
                try { return ApiClient.loadBook(bookId); }
                catch (Exception exception) { error = exception; return null; }
            }
            @Override protected void onPostExecute(BookDetails details) {
                if (details == null) {
                    showMessage("Не удалось загрузить книгу: " + error.getMessage());
                    return;
                }
                titleText.setText(details.title);
                authorText.setText(details.authors);
                descriptionText.setText(details.description);
                likeButton.setText("Нравится · " + details.likeCount);
                parts.clear();
                parts.addAll(details.parts);
                ArrayAdapter<AudioPartItem> adapter = new ArrayAdapter<>(PlayerActivity.this,
                        android.R.layout.simple_spinner_dropdown_item, parts);
                partSpinner.setAdapter(adapter);
                partSpinner.setOnItemSelectedListener(new SimpleItemSelectedListener(position -> {
                    AudioPartItem selected = parts.get(position);
                    if (currentPart == null || currentPart.id != selected.id) preparePart(selected);
                }));
                int savedIndex = findPartIndex(savedProgress.partId);
                partSpinner.setSelection(Math.max(0, savedIndex));
                loadComments();
            }
        }.execute();
    }

    /** Возвращает индекс сохраненной части либо ноль. */
    private int findPartIndex(long partId) {
        for (int index = 0; index < parts.size(); index++) {
            if (parts.get(index).id == partId) return index;
        }
        return 0;
    }

    /** Освобождает старый проигрыватель и асинхронно готовит выбранную часть. */
    private void preparePart(AudioPartItem part) {
        releasePlayer();
        currentPart = part;
        prepared = false;
        lastPersistedPosition = -5_000;
        playButton.setText("Загрузка…");
        player = new MediaPlayer();
        player.setAudioStreamType(AudioManager.STREAM_MUSIC);
        try {
            player.setDataSource(ApiClient.BASE_URL + part.audioUrl);
            player.setOnPreparedListener(mediaPlayer -> {
                prepared = true;
                seekBar.setMax(mediaPlayer.getDuration());
                if (savedProgress.partId == part.id && savedProgress.positionMs > 0) {
                    mediaPlayer.seekTo((int) Math.min(savedProgress.positionMs, mediaPlayer.getDuration()));
                }
                playButton.setText(R.string.play);
                updateTime(mediaPlayer.getCurrentPosition(), mediaPlayer.getDuration());
            });
            player.setOnCompletionListener(mediaPlayer -> onPartCompleted());
            player.setOnErrorListener((mediaPlayer, what, extra) -> {
                showMessage("Не удалось воспроизвести аудиофайл");
                playButton.setText(R.string.play);
                return true;
            });
            player.prepareAsync();
        } catch (Exception exception) {
            showMessage("Ошибка проигрывателя: " + exception.getMessage());
        }
    }

    /** Переключает воспроизведение и паузу. */
    private void togglePlayback() {
        if (!prepared || player == null) return;
        if (player.isPlaying()) {
            player.pause();
            playButton.setText(R.string.play);
        } else {
            player.start();
            playButton.setText(R.string.pause);
        }
    }

    /** Переходит к следующей части или фиксирует завершение последней. */
    private void onPartCompleted() {
        int index = partSpinner.getSelectedItemPosition();
        if (index >= 0 && index < parts.size() - 1) {
            partSpinner.setSelection(index + 1);
        } else if (currentPart != null) {
            progressDatabase.save(bookId, currentPart.id, player.getDuration(), true);
            playButton.setText(R.string.play);
            showMessage("Книга добавлена в категорию «Прослушано»");
        }
    }

    /** Сохраняет позицию и выставляет завершение только для последней части. */
    private void saveProgress(long position, long duration) {
        if (currentPart == null || duration <= 0) return;
        boolean lastPart = partSpinner.getSelectedItemPosition() == parts.size() - 1;
        boolean completed = lastPart && position >= Math.round(duration * 0.95);
        progressDatabase.save(bookId, currentPart.id, position, completed || savedProgress.completed);
    }

    /** Добавляет комментарий к текущей части и позиции. */
    private void sendComment() {
        if (!prepared || currentPart == null) return;
        String text = commentInput.getText().toString().trim();
        if (text.isEmpty()) {
            showMessage("Введите текст комментария");
            return;
        }
        long position = player.getCurrentPosition();
        new AsyncTask<Void, Void, Exception>() {
            @Override protected Exception doInBackground(Void... values) {
                try { ApiClient.addComment(bookId, currentPart.id, position, "Слушатель", text); return null; }
                catch (Exception exception) { return exception; }
            }
            @Override protected void onPostExecute(Exception error) {
                if (error == null) { commentInput.setText(""); loadComments(); }
                else showMessage("Комментарий не отправлен");
            }
        }.execute();
    }

    /** Добавляет отметку «Нравится» к текущей временной точке. */
    private void sendLike() {
        if (!prepared || currentPart == null) return;
        long position = player.getCurrentPosition();
        String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        if (deviceId == null || deviceId.trim().isEmpty()) {
            deviceId = "android-device";
        }
        final String stableDeviceId = deviceId;
        new AsyncTask<Void, Void, Integer>() {
            @Override protected Integer doInBackground(Void... values) {
                try { return ApiClient.addLike(bookId, currentPart.id, position, stableDeviceId); }
                catch (Exception exception) { return -1; }
            }
            @Override protected void onPostExecute(Integer count) {
                if (count >= 0) likeButton.setText("Нравится · " + count);
                else showMessage("Отметка не отправлена");
            }
        }.execute();
    }

    /** Загружает комментарии и создает кликабельные строки временной шкалы. */
    private void loadComments() {
        new AsyncTask<Void, Void, List<CommentItem>>() {
            @Override protected List<CommentItem> doInBackground(Void... values) {
                try { return ApiClient.loadComments(bookId); }
                catch (Exception exception) { return new ArrayList<>(); }
            }
            @Override protected void onPostExecute(List<CommentItem> comments) {
                commentsContainer.removeAllViews();
                if (comments.isEmpty()) {
                    TextView empty = createCommentView("Комментариев пока нет");
                    commentsContainer.addView(empty);
                    return;
                }
                for (CommentItem comment : comments) {
                    TextView view = createCommentView(formatTime(comment.positionMs) + "  "
                            + comment.author + ": " + comment.text);
                    view.setOnClickListener(clicked -> jumpToComment(comment));
                    commentsContainer.addView(view);
                }
            }
        }.execute();
    }

    /** Создает визуальную карточку комментария программным способом. */
    private TextView createCommentView(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(15);
        view.setTextColor(getResources().getColor(R.color.text_primary));
        view.setBackgroundResource(R.drawable.card_background);
        int padding = (int) (12 * getResources().getDisplayMetrics().density);
        view.setPadding(padding, padding, padding, padding);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = padding / 2;
        view.setLayoutParams(params);
        return view;
    }

    /** Выбирает нужную часть и после подготовки переводит проигрыватель к метке. */
    private void jumpToComment(CommentItem comment) {
        int index = findPartIndex(comment.partId);
        if (index < 0) return;
        savedProgress = new ProgressDatabase.Progress(comment.partId, comment.positionMs, false);
        if (partSpinner.getSelectedItemPosition() == index && prepared) {
            player.seekTo((int) comment.positionMs);
        } else {
            partSpinner.setSelection(index);
        }
    }

    /** Обновляет текст текущей и общей длительности. */
    private void updateTime(long position, long duration) {
        timeText.setText(formatTime(position) + " / " + formatTime(duration));
    }

    /** Форматирует миллисекунды в понятный вид. */
    private static String formatTime(long milliseconds) {
        long seconds = Math.max(0, milliseconds) / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long tail = seconds % 60;
        return hours > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, tail)
                : String.format(Locale.ROOT, "%d:%02d", minutes, tail);
    }

    /** Показывает краткое уведомление пользователю. */
    private void showMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    /** Сохраняет прогресс и освобождает MediaPlayer при закрытии экрана. */
    @Override
    protected void onDestroy() {
        handler.removeCallbacks(progressTask);
        if (prepared && player != null) saveProgress(player.getCurrentPosition(), player.getDuration());
        releasePlayer();
        progressDatabase.close();
        super.onDestroy();
    }

    /** Безопасно освобождает системные аудиоресурсы. */
    private void releasePlayer() {
        prepared = false;
        if (player != null) {
            player.reset();
            player.release();
            player = null;
        }
    }

    /** Компактный адаптер обратного вызова Spinner. */
    private static final class SimpleItemSelectedListener implements android.widget.AdapterView.OnItemSelectedListener {
        private final SelectionAction action;
        private SimpleItemSelectedListener(SelectionAction action) { this.action = action; }
        @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
            action.accept(position);
        }
        @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
    }

    /** Функциональный интерфейс выбора части. */
    private interface SelectionAction { void accept(int position); }
}

