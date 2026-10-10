package ru.audiomark.client;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import ru.audiomark.client.model.BookItem;
import ru.audiomark.client.net.ApiClient;
import ru.audiomark.client.storage.ProgressDatabase;

/** Главный компонент интерфейса: каталог, клиентский поиск и категории. */
public final class MainActivity extends Activity {
    /** Полный каталог, загруженный с сервера. */
    private final List<BookItem> allBooks = new ArrayList<>();
    private BookAdapter adapter;
    private EditText searchInput;
    private TextView statusText;
    private ProgressDatabase progressDatabase;
    /** true означает фильтрацию только завершенных книг. */
    private boolean completedOnly;

    /** Инициализирует компоненты интерфейса и начинает загрузку каталога. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        InsetsHelper.apply(this);
        progressDatabase = new ProgressDatabase(this);
        searchInput = findViewById(R.id.searchInput);
        statusText = findViewById(R.id.statusText);
        ListView booksList = findViewById(R.id.booksList);
        Button allButton = findViewById(R.id.allButton);
        Button completedButton = findViewById(R.id.completedButton);
        adapter = new BookAdapter(this);
        booksList.setAdapter(adapter);

        allButton.setOnClickListener(view -> { completedOnly = false; applyFilter(); });
        completedButton.setOnClickListener(view -> { completedOnly = true; applyFilter(); });
        booksList.setOnItemClickListener((parent, view, position, id) -> openBook(adapter.getItem(position)));
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { applyFilter(); }
            @Override public void afterTextChanged(Editable s) { }
        });
        loadCatalog();
    }

    /** Обновляет категорию после возврата с экрана прослушивания. */
    @Override
    protected void onResume() {
        super.onResume();
        if (adapter != null) applyFilter();
    }

    /** Открывает проигрыватель и передает серверный идентификатор книги. */
    private void openBook(BookItem book) {
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra("bookId", book.id);
        startActivity(intent);
    }

    /** Загружает каталог в фоновом потоке, не блокируя интерфейс. */
    private void loadCatalog() {
        statusText.setText("Загрузка каталога…");
        new AsyncTask<Void, Void, List<BookItem>>() {
            private Exception error;
            @Override protected List<BookItem> doInBackground(Void... values) {
                try { return ApiClient.loadBooks(); }
                catch (Exception exception) { error = exception; return new ArrayList<>(); }
            }
            @Override protected void onPostExecute(List<BookItem> books) {
                allBooks.clear();
                allBooks.addAll(books);
                statusText.setText(error == null ? "Найдено книг: " + books.size()
                        : "Сервер недоступен. Проверьте подключение.");
                applyFilter();
            }
        }.execute();
    }

    /** Фильтрует локальный список по строке и признаку завершения. */
    private void applyFilter() {
        String query = searchInput == null ? "" : searchInput.getText().toString()
                .trim().toLowerCase(Locale.ROOT);
        List<BookItem> filtered = new ArrayList<>();
        for (BookItem book : allBooks) {
            boolean matches = query.isEmpty()
                    || book.title.toLowerCase(Locale.ROOT).contains(query)
                    || book.authors.toLowerCase(Locale.ROOT).contains(query);
            if (matches && (!completedOnly || progressDatabase.isCompleted(book.id))) {
                filtered.add(book);
            }
        }
        adapter.replace(filtered);
        if (!allBooks.isEmpty()) statusText.setText("Показано: " + filtered.size());
    }
}

