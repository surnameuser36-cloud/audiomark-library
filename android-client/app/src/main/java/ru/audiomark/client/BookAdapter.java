package ru.audiomark.client;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import ru.audiomark.client.model.BookItem;

/** Компонент отображения карточек книг в стандартном ListView. */
public final class BookAdapter extends BaseAdapter {
    private final LayoutInflater inflater;
    /** Загрузчик и кэш обложек для переиспользуемых строк списка. */
    private final CoverLoader coverLoader;
    /** Текущий отфильтрованный список. */
    private final List<BookItem> books = new ArrayList<>();

    /** Создает адаптер каталога для указанного контекста. */
    public BookAdapter(Context context) {
        inflater = LayoutInflater.from(context);
        coverLoader = new CoverLoader();
    }

    /** Полностью заменяет отображаемые элементы. */
    public void replace(List<BookItem> values) {
        books.clear();
        books.addAll(values);
        notifyDataSetChanged();
    }

    @Override public int getCount() { return books.size(); }
    @Override public BookItem getItem(int position) { return books.get(position); }
    @Override public long getItemId(int position) { return books.get(position).id; }

    /** Заполняет переиспользуемую карточку обложкой и сведениями о книге. */
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView;
        ViewHolder holder;
        if (view == null) {
            view = inflater.inflate(R.layout.item_book, parent, false);
            holder = new ViewHolder(view);
            view.setTag(holder);
        } else {
            holder = (ViewHolder) view.getTag();
        }
        BookItem book = getItem(position);
        holder.title.setText(book.title);
        holder.author.setText(book.authors);
        holder.parts.setText("Частей: " + book.partCount);
        coverLoader.load(holder.cover, book.coverUrl);
        return view;
    }

    /** Хранит ссылки на компоненты строки и уменьшает число вызовов findViewById. */
    private static final class ViewHolder {
        /** Изображение обложки. */
        private final ImageView cover;
        /** Название книги. */
        private final TextView title;
        /** Автор или авторы книги. */
        private final TextView author;
        /** Количество аудиочастей. */
        private final TextView parts;

        /** Находит компоненты в созданной строке списка. */
        private ViewHolder(View view) {
            cover = view.findViewById(R.id.bookCover);
            title = view.findViewById(R.id.bookTitle);
            author = view.findViewById(R.id.bookAuthor);
            parts = view.findViewById(R.id.bookParts);
        }
    }
}

