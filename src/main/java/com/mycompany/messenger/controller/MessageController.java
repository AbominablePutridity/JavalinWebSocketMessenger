package com.mycompany.messenger.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mycompany.messenger.dao.MessageDao;
import com.mycompany.messenger.dao.UserChannelDao;
import com.mycompany.messenger.dto.MessageDto;
import com.mycompany.messenger.dto.UserChannelDto;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Контроллер для работы с сообщениями через WebSocket.
 * <p>
 * Автор сообщения определяется через связь user_channels:
 * messages.channel_id → user_channels.id, user_channels.user_code — это автор.
 * <p>
 * Формат ответа (успех):
 * <pre>{ "action": "...", "status": "SUCCESS", "payload": { ... } }</pre>
 * Формат ответа (ошибка):
 * <pre>{ "action": "...", "status": "ERROR", "error": "..." }</pre>
 */
public class MessageController {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .findAndRegisterModules();

    private final MessageDao messageDao;
    private final UserChannelDao userChannelDao;

    public MessageController() {
        this.messageDao = new MessageDao();
        this.userChannelDao = new UserChannelDao();
    }

    // ========================================================================
    // ДИСПЕТЧЕР
    // ========================================================================

    /**
     * Центральный метод-диспетчер. Вызывается из WebSocket-обработчика.
     *
     * @param action   тип операции (CREATE_MESSAGE, UPDATE_MESSAGE, DELETE_MESSAGE, SEARCH_MESSAGES)
     * @param userCode код пользователя, отправившего запрос
     * @param payload  JSON-объект с параметрами запроса
     * @return JSON-строка с результатом
     */
    public String handleAction(String action, String userCode, ObjectNode payload) {
        return switch (action != null ? action.toUpperCase() : "") {
            case "CREATE_MESSAGE" -> handleCreateMessage(userCode, payload);
            case "UPDATE_MESSAGE" -> handleUpdateMessage(userCode, payload);
            case "DELETE_MESSAGE" -> handleDeleteMessage(userCode, payload);
            case "SEARCH_MESSAGES" -> handleSearchMessages(userCode, payload);
            default -> buildError(action, "Неизвестный тип действия: " + action);
        };
    }

    // ========================================================================
    // 1. CREATE_MESSAGE — Отправка сообщения в канал
    // Клиент присылает: { "channelCode": "...", "text": "..." }
    // Сервер находит user_channel (пользователь + канал),
    // создаёт сообщение с channel_id = user_channel.id
    // ========================================================================

    /**
     * Отправляет сообщение в канал. Пользователь должен быть участником канала.
     * <p>
     * Логика:
     * <ol>
     *   <li>Находим user_channel по userCode + channelCode</li>
     *   <li>Если связи нет — пользователь не участник, ошибка</li>
     *   <li>Создаём сообщение с channel_id = user_channel.id</li>
     * </ol>
     */
    private String handleCreateMessage(String userCode, ObjectNode payload) {
        try {
            // Извлекаем код канала
            if (!payload.has("channelCode") || payload.get("channelCode").asText().isEmpty()) {
                return buildError("CREATE_MESSAGE", "Не указан код канала (channelCode)");
            }
            String channelCode = payload.get("channelCode").asText();

            // Извлекаем текст сообщения
            if (!payload.has("text") || payload.get("text").asText().trim().isEmpty()) {
                return buildError("CREATE_MESSAGE", "Текст сообщения не может быть пустым");
            }
            String text = payload.get("text").asText().trim();

            // Проверяем, является ли пользователь участником канала
            UserChannelDto userChannel = userChannelDao.findByUserCodeAndChannelCode(userCode, channelCode);
            if (userChannel == null) {
                return buildError("CREATE_MESSAGE", "Вы не являетесь участником этого канала");
            }

            // Создаём сообщение
            MessageDto message = new MessageDto();
            message.setText(text);
            message.setDateSend(LocalDateTime.now());
            message.setChannelId(userChannel.getId()); // ссылка на user_channel

            messageDao.save(message);

            // Формируем ответ
            ObjectNode payloadResponse = MAPPER.createObjectNode();
            payloadResponse.put("id", message.getId());
            payloadResponse.put("text", message.getText());
            payloadResponse.put("dateSend", message.getDateSend().toString());
            payloadResponse.put("channelCode", channelCode);
            payloadResponse.put("userCode", userCode); // кто написал

            return buildSuccess("CREATE_MESSAGE", payloadResponse);

        } catch (Exception e) {
            return buildError("CREATE_MESSAGE", "Ошибка при отправке сообщения: " + e.getMessage());
        }
    }

    // ========================================================================
    // 2. UPDATE_MESSAGE — Редактирование сообщения
    // Клиент присылает: { "messageId": 123, "text": "новый текст" }
    // Редактировать можно ТОЛЬКО своё сообщение
    // ========================================================================

    /**
     * Редактирует текст сообщения. Только автор может редактировать.
     * <p>
     * Проверка автора: messages.channel_id ∈ user_channels WHERE user_code = ?
     * Выполняется на уровне SQL в MessageDao.update().
     */
    private String handleUpdateMessage(String userCode, ObjectNode payload) {
        try {
            // Извлекаем ID сообщения
            if (!payload.has("messageId")) {
                return buildError("UPDATE_MESSAGE", "Не указан ID сообщения (messageId)");
            }
            long messageId = payload.get("messageId").asLong();

            // Извлекаем новый текст
            if (!payload.has("text") || payload.get("text").asText().trim().isEmpty()) {
                return buildError("UPDATE_MESSAGE", "Текст сообщения не может быть пустым");
            }
            String newText = payload.get("text").asText().trim();

            // Пытаемся обновить (DAO проверяет авторство через подзапрос)
            boolean updated = messageDao.update(messageId, newText, userCode);

            if (updated) {
                ObjectNode payloadResponse = MAPPER.createObjectNode();
                payloadResponse.put("id", messageId);
                payloadResponse.put("text", newText);
                return buildSuccess("UPDATE_MESSAGE", payloadResponse);
            } else {
                return buildError("UPDATE_MESSAGE", "Сообщение не найдено или вы не являетесь его автором");
            }

        } catch (Exception e) {
            return buildError("UPDATE_MESSAGE", "Ошибка при редактировании сообщения: " + e.getMessage());
        }
    }

    // ========================================================================
    // 3. DELETE_MESSAGE — Удаление сообщения
    // Клиент присылает: { "messageId": 123 }
    // Удалить можно ТОЛЬКО своё сообщение
    // ========================================================================

    /**
     * Удаляет сообщение. Только автор может удалить.
     * Проверка автора на уровне SQL в MessageDao.delete().
     */
    private String handleDeleteMessage(String userCode, ObjectNode payload) {
        try {
            // Извлекаем ID сообщения
            if (!payload.has("messageId")) {
                return buildError("DELETE_MESSAGE", "Не указан ID сообщения (messageId)");
            }
            long messageId = payload.get("messageId").asLong();

            // Пытаемся удалить (DAO проверяет авторство через подзапрос)
            boolean deleted = messageDao.delete(messageId, userCode);

            if (deleted) {
                ObjectNode payloadResponse = MAPPER.createObjectNode();
                payloadResponse.put("message", "Сообщение успешно удалено");
                payloadResponse.put("id", messageId);
                return buildSuccess("DELETE_MESSAGE", payloadResponse);
            } else {
                return buildError("DELETE_MESSAGE", "Сообщение не найдено или вы не являетесь его автором");
            }

        } catch (Exception e) {
            return buildError("DELETE_MESSAGE", "Ошибка при удалении сообщения: " + e.getMessage());
        }
    }

    // ========================================================================
    // 4. SEARCH_MESSAGES — Поиск сообщений в канале
    // Клиент присылает: { "channelCode": "...", "text": "...", "page": 1, "size": 20 }
    // Если text пустой — все сообщения канала
    // Если text непустой — ILIKE по содержимому
    // В ответе также передаётся userCode отправителя каждого сообщения
    // ========================================================================

    /**
     * Ищет сообщения в указанном канале с пагинацией.
     * <p>
     * Для каждого сообщения определяется автор через user_channels.
     * Результаты сортируются по дате отправки (сначала новые).
     */
    private String handleSearchMessages(String userCode, ObjectNode payload) {
        try {
            // Извлекаем код канала
            if (!payload.has("channelCode") || payload.get("channelCode").asText().isEmpty()) {
                return buildError("SEARCH_MESSAGES", "Не указан код канала (channelCode)");
            }
            String channelCode = payload.get("channelCode").asText();

            // Извлекаем фильтр по тексту (опционально)
            String searchText = payload.has("text") ? payload.get("text").asText() : "";

            // Извлекаем параметры пагинации (по умолчанию страница 1, размер 20)
            int page = payload.has("page") ? payload.get("page").asInt(1) : 1;
            int size = payload.has("size") ? payload.get("size").asInt(20) : 20;

            if (page < 1) page = 1;
            if (size < 1) size = 20;
            if (size > 100) size = 100; // ограничение макс. размера

            // 1. Ищем сообщения
            List<MessageDto> messages = messageDao.findByChannelCode(channelCode, searchText, page, size);

            // 2. Строим маппинг user_channel.id → userCode для этого канала
            //    (чтобы для каждого сообщения узнать автора без N+1 запросов)
            Map<Long, String> userChannelToUserCode = new HashMap<>();
            List<UserChannelDto> userChannels = userChannelDao.findAllByChannelCode(channelCode);
            for (UserChannelDto uc : userChannels) {
                userChannelToUserCode.put(uc.getId(), uc.getUserCode());
            }

            // 3. Формируем JSON-ответ
            ArrayNode messagesArray = MAPPER.createArrayNode();

            for (MessageDto msg : messages) {
                ObjectNode msgNode = MAPPER.createObjectNode();
                msgNode.put("id", msg.getId());
                msgNode.put("text", msg.getText());
                msgNode.put("dateSend", msg.getDateSend() != null ? msg.getDateSend().toString() : "");
                msgNode.put("channelCode", channelCode);
                // Определяем автора сообщения через маппинг
                String authorCode = userChannelToUserCode.get(msg.getChannelId());
                msgNode.put("userCode", authorCode != null ? authorCode : "unknown");
                messagesArray.add(msgNode);
            }

            ObjectNode payloadResponse = MAPPER.createObjectNode();
            payloadResponse.set("messages", messagesArray);
            payloadResponse.put("page", page);
            payloadResponse.put("size", size);
            payloadResponse.put("total", messagesArray.size());

            return buildSuccess("SEARCH_MESSAGES", payloadResponse);

        } catch (Exception e) {
            return buildError("SEARCH_MESSAGES", "Ошибка при поиске сообщений: " + e.getMessage());
        }
    }

    // ========================================================================
    // ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ
    // ========================================================================

    private String buildSuccess(String action, ObjectNode payload) {
        ObjectNode response = MAPPER.createObjectNode();
        response.put("action", action);
        response.put("status", "SUCCESS");
        response.set("payload", payload);
        return response.toString();
    }

    private String buildError(String action, String errorText) {
        ObjectNode response = MAPPER.createObjectNode();
        response.put("action", action);
        response.put("status", "ERROR");
        response.put("error", errorText);
        return response.toString();
    }
}
