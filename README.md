# AudioMark Library

AudioMark Library — программный комплекс для каталогизации и прослушивания аудиокниг с общими комментариями и отметками «Нравится» на временной шкале.

## Состав

- `android-client` — мобильный клиент на Java для Android 4.2 и новее;
- `server` — Java-сервер, индексатор аудиофайлов и HTTP API;
- `docs` — руководство пользователя, собираемое MkDocs;
- `docker-compose.yml` — запуск сервера и подключение постоянных томов.

## Быстрый запуск сервера

1. Поместите книги в `server/data/audio`. Каждая книга хранится в отдельной папке.
2. При необходимости добавьте в папку `book.properties` и `cover.png`.
3. Выполните `docker compose up --build`.
4. Откройте `http://localhost:8080/api/health`. Ожидаемый ответ: `{"status":"ok"}`.

Пример `book.properties`:

```properties
title=Мастер и Маргарита
authors=Михаил Булгаков
description=Роман в аудиоформате
```

## Запуск Android-клиента

1. Откройте каталог `android-client` в Android Studio.
2. Дождитесь синхронизации Gradle.
3. Запустите конфигурацию `app` на AVD или устройстве с Android 4.2+.
4. Для эмулятора используется адрес сервера `http://10.0.2.2:8080`.

Адрес можно изменить в `app/src/main/java/ru/audiomark/client/net/ApiClient.java`.

## Тесты

```bash
cd server
mvn test
```

## Документация

```bash
python -m pip install -r docs/requirements.txt
mkdocs serve -f mkdocs.yml
```

После запуска руководство доступно по адресу `http://127.0.0.1:8000`.

## Репозиторий

`https://github.com/surnameuser36-cloud/audiomark-library`

