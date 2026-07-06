# Messenger — лёгкий WebSocket-мессенджер

Асинхронный мессенджер с веб-интерфейсом, работающий целиком через **WebSocket** (единое постоянное соединение). Серверная часть — **Java 17 + Javalin**, клиентская — **vanilla JS SPA** (без фреймворков), база данных — **PostgreSQL** (raw JDBC, без ORM).

Такой минималистичный стек выбран осознанно: приложение спроектировано для работы на **слабом серверном оборудовании** (одноплатные компьютеры, VPS с 512 MB RAM), где каждый мегабайт памяти и каждый процессорный такт на счету. Javalin и raw JDBC потребляют на порядок меньше ресурсов, чем тяжеловесные Spring Boot + Hibernate.

---

## Содержание

1. [Архитектура](#1-архитектура)
2. [Структура проекта](#2-структура-проекта)
3. [Протокол WebSocket](#3-протокол-websocket)
4. [База данных](#4-база-данных)
5. [Backend (Java)](#5-backend-java)
6. [Frontend (JavaScript SPA)](#6-frontend-javascript-spa)
7. [Установка и запуск](#7-установка-и-запуск)

---

## 1. Архитектура

```
┌─────────────────────────────────────────────────────────────────────┐
│                         Браузер (SPA)                              │
│  ┌─────────┐ ┌──────────┐ ┌────────┐ ┌────────────┐ ┌──────────┐  │
│  │ api.js  │ │ auth.js  │ │chat.js │ │channels.js │ │channel-  │  │
│  │(WebSock)│ │(login/   │ │(msgs)  │ │(list/      │ │info.js   │  │
│  │         │ │ register)│ │        │ │ create)    │ │(members/ │  │
│  │         │ │          │ │        │ │            │ │ settings)│  │
│  └────┬────┘ └──────────┘ └────────┘ └────────────┘ └──────────┘  │
│       │                  app.js (ядро SPA)                         │
│       │              push-handler.js (push-события)                │
│       │                  utils.js, style.css                        │
│       └─────────────────────────────────────── WS ────────────────┘
└──────────────────────────────────────────────────────────────────────┘
                          │  ws://host:7070/websocket
                          ▼
┌─────────────────────────────────────────────────────────────────────┐
│                   Сервер (Java 17 + Javalin)                       │
│                                                                     │
│  ┌──────────────┐  ┌─────────────────────────────────────────────┐ │
│  │  Messenger   │  │  WebSocket-обработчик:                       │ │
│  │  (main)      │  │  · onConnect → регистрация сессии           │ │
│  │              │  │  · onMessage → парсинг JSON → роутинг       │ │
│  │  · Javalin   │  │    REGISTER/LOGIN → AuthController          │ │
│  │  · CORS      │  │    *MESSAGE*    → MessageController         │ │
│  │  · port 7070 │  │    остальное    → ChannelController          │ │
│  └──────────────┘  │  · onClose/onError → очистка сессии         │ │
│                    └─────────────────────────────────────────────┘ │
│                              │                                      │
│         ┌────────────────────┼────────────────────┐                │
│         ▼                    ▼                    ▼                │
│  ┌──────────────┐   ┌──────────────┐   ┌──────────────────┐       │
│  │ AuthController│  │ChannelControl│   │MessageController │       │
│  │ · REGISTER    │  │ · CREATE     │   │ · CREATE_MESSAGE │       │
│  │ · LOGIN       │  │ · SEARCH     │   │ · UPDATE_MESSAGE │       │
│  │               │  │ · UPDATE     │   │ · DELETE_MESSAGE │       │
│  │ JwtService    │  │ · DELETE     │   │ · SEARCH_MESSAGES│       │
│  │ BCrypt        │  │ · ADD_MEMBER │   │                  │       │
│  │               │  │ · REMOVE_MEM │   │ broadcastToChnl  │       │
│  │               │  │ · GET_MEMBERS│   │ (push всем,      │       │
│  │               │  │              │   │  кроме отправит.)│       │
│  └───────┬───────┘  └──────┬───────┘   └────────┬─────────┘       │
│          │                 │                     │                  │
│          ▼                 ▼                     ▼                  │
│   ┌──────────────────────────────────────────────────────────┐    │
│   │              DAO-слой (raw JDBC)                          │    │
│   │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌───────────┐   │    │
│   │  │ UserDao  │ │AccessDao │ │ChannelDao│ │MessageDao │   │    │
│   │  └──────────┘ └──────────┘ └──────────┘ └───────────┘   │    │
│   │  ┌──────────────┐ ┌─────────────────────────────┐       │    │
│   │  │ UserChannel  │ │ DbConfig (JDBC → PostgreSQL)│       │    │
│   │  │ Dao          │ └─────────────────────────────┘       │    │
│   │  └──────────────┘                                       │    │
│   └──────────────────────────────────────────────────────────┘    │
│                              │                                      │
│                              ▼                                      │
│                    ┌──────────────────┐                            │
│                    │   PostgreSQL     │                            │
│                    │   messenger_db   │                            │
│                    └──────────────────┘                            │
└────────────────────────────────────────────────────────────────────┘
```

### Ключевые особенности архитектуры

- **Единое соединение**: после логина WebSocket остаётся открытым всё время. Все запросы и push-уведомления ходят по нему.
- **Запрос-ответ**: каждому запросу присваивается `requestId`, сервер возвращает его в ответе — клиент сопоставляет ответ с колбэком.
- **Push-уведомления**: сервер отправляет события без `requestId` (новое сообщение, изменение канала) — клиент направляет их в `PushHandler`.
- **Нет REST**: всё общение — только через WebSocket.
- **Нет ORM**: чистая JDBC с ручным маппингом (DTO → ResultSet).
- **Лёгкий сервер**: Javalin (всего ~2 MB) вместо Spring Boot (~40 MB).

---

## 2. Структура проекта

```
Messenger/
│
├── frontend/                          # Клиентская часть (SPA)
│   ├── index.html                     # Точка входа — загружает все скрипты
│   ├── css/
│   │   └── style.css                  # Все стили (тёмная + светлая тема, адаптив)
│   ├── js/
│   │   ├── api.js                     # WebSocket-соединение, отправка/приём
│   │   ├── utils.js                   # formatDate, toggleTheme
│   │   ├── auth.js                    # Страница входа/регистрации
│   │   ├── app.js                     # Ядро SPA, навигация, состояние
│   │   ├── channels.js                # Левый сайдбар: список каналов
│   │   ├── chat.js                    # Правая панель: сообщения чата
│   │   ├── channel-info.js            # Панель "О канале" (участники, настройки)
│   │   └── push-handler.js            # Обработка push-событий от сервера
│   └── pages/                         # HTML-шаблоны (справочно)
│       ├── auth.html
│       └── main.html
│
├── src/main/java/com/mycompany/messenger/
│   ├── Messenger.java                 # Главный класс, точка входа
│   ├── controller/
│   │   ├── AuthController.java        # REGISTER / LOGIN
│   │   ├── ChannelController.java     # Управление каналами
│   │   ├── MessageController.java     # Сообщения
│   │   ├── FilesController.java       # Загрузка/скачивание файлов (REST HTTP)
│   │   └── UserController.java        # Заглушка (не реализован)
│   ├── dao/
│   │   ├── DbConfig.java              # Подключение к PostgreSQL
│   │   ├── DatabaseInit.java          # Автосоздание таблиц
│   │   ├── UserDao.java               # users
│   │   ├── AccessDao.java             # access (логины/пароли)
│   │   ├── ChannelDao.java            # channels
│   │   ├── UserChannelDao.java        # user_channels (M:N)
│   │   ├── MessageDao.java            # messages
│   │   └── FileDao.java              # files
│   ├── dto/
│   │   ├── UserDto.java               # Пользователь
│   │   ├── AccessDto.java             # Учётные данные
│   │   ├── ChannelDto.java            # Канал
│   │   ├── UserChannelDto.java        # Связь пользователь-канал
│   │   ├── MessageDto.java            # Сообщение
│   │   └── FileDto.java              # Файл
│   └── util/
│       └── JwtService.java            # Генерация/валидация JWT (HS256)
│
├── pom.xml                            # Maven (Java 17, Javalin, Jackson, JJWT, jBCrypt)
├── init_db.sql                        # SQL для инициализации БД + тестовые данные
│
└── test_requests.html                 # Инструмент для ручного тестирования WebSocket
```

---

## 3. Протокол WebSocket

### 3.1. Общий формат

**Запрос (клиент → сервер):**
```json
{
  "action": "НАЗВАНИЕ_ДЕЙСТВИЯ",
  "token": "jwt-токен",
  "requestId": 123,
  ...поля, специфичные для действия
}
```

**Ответ (сервер → клиент) — на запрос с requestId:**
```json
{
  "action": "НАЗВАНИЕ_ДЕЙСТВИЯ",
  "status": "SUCCESS" | "ERROR",
  "payload": { ... },
  "requestId": 123
}
```

**Push-уведомление (сервер → клиент) — без requestId:**
```json
{
  "action": "NEW_MESSAGE" | "MESSAGE_UPDATED" | ...,
  "payload": { ... }
}
```

### 3.2. Полный список действий

| Действие | Направление | Описание |
|---|---|---|
| `REGISTER` | C → S | Регистрация нового пользователя |
| `LOGIN` | C → S | Вход в систему |
| `PING` | C → S | Heartbeat (сервер игнорирует) |
| `CREATE_CHANNEL` | C → S | Создать канал |
| `SEARCH_CHANNELS` | C → S | Поиск каналов пользователя |
| `UPDATE_CHANNEL` | C → S | Обновить канал (владелец) |
| `DELETE_CHANNEL` | C → S | Удалить канал (владелец) |
| `ADD_MEMBER` | C → S | Добавить участника |
| `REMOVE_MEMBER` | C → S | Удалить участника (владелец) |
| `GET_CHANNEL_MEMBERS` | C → S | Список участников канала |
| `CREATE_MESSAGE` | C → S | Отправить сообщение |
| `UPDATE_MESSAGE` | C → S | Редактировать сообщение (автор) |
| `DELETE_MESSAGE` | C → S | Удалить сообщение (автор) |
| `SEARCH_MESSAGES` | C → S | Поиск сообщений в канале |
| **`NEW_MESSAGE`** | **S → C** | **Push**: новое сообщение |
| **`MESSAGE_UPDATED`** | **S → C** | **Push**: сообщение изменено |
| **`MESSAGE_DELETED`** | **S → C** | **Push**: сообщение удалено |
| **`CHANNEL_ADDED`** | **S → C** | **Push**: добавлен в канал |
| **`CHANNEL_UPDATED`** | **S → C** | **Push**: канал изменён |
| **`CHANNEL_DELETED`** | **S → C** | **Push**: канал удалён |
| **`REMOVED_FROM_CHANNEL`** | **S → C** | **Push**: удалён из канала |

### 3.3. Примеры запросов

**Регистрация:**
```json
→ {"action":"REGISTER","login":"alice","password":"qwerty","name":"Алиса","surname":"Иванова"}
← {"action":"REGISTER","status":"SUCCESS","payload":{"token":"eyJhbG...","userCode":"uuid-xxx","name":"Алиса","surname":"Иванова"}}
```

**Вход:**
```json
→ {"action":"LOGIN","login":"alice","password":"qwerty"}
← {"action":"LOGIN","status":"SUCCESS","payload":{"token":"eyJhbG...","userCode":"uuid-xxx"}}
```

**Создание канала:**
```json
→ {"action":"CREATE_CHANNEL","token":"eyJhbG...","name":"Друзья","description":"Для общения"}
← {"action":"CREATE_CHANNEL","status":"SUCCESS","payload":{"code":"uuid","name":"Друзья","description":"...","creationDate":"2026-07-06T12:00:00"}}
```

**Отправка сообщения:**
```json
→ {"action":"CREATE_MESSAGE","token":"...","channelCode":"uuid","text":"Всем привет!"}
← (ответ отправителю) {"action":"CREATE_MESSAGE","status":"SUCCESS","payload":{"id":42,"text":"Всем привет!","dateSend":"...","channelCode":"uuid","userCode":"uuid"}}
← (push остальным)    {"action":"NEW_MESSAGE","payload":{"id":42,"text":"Всем привет!","dateSend":"...","channelCode":"uuid","userCode":"uuid-отправителя"}}
```

### 3.4. Аутентификация

- После `LOGIN` / `REGISTER` клиент получает **JWT-токен** (HS256, 24 часа).
- Все последующие запросы содержат поле `token` с этим JWT.
- Сервер валидирует токен: извлекает `userCode` из `subject`.
- Если токен истёк или недействителен — ответ `ERROR`.
- **Секретный ключ генерируется при каждом запуске сервера** — все токены становятся невалидными после перезапуска.
- На фронтенде `login` и `password` хранятся **только в памяти** (для восстановления сессии после разрыва WebSocket). На диск они никогда не пишутся.

### 3.5. Push-механизм (как сервер находит нужные WebSocket'ы)

Сервер отслеживает активные WebSocket-соединения через две `ConcurrentHashMap` в `Messenger.java`:

```
userContexts:  { userCode → WsContext }     // userCode → его WebSocket-сессия
sessionToUser: { sessionId → userCode }     // обратная карта для очистки при отключении
```

Когда пользователь отправляет первый запрос с токеном (после логина), сервер регистрирует его:

```java
userContexts.put(userCode, ctx);
sessionToUser.put(ctx.sessionId, userCode);
```

При отправке сообщения push-доставка работает в 3 шага:

**Шаг 1. Запрос в БД.** `MessageController.broadcastToChannel()` делает:
```sql
SELECT * FROM user_channels WHERE channel_code = 'uuid-канала'
```
Получает список всех `userCode`, кто состоит в канале.

**Шаг 2. Обход участников.** Для каждого `userCode` (кроме отправителя) вызывается `pusher.accept(userCode, message)`.

**Шаг 3. Поиск сессии и отправка.** `pusher` (BiConsumer из `Messenger.java`) делает lookup в `userContexts`:
```java
WsContext ctx = userContexts.get(targetUserCode);
if (ctx != null && ctx.session.isOpen()) {
    ctx.send(message);
}
```

То есть сервер **не перебирает все WebSocket-соединения**, а сначала идёт в БД за списком участников канала, потом для каждого делает lookup в `HashMap` — **O(1)** на поиск сессии. Это эффективно даже для тысяч каналов.

### 3.6. Heartbeat и восстановление соединения

**Почему соединение рвалось.** Прокси-серверы, балансировщики нагрузки и корпоративные файрволы обрывают TCP-соединения, по которым долго нет данных. Типичный таймаут — 60-120 секунд. Если в канале никто не пишет, WebSocket «молчит» и через минуту-две соединение обрывается.

**Решение — Heartbeat (PING).** Клиент отправляет `{"action":"PING"}` каждые 30 секунд. Это держит соединение живым:

```javascript
Api._startHeartbeat = function() {
    setInterval(function() {
        ws.send(JSON.stringify({ action: 'PING' }));
    }, 30000);
};
```

Сервер просто игнорирует PING — ему достаточно самого факта получения данных по TCP.

**Что если соединение всё-таки оборвалось.** Сеть нестабильна, сервер перезагрузился, пользователь ушёл в лифт — WebSocket может разорваться в любой момент. Решение — **автоматический реконнект с экспоненциальной задержкой**:

```javascript
Api._tryReconnect = function() {
    // delay = 1000ms × 1.5^(attempt-1), максимум 30s
    // до 20 попыток
    setTimeout(function() {
        Api._createWebSocket(url, function() {
            Api.onReconnect();  // реавторизация
        });
    }, delay);
};
```

После восстановления WebSocket клиент автоматически реавторизуется — отправляет `LOGIN` с сохранёнными в памяти логином и паролем, получает новый JWT и продолжает работу:

```javascript
App.handleReconnect = function() {
    Api.send({ action: 'LOGIN', login, password }, function(response) {
        Api.token = response.payload.token;
        Channels.load();
        if (AppState.currentChannel) App.selectChannel(AppState.currentChannel);
    });
};
```

Логин и пароль хранятся **только в памяти** (`Api.login`, `Api.password`), никогда не пишутся на диск. Это безопасно и позволяет восстанавливать сессию без участия пользователя.

**Итог по надёжности соединения:**
1. Heartbeat каждые 30 с → соединение не протухает на прокси
2. Если разорвали → автоматические попытки переподключения (20 попыток, до 30 с между ними)
3. После восстановления → автоматическая реавторизация через `LOGIN`
4. Если не удалось за 20 попыток → показывается форма входа

---

## 4. База данных

### 4.1. Схема

```
┌──────────────────┐       ┌──────────────────┐
│      users       │       │     access       │
├──────────────────┤       ├──────────────────┤
│ code (PK)        │◄──────│ user_code (FK)   │
│ name             │ 1   1 │ id (PK)          │
│ surname          │       │ login            │
│ registration_date│       │ password (BCrypt)│
│ login            │       └──────────────────┘
└────────┬─────────┘
         │
         │ 1
         │
         │
         │ N
┌────────┴─────────┐       ┌──────────────────┐
│  user_channels   │       │    channels      │
├──────────────────┤       ├──────────────────┤
│ id (PK)          │       │ code (PK)        │
│ user_code (FK)   │       │ name             │
│ channel_code (FK)│──────►│ description      │
└────────┬─────────┘ N   1 │ creation_date    │
         │                 │ owner_code (FK)──┤──► users:code
         │ N               └──────────────────┘
         │
┌────────┴─────────┐
│    messages      │
├──────────────────┤
│ id (PK)          │
│ text             │
│ date_send        │
│ channel_id (FK)──┤──► user_channels:id
└────────┬─────────┘
         │
         │ 1
         │
         │ N
┌────────┴─────────┐
│      files       │
├──────────────────┤
│ id (PK)          │
│ file_name        │
│ stored_name      │
│ file_path        │
│ file_size        │
│ file_type        │
│ message_id (FK)──┤──► messages:id
│ upload_date      │
│ user_code (FK)───┤──► users:code
└──────────────────┘
```

### 4.2. Описание таблиц

| Таблица | Назначение | Ключевые поля |
|---|---|---|
| `users` | Профили пользователей | `code` (UUID), `name`, `surname`, `registration_date` |
| `access` | Учётные данные | `login`, `password` (BCrypt), `user_code` → users |
| `channels` | Каналы чата | `code` (UUID), `name`, `description`, `owner_code` → users |
| `user_channels` | Связь M:N | `user_code` → users, `channel_code` → channels (CASCADE) |
| `messages` | Сообщения | `text`, `date_send`, `channel_id` → user_channels (CASCADE) |
| `files` | Файлы, прикреплённые к сообщениям | `file_name`, `stored_name` (UUID), `file_path`, `file_size`, `file_type`, `message_id` → messages (CASCADE), `user_code` → users |

### 4.3. Особенности

- **`messages.channel_id` ссылается на `user_channels.id`**, а не напрямую на `channels.code`. Это позволяет определить, кто именно отправил сообщение (привязка к конкретной связи пользователь-канал). Сервер маппит `user_channels.id` → `user_code` при выдаче сообщений клиенту.
- **Каскадное удаление**: при удалении канала (`DELETE CASCADE`) автоматически удаляются все связи `user_channels` и все сообщения в этом канале.
- **Пароли хешируются BCrypt** перед сохранением в `access.password`.
- **Тестовые данные** в `init_db.sql`: 3 пользователя (Алиса, Боб, Чарли), 5 каналов, связи членства и 7 сообщений.

---

## 5. Backend (Java)

### 5.1. Стек

- **Java 17** — современная версия с pattern matching в switch
- **Javalin 7.2.2** — микро-фреймворк (HTTP + WebSocket) весом ~2 MB
- **Jackson 2.17.3** — JSON-сериализация (включая jsr310 для `LocalDateTime`)
- **JJWT 0.12.6** — генерация и валидация JWT-токенов
- **jBCrypt 0.4** — хеширование паролей
- **PostgreSQL 42.7.12** — драйвер JDBC
- **SLF4J Simple 2.0.17** — логирование
- **maven-shade-plugin** — сборка fat JAR

### 5.2. Messenger.java (точка входа)

```java
public static void main(String[] args) {
    DatabaseInit.createTablesIfNotExist();       // автосоздание таблиц
    // ...
    Javalin.create(config -> {
        config.bundledPlugins.enableCors(cors -> cors.addRule(it -> it.anyHost()));
        config.routes.ws("/websocket", ws -> {
            ws.onConnect(ctx -> { /* логирование */ });
            ws.onMessage(ctx -> {
                // парсинг JSON → роутинг по action
                // REGISTER/LOGIN → AuthController
                // *MESSAGE* → MessageController
                // иначе → ChannelController
            });
            ws.onClose(ctx -> { /* очистка userContexts */ });
            ws.onError(ctx -> { /* очистка sessionToUser */ });
        });
    }).start(7070);
}
```

**Ключевые структуры данных:**
- `Map<String, WsContext> userContexts` — `userCode` → WebSocket-контекст (для push-уведомлений)
- `Map<String, String> sessionToUser` — `sessionId` → `userCode` (для очистки при отключении)

**Push-механизм:**
```java
BiConsumer<String, String> pusher = (targetUserCode, message) -> {
    WsContext ctx = userContexts.get(targetUserCode);
    if (ctx != null && ctx.session.isOpen()) {
        ctx.send(message);
    }
};
```

### 5.3. Контроллеры

#### AuthController
- **REGISTER**: валидирует поля → проверяет уникальность логина → создаёт User (UUID) → хеширует пароль BCrypt → создаёт Access → генерирует JWT
- **LOGIN**: ищет Access по логину → проверяет пароль BCrypt → генерирует JWT
- Возвращает: `{ token, userCode, name?, surname? }`

#### ChannelController
- **CREATE_CHANNEL**: создаёт канал (UUID) + добавляет создателя как участника
- **SEARCH_CHANNELS**: поиск по имени (ILIKE) с пагинацией, только каналы пользователя
- **UPDATE_CHANNEL**: только владелец → push `CHANNEL_UPDATED` всем участникам
- **DELETE_CHANNEL**: только владелец → удаляет members + канал → push `CHANNEL_DELETED`
- **ADD_MEMBER**: добавляет участника → push `CHANNEL_ADDED` новому участнику
- **REMOVE_MEMBER**: только владелец → push `REMOVED_FROM_CHANNEL` удалённому
- **GET_CHANNEL_MEMBERS**: список всех участников с именами

#### MessageController
- **CREATE_MESSAGE**: проверяет членство → создаёт сообщение → привязывает файлы (`fileIds`) → push `NEW_MESSAGE` всем, кроме отправителя
- **UPDATE_MESSAGE**: только автор → push `MESSAGE_UPDATED`
- **DELETE_MESSAGE**: только автор → удаляет файлы с диска → удаляет запись из БД (CASCADE удаляет записи `files`) → push `MESSAGE_DELETED`
- **SEARCH_MESSAGES**: по каналу + опциональный текст (ILIKE) + пагинация, маппит `user_channels.id` → `userCode`, включает `files` для каждого сообщения

#### FilesController (REST HTTP)
Контроллер для загрузки, скачивания и удаления файлов. Работает через **HTTP**, а не WebSocket, так как бинарные данные неэффективно передавать через WebSocket-JSON.

- **POST `/api/files/upload`** — загрузка файла (multipart/form-data). Принимает поле `file` + `token`. Сохраняет файл в `./uploads/` с UUID-именем. Возвращает `{ id, fileName, fileSize, fileType }`.
- **GET `/api/files/{fileId}`** — скачивание файла. Параметр `?token=...`. Проверяет членство в канале (через message → channel). Отдаёт файл с заголовком `Content-Disposition: attachment`.
- **GET `/api/files/by-message/{messageId}`** — список файлов сообщения. Параметр `?token=...`. Возвращает массив `{ id, fileName, fileSize, fileType }`.
- **DELETE `/api/files/{fileId}`** — удаление файла (только автор). Параметр `?token=...`. Удаляет с диска и из БД.

**Поток загрузки файла:**
```
1. Клиент выбирает файлы → upload каждого через POST /api/files/upload
2. Сервер сохраняет файл на диск (./uploads/uuid_originalname.ext)
3. Сервер создаёт запись в БД files с message_id = null
4. Клиент получает fileId → отправляет CREATE_MESSAGE с fileIds: [1,2,3]
5. Сервер обновляет files SET message_id = ? WHERE id IN (1,2,3)
6. В ответе и push-событии NEW_MESSAGE появляется поле files: [{id, fileName, ...}]
```

**Удаление файлов при удалении сообщения:**
1. `MessageController.handleDeleteMessage` → запрашивает `fileDao.findByMessageId(messageId)`
2. Удаляет каждый физический файл: `Files.deleteIfExists(path)`
3. Вызывает `messageDao.delete()` — CASCADE удаляет записи из таблицы `files`

### 5.4. DAO-слой (raw JDBC)

Каждый DAO — это класс с набором методов для CRUD одной таблицы. Особенности:
- `DbConfig.getConnection()` — открывает новое JDBC-соединение при каждом вызове (без пула)
- Ручной маппинг `ResultSet` → DTO через приватный `mapRowToDto()`
- `RETURN_GENERATED_KEYS` для получения автоинкрементных ID
- `ILIKE` для регистронезависимого поиска
- `LIMIT/OFFSET` для пагинации

### 5.5. JwtService

- Алгоритм: **HS256** (HMAC-SHA256)
- Ключ: `Jwts.SIG.HS256.key().build()` — генерируется при старте (теряется после перезапуска)
- Срок: **24 часа**
- `generateToken(userCode)` → создаёт JWT с subject=userCode
- `validateToken(token)` → парсит, проверяет подпись, возвращает userCode или null

### 5.6. Права доступа

| Действие | Кто может |
|---|---|
| Просмотр каналов | Все участники канала |
| Создание сообщения | Участники канала |
| Редактирование сообщения | Только автор |
| Удаление сообщения | Только автор |
| Изменение канала | Только владелец |
| Удаление канала | Только владелец |
| Добавление участника | Любой участник (можно вызвать по userCode) |
| Удаление участника | Только владелец |
| Просмотр участников | Все участники канала |

---

## 6. Frontend (JavaScript SPA)

### 6.1. Структура и зависимости

```
index.html (точка входа)
  └── style.css
  └── api.js       (должен быть 1-м — WebSocket)
  └── utils.js     (2-м — утилиты)
  └── auth.js      (3-м — страница входа)
  └── channels.js  (4-м — каналы)
  └── chat.js      (5-м — чат)
  └── channel-info.js (6-м — инфо о канале)
  └── push-handler.js (7-м — обработка пушей)
  └── app.js       (последним — ядро)
```

Порядок загрузки критичен, так как модули используют глобальные объекты друг друга (`Api`, `Auth`, `Channels`, `Chat`, `ChannelInfo`, `PushHandler`, `App`).

### 6.2. api.js — WebSocket-слой

**Единственный модуль, который общается с сервером.** Все остальные модули вызывают `Api.send()` и получают ответ через колбэки.

**Ключевые возможности:**
- **Единое соединение**: WebSocket открывается при логине и остаётся открытым.
- **Request-Response**: каждому запросу присваивается уникальный `requestId`, колбэк регистрируется в `Api.callbacks`.
- **Push-события**: сообщения без `requestId` направляются в `Api.onPush`.
- **Автоматический JWT**: `Api.send()` сам вкладывает `Api.token` в каждый запрос.
- **Переподключение**: экспоненциальная задержка (1.5×, макс 30 с, 20 попыток).
- **Heartbeat**: PING каждые 30 секунд для предотвращения разрыва бездействующих соединений.
- **Хранение логина/пароля**: только в памяти (поле `Api.login`, `Api.password`) — для восстановления сессии при переподключении.

**Поток данных:**
```
Модуль → Api.send({action, ...}, callback)
           ↓
       Добавляется token + requestId
           ↓
       JSON.stringify → ws.send()
           ↓
       [Сервер обрабатывает]
           ↓
       ws.onmessage → JSON.parse
           ↓
       Есть requestId? → найти колбэк в callbacks → выполнить
       Нет requestId?  → onPush(response) → PushHandler.handle()
```

### 6.3. app.js — ядро SPA

**Главный диспетчер.** Содержит:
- `AppState` — глобальное состояние (текущий канал, страницы пагинации, кеш, метки непрочитанного)
- `App.showAuthPage()` — страница входа
- `App.showMainPage()` — главная страница с хедером, левой и правой панелями
- `App.selectChannel(channel)` — выбор канала (загрузка сообщений + участников)
- `App.showChatView()` / `App.showWelcome()` — переключение правой панели
- `App.logout()` — очистка localStorage + отключение WebSocket
- `App.handleReconnect()` — реавторизация после переподключения

**Маршрутизация (условная):**
```
DOMContentLoaded → showAuthPage()
                    ↓ (логин/регистрация)
                  showMainPage()
                    ↓ (выбор канала)
                  selectChannel() → showChatView()
                    ↓ (кнопка "О канале")
                  ChannelInfo.show() → currentView = 'info'
                    ↓ (кнопка "← Назад")
                  showChatView() → currentView = 'chat'
```

### 6.4. auth.js — страница входа/регистрации

- Две вкладки: **Вход** и **Регистрация**.
- При успешном входе/регистрации:
  1. Сохраняет `token`, `userCode`, `login`, `password` в `Api`
  2. Пишет токен и userCode в `localStorage`
  3. Вызывает `App.showMainPage()`

### 6.5. channels.js — левая панель (каналы)

- Отображает список каналов, в которых состоит пользователь.
- **Поиск**: `oninput` с debounce-like сбросом страницы на 1.
- **Создание**: форма скрыта по умолчанию, открывается по кнопке "+ Создать канал".
- **Пагинация**: ← 1 → (серверная, 50 каналов на страницу).
- **Метки непрочитанного**: `markUnread()` / `clearUnread()` — добавляют/убирают класс `has-unread`.
- **Защита от гонок**: `_lastChannelsReq` — если пришёл ответ от устаревшего запроса, он игнорируется.

### 6.6. chat.js — сообщения чата

- Отображает сообщения в выбранном канале.
- **Отправка**: Enter или кнопка "Отправить". Shift+Enter — новая строка.
- **Прикрепление файлов**: кнопка 📎 открывает системный диалог выбора файлов (multiple). Перед отправкой сообщения все выбранные файлы загружаются через `POST /api/files/upload`, затем `CREATE_MESSAGE` отправляется с массивом `fileIds`.
- **Отображение файлов**: под текстом сообщения выводятся ссылки на скачивание с иконкой 📎, именем файла и размером.
- **Редактирование**: ✎ → prompt → `UPDATE_MESSAGE`.
- **Удаление**: ✕ → confirm → `DELETE_MESSAGE`.
- **Пагинация**: ← 1 → (серверная, 50 сообщений на страницу).
- **Свои/чужие сообщения**: свои — справа (класс `my-message`), чужие — слева.
- **Стилизация сообщения**: имя отправителя, текст, дата, кнопки действий (только для своих).

### 6.7. channel-info.js — информация о канале

- Отображает: название, описание, дату создания, владельца, участников.
- **Участники**: пагинация **клиентская** (20 на страницу) — сервер отдаёт всех сразу.
- **Инструменты владельца** (показываются только если `currentChannel.ownerCode === Api.userCode`):
  - Редактирование названия и описания
  - Добавление участника по userCode
  - Удаление участников (✕)
  - Удаление канала (danger-зона)
- `getUserName(userCode)` — резолвит код в "Имя Фамилия" через кеш участников.

### 6.8. push-handler.js — push-события

Обрабатывает 7 типов push-событий:
- **NEW_MESSAGE**: если пользователь смотрит этот канал на 1-й странице → добавляет сообщение в DOM. Иначе — ставит метку непрочитанного.
- **MESSAGE_UPDATED**: находит сообщение по `data-msg-id` и обновляет текст.
- **MESSAGE_DELETED**: удаляет элемент из DOM.
- **CHANNEL_ADDED**: перезагружает список каналов.
- **CHANNEL_UPDATED**: обновляет название/описание на месте + перезагружает список.
- **CHANNEL_DELETED**: сбрасывает текущий канал, показывает welcome, перезагружает список.
- **REMOVED_FROM_CHANNEL**: alert + сброс канала + перезагрузка.

### 6.9. utils.js — утилиты

- `formatDate(dateStr)` — ISO → `ru-RU` locale (например, "06.07.2026, 12:00:00").
- `toggleTheme()` / `updateThemeBtn()` — переключение тёмной/светлой темы с сохранением в `localStorage`.

### 6.10. Тема оформления

- **Тёмная тема** — по умолчанию (фон `#1e1e1e`, текст `#d4d4d4`).
- **Светлая тема** — активируется классом `light-theme` на `<body>`, сохраняется в `localStorage`.
- **Адаптив**: на экранах уже 768px панели перестраиваются вертикально.

---

## 7. Установка и запуск

### Требования

- **Java 17** (или новее)
- **Maven 3.x**
- **PostgreSQL** (любая поддерживаемая версия)

### 7.1. База данных

```sql
-- Создать базу данных
CREATE DATABASE messenger_db;

-- Выполнить скрипт инициализации (создание таблиц + тестовые данные)
\i init_db.sql
```

Или просто создайте пустую БД `messenger_db` — таблицы создадутся **автоматически** при первом запуске сервера (`DatabaseInit.createTablesIfNotExist()`).

**Настройки подключения** (`src/main/java/.../dao/DbConfig.java`):
```java
String url = "jdbc:postgresql://localhost:5432/messenger_db";
String user = "postgres";
String password = "root";
```

### 7.2. Сборка и запуск сервера

```bash
# Сборка fat JAR (Messenger-1.0-SNAPSHOT.jar)
mvn clean package -DskipTests

# Запуск
java -jar target/Messenger-1.0-SNAPSHOT.jar
```

Сервер запустится на порту **7070**. WebSocket-эндпоинт: `ws://localhost:7070/websocket`

### 7.3. Фронтенд

Фронтенд — это статические файлы в папке `frontend/`. Их можно:
1. Открыть напрямую через `file://` (CORS разрешён для любого хоста) — **упрощённый вариант**.
2. Или раздать через любой статический сервер (nginx, Apache, даже `python -m http.server`).

**Важно**: адрес WebSocket зашит в `app.js` как `ws://localhost:7070/websocket`. Если сервер на другом хосте — измените `WS_URL`.

### 7.4. Тестирование

1. Запустите сервер (`java -jar target/Messenger-1.0-SNAPSHOT.jar`).
2. Откройте `frontend/index.html` в браузере.
3. Зарегистрируйтесь (логин, пароль ≥ 4 символов, имя) или войдите, если есть тестовые данные.
4. Создайте канал, добавьте участников, общайтесь.

Также доступен `test_requests.html` — HTML-инструмент для ручной отправки WebSocket-запросов.

---

## Почему такой стек?

Этот проект сделан для работы на **слабом серверном оборудовании** (например, Raspberry Pi, старый ноутбук, VPS с 512 MB RAM).

| Компонент | Почему выбран | Что даёт |
|---|---|---|
| **Java 17 + Javalin** (не Spring Boot) | Javalin весит ~2 MB и стартует за секунды. Spring Boot — ~40 MB и 5-10 секунд старта. | Меньше потребление RAM, быстрый старт |
| **Raw JDBC** (не Hibernate/JPA) | Hibernate тянет кэши, proxy, session management. JDBC — это просто SQL. | Минимум накладных расходов |
| **Vanilla JS** (не React/Vue/Angular) | Никаких node_modules, сборщиков, транспиляции. Открыл HTML — работает. | Ноль зависимостей, мгновенная загрузка |
| **WebSocket** (не REST + WS) | Единое постоянное соединение без overhead HTTP-заголовков. Push бесплатно. | Меньше трафика, real-time из коробки |

Такой подход позволяет всему приложению (сервер + БД) комфортно работать в **128-256 MB RAM**, оставляя ресурсы для самой ОС и других сервисов.
