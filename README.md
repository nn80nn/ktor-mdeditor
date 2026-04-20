# ktor-mdeditor — бэкенд

REST API для MDEditor приложения. Хранит markdown файлы пользователей, авторизация через JWT.

## Что есть

- регистрация и вход (JWT токены)
- загрузка и скачивание .md файлов
- список документов пользователя
- удаление документов

## Эндпоинты

```
POST /auth/register
POST /auth/login

GET    /documents
POST   /documents
GET    /documents/{id}
DELETE /documents/{id}
```

## Стек

- Ktor 3.x + Netty
- PostgreSQL + Exposed
- BCrypt для паролей
- JWT для авторизации

## Запуск локально

```
./gradlew run
```

Нужен PostgreSQL. Переменные окружения:

```
DB_URL=jdbc:postgresql://localhost:5432/mdeditor
DB_USER=postgres
DB_PASSWORD=...
JWT_SECRET=...
```

## Деплой

Деплоится через Dokploy автоматически при пуше в master.
