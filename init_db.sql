-- ============================================================
-- SQL-скрипт инициализации базы данных messenger_db
-- Запускать строго по порядку: сначала таблицы, затем данные
-- ============================================================

-- Удаляем таблицы если уже существуют (чтобы пересоздать с нуля)
DROP TABLE IF EXISTS messages CASCADE;
DROP TABLE IF EXISTS user_channels CASCADE;
DROP TABLE IF EXISTS access CASCADE;
DROP TABLE IF EXISTS channels CASCADE;
DROP TABLE IF EXISTS users CASCADE;

-- ============================================================
-- 1. Создание таблиц
-- ============================================================

-- Пользователи
CREATE TABLE users (
    code              VARCHAR(255) PRIMARY KEY,        -- уникальный код пользователя (UUID)
    name              VARCHAR(255) NOT NULL,            -- имя
    surname           VARCHAR(255) NOT NULL,            -- фамилия
    registration_date TIMESTAMP NOT NULL,               -- дата регистрации
    login             VARCHAR(255)                      -- логин (для будущей авторизации)
);

-- Учётные данные для входа
CREATE TABLE access (
    id              BIGSERIAL PRIMARY KEY,              -- автоинкрементный ID
    login           VARCHAR(255) NOT NULL,              -- логин
    password        VARCHAR(255) NOT NULL,              -- пароль
    user_code       VARCHAR(255) REFERENCES users(code) -- ссылка на пользователя
);

-- Каналы
CREATE TABLE channels (
    code            VARCHAR(255) PRIMARY KEY,           -- уникальный код канала (UUID)
    name            VARCHAR(255) NOT NULL,               -- название канала
    description     TEXT,                                -- описание канала
    creation_date   TIMESTAMP NOT NULL,                  -- дата создания
    owner_code      VARCHAR(255) NOT NULL REFERENCES users(code) -- кто создал канал (владелец)
);

-- Связь пользователей с каналами (многие ко многим)
CREATE TABLE user_channels (
    id              BIGSERIAL PRIMARY KEY,              -- автоинкрементный ID
    user_code       VARCHAR(255) NOT NULL REFERENCES users(code),    -- пользователь
    channel_code    VARCHAR(255) NOT NULL REFERENCES channels(code)  -- канал
);

-- Сообщения
CREATE TABLE messages (
    id              BIGSERIAL PRIMARY KEY,              -- автоинкрементный ID
    text            TEXT NOT NULL,                       -- текст сообщения
    date_send       TIMESTAMP NOT NULL,                  -- дата отправки
    channel_id      BIGINT NOT NULL REFERENCES user_channels(id) -- ссылка на связь пользователь-канал
);

-- ============================================================
-- 2. Заполнение тестовыми данными
-- ============================================================

-- Пользователи
INSERT INTO users (code, name, surname, registration_date, login) VALUES
('user-alice', 'Алиса', 'Иванова', '2024-01-15 10:00:00', 'alice_login'),
('user-bob',   'Боб',   'Петров',  '2024-02-20 14:30:00', 'bob_login'),
('user-charlie', 'Чарли', 'Сидоров', '2024-03-10 09:15:00', 'charlie_login');

-- Учётные данные
INSERT INTO access (login, password, user_code) VALUES
('alice_login', 'pass123', 'user-alice'),
('bob_login',   'pass456', 'user-bob'),
('charlie_login', 'pass789', 'user-charlie');

-- Каналы (owner_code указывает, кто создал канал)
INSERT INTO channels (code, name, description, creation_date, owner_code) VALUES
('channel-friends',  'Друзья',        'Канал для общения с друзьями',         '2024-01-20 12:00:00', 'user-alice'),
('channel-work',     'Рабочие вопросы', 'Обсуждение рабочих проектов',        '2024-02-25 10:00:00', 'user-bob'),
('channel-games',    'Игры',          'Обсуждаем новые игры',                 '2024-03-15 18:00:00', 'user-alice'),
('channel-music',    'Музыка',        'Делимся любимыми треками',             '2024-04-01 20:00:00', 'user-charlie'),
('channel-tech',     'Технологии',    'Новости из мира IT',                   '2024-04-10 08:00:00', 'user-bob');

-- Связи пользователей с каналами (кто в каких каналах состоит)
INSERT INTO user_channels (user_code, channel_code) VALUES
-- Алиса состоит в каналах: Друзья, Игры, Музыка
('user-alice',   'channel-friends'),
('user-alice',   'channel-games'),
('user-alice',   'channel-music'),
-- Боб состоит в каналах: Рабочие вопросы, Технологии, Друзья
('user-bob',     'channel-work'),
('user-bob',     'channel-tech'),
('user-bob',     'channel-friends'),
-- Чарли состоит в каналах: Музыка, Технологии, Игры
('user-charlie', 'channel-music'),
('user-charlie', 'channel-tech'),
('user-charlie', 'channel-games');

-- Сообщения
INSERT INTO messages (text, date_send, channel_id) VALUES
-- Сообщения от Алисы в канале "Друзья" (user_channel id = 1)
('Всем привет!',                        '2024-06-01 10:00:00', 1),
('Как дела?',                           '2024-06-01 10:05:00', 1),
-- Сообщения от Боба в канале "Рабочие вопросы" (user_channel id = 4)
('Нужно обсудить новый проект',         '2024-06-02 09:00:00', 4),
('Созвонимся завтра?',                  '2024-06-02 09:30:00', 4),
-- Сообщения от Чарли в канале "Музыка" (user_channel id = 7)
('Послушайте новый трек!',              '2024-06-03 15:00:00', 7),
('Классная песня',                      '2024-06-03 15:10:00', 7),
-- Сообщение от Боба в канале "Друзья" (user_channel id = 6)
('Алиса, привет! Как твои дела?',       '2024-06-04 18:00:00', 6);
