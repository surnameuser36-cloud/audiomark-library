# HTTP API

| Метод | Путь | Назначение |
|---|---|---|
| GET | `/api/health` | Проверка состояния |
| GET | `/api/books` | Каталог книг |
| GET | `/api/books/{id}` | Карточка книги и список частей |
| GET | `/api/books/{id}/cover` | Обложка |
| GET | `/api/books/{id}/parts/{partId}/audio` | Поток аудио с поддержкой Range |
| GET | `/api/books/{id}/comments` | Комментарии и временные метки |
| POST | `/api/books/{id}/comments` | Добавление комментария |
| GET | `/api/books/{id}/likes` | Сгруппированные отметки на временной шкале |
| POST | `/api/books/{id}/likes` | Добавление отметки «Нравится» |

Тело POST-запроса передается в JSON. Для комментария используются поля `partId`, `positionMs`, `author` и `text`; для отметки — `partId`, `positionMs` и `deviceId`.

