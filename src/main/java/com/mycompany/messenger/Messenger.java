package com.mycompany.messenger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mycompany.messenger.controller.ChannelController;
import com.mycompany.messenger.controller.MessageController;
import com.mycompany.messenger.dao.DatabaseInit;
import io.javalin.Javalin;

/**
 * Главный класс сервера Messenger.
 * Запускает Javalin-сервер на порту 7070 с WebSocket-эндпоинтом /websocket.
 * <p>
 * Протокол сообщений WebSocket (JSON):
 * <pre>
 * Запрос клиента:  { "action": "CREATE_CHANNEL", "userCode": "...", "payload": { ... } }
 * Ответ сервера:  { "action": "CREATE_CHANNEL", "status": "SUCCESS", "payload": { ... } }
 *                 { "action": "CREATE_CHANNEL", "status": "ERROR", "error": "..." }
 * </pre>
 *
 * @author User
 */
public class Messenger {

    // ObjectMapper для парсинга входящих JSON-сообщений (поставляется с Javalin)
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .findAndRegisterModules();

    public static void main(String[] args) {

        // Инициализируем контроллеры
        ChannelController channelController = new ChannelController();
        MessageController messageController = new MessageController();

        // 0. Создаём таблицы БД, если их ещё нет (IF NOT EXISTS)
        try {
            DatabaseInit.createTablesIfNotExist();
        } catch (Exception e) {
            System.err.println("[DB] Ошибка при создании таблиц: " + e.getMessage());
            System.exit(1); // без таблиц работать бесполезно
        }

        // В Javalin 7 ВСЁ настраивается внутри create()
        Javalin app = Javalin.create(config -> {

            // 1. Настройка CORS — разрешаем запросы с любых хостов
            config.bundledPlugins.enableCors(cors -> {
                cors.addRule(it -> it.anyHost());
            });

            // 2. Настройка WebSocket-эндпоинта
            config.routes.ws("/websocket", ws -> {

                // ----------------------------------------------------------------
                // onConnect — вызывается при новом подключении клиента
                // ----------------------------------------------------------------
                ws.onConnect(ctx -> {
                    System.out.println("[WS] Клиент подключился: " + ctx.sessionId());
                });

                // ----------------------------------------------------------------
                // onMessage — вызывается при получении сообщения от клиента
                // Ожидаем JSON вида: { "action": "...", "userCode": "...", "payload": {...} }
                // ----------------------------------------------------------------
                ws.onMessage(ctx -> {
                    String rawMessage = ctx.message();
                    System.out.println("[WS] Получено от " + ctx.sessionId() + ": " + rawMessage);

                    try {
                        // Парсим входящее JSON-сообщение
                        ObjectNode json = (ObjectNode) MAPPER.readTree(rawMessage);

                        // Извлекаем обязательные поля
                        String action = json.has("action") ? json.get("action").asText() : "";
                        String userCode = json.has("userCode") ? json.get("userCode").asText() : "";

                        // Извлекаем payload (тело запроса) — может отсутствовать
                        ObjectNode payload = json.has("payload") && json.get("payload").isObject()
                                ? (ObjectNode) json.get("payload")
                                : MAPPER.createObjectNode();

                        // Валидируем наличие userCode
                        // (в будущем userCode будет извлекаться из JWT-токена)
                        if (userCode.isEmpty()) {
                            ctx.send("{\"action\":\"" + action
                                    + "\",\"status\":\"ERROR\",\"error\":\"Не указан userCode\"}");
                            return;
                        }

                        // Маршрутизируем запрос в нужный контроллер
                        // Действия с сообщениями содержат "MESSAGE" в названии
                        String response;
                        if (action.contains("MESSAGE")) {
                            response = messageController.handleAction(action, userCode, payload);
                        } else {
                            response = channelController.handleAction(action, userCode, payload);
                        }
                        ctx.send(response);

                    } catch (Exception e) {
                        // Если JSON не распарсился или произошла другая ошибка
                        System.err.println("[WS] Ошибка обработки сообщения: " + e.getMessage());
                        ctx.send("{\"action\":\"UNKNOWN\",\"status\":\"ERROR\",\"error\":\""
                                + "Ошибка обработки запроса: " + e.getMessage().replace("\"", "'")
                                + "\"}");
                    }
                });

                // ----------------------------------------------------------------
                // onClose — вызывается при отключении клиента
                // ----------------------------------------------------------------
                ws.onClose(ctx -> {
                    System.out.println("[WS] Клиент отключился: " + ctx.sessionId());
                });

                // ----------------------------------------------------------------
                // onError — вызывается при ошибке в сессии
                // ----------------------------------------------------------------
                ws.onError(ctx -> {
                    System.err.println("[WS] Ошибка сессии: " + ctx.sessionId());
                });
            });
        }).start(7070); // Запускаем сервер на порту 7070
    }
}
