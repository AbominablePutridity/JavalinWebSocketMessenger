package com.mycompany.messenger.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mycompany.messenger.dao.ChannelDao;
import com.mycompany.messenger.dao.UserChannelDao;
import com.mycompany.messenger.dto.ChannelDto;
import com.mycompany.messenger.dto.UserChannelDto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Контроллер для работы с каналами через WebSocket.
 * <p>
 * Каждый метод принимает userCode (временная идентификация — в будущем будет
 * заменена на JWT-токен) и JSON-узел с параметрами запроса (payload).
 * Возвращает JSON-строку с результатом операции.
 * <p>
 * Формат ответа (успех):
 * <pre>
 * { "action": "CREATE_CHANNEL", "status": "SUCCESS", "payload": { ... } }
 * </pre>
 * Формат ответа (ошибка):
 * <pre>
 * { "action": "CREATE_CHANNEL", "status": "ERROR", "error": "Сообщение об ошибке" }
 * </pre>
 */
public class ChannelController {

    // ObjectMapper для сериализации/десериализации JSON (поставляется с Javalin)
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .findAndRegisterModules(); // регистрируем модули (JSR310 для дат)

    private final ChannelDao channelDao;
    private final UserChannelDao userChannelDao;

    /**
     * Конструктор — инициализируем DAO для работы с БД.
     */
    public ChannelController() {
        this.channelDao = new ChannelDao();
        this.userChannelDao = new UserChannelDao();
    }

    // ========================================================================
    // ПУБЛИЧНЫЙ МЕТОД-МАРШРУТИЗАТОР
    // Определяет, какой метод вызвать на основе переданного action
    // ========================================================================

    /**
     * Центральный метод-диспетчер. Вызывается из WebSocket-обработчика.
     *
     * @param action   тип операции (CREATE_CHANNEL, SEARCH_CHANNELS, DELETE_CHANNEL, UPDATE_CHANNEL)
     * @param userCode код пользователя, отправившего запрос
     * @param payload  JSON-объект с параметрами запроса
     * @return JSON-строка с результатом
     */
    public String handleAction(String action, String userCode, ObjectNode payload) {
        return switch (action != null ? action.toUpperCase() : "") {
            case "CREATE_CHANNEL" -> handleCreateChannel(userCode, payload);
            case "SEARCH_CHANNELS" -> handleSearchChannels(userCode, payload);
            case "DELETE_CHANNEL" -> handleDeleteChannel(userCode, payload);
            case "UPDATE_CHANNEL" -> handleUpdateChannel(userCode, payload);
            case "ADD_MEMBER" -> handleAddMember(userCode, payload);
            default -> buildError(action, "Неизвестный тип действия: " + action);
        };
    }

    // ========================================================================
    // 1. CREATE_CHANNEL — Создание канала
    // Клиент присылает: { "name": "...", "description": "..." }
    // Сервер создаёт канал и добавляет создателя как участника
    // ========================================================================

    /**
     * Обрабатывает создание нового канала.
     * <p>
     * Логика:
     * <ol>
     *   <li>Валидируем название канала (не пустое)</li>
     *   <li>Генерируем уникальный UUID для code канала</li>
     *   <li>Создаём запись в таблице channels (с owner_code = userCode)</li>
     *   <li>Создаём запись в таблице user_channels (связь пользователя с каналом)</li>
     * </ol>
     *
     * @param userCode код создателя канала
     * @param payload  JSON с полями name (обязательно), description (опционально)
     * @return JSON-строка с результатом
     */
    private String handleCreateChannel(String userCode, ObjectNode payload) {
        try {
            // Извлекаем и валидируем название канала
            String name = payload.has("name") ? payload.get("name").asText().trim() : "";
            if (name.isEmpty()) {
                return buildError("CREATE_CHANNEL", "Название канала не может быть пустым");
            }

            // Извлекаем описание (опционально)
            String description = payload.has("description") ? payload.get("description").asText().trim() : "";

            // Генерируем уникальный идентификатор канала
            String channelCode = UUID.randomUUID().toString();

            // Собираем DTO канала
            ChannelDto channel = new ChannelDto();
            channel.setCode(channelCode);
            channel.setName(name);
            channel.setDescription(description);
            channel.setCreationDate(LocalDateTime.now());
            channel.setOwnerCode(userCode);

            // 1) Сохраняем канал в БД
            channelDao.save(channel);

            // 2) Добавляем создателя как участника канала (связь в user_channels)
            UserChannelDto userChannel = new UserChannelDto();
            userChannel.setUserCode(userCode);
            userChannel.setChannelCode(channelCode);
            userChannelDao.save(userChannel);

            // Формируем успешный ответ с данными созданного канала
            ObjectNode payloadResponse = MAPPER.createObjectNode();
            payloadResponse.put("code", channelCode);
            payloadResponse.put("name", name);
            payloadResponse.put("description", description);
            payloadResponse.put("creationDate", channel.getCreationDate().toString());

            return buildSuccess("CREATE_CHANNEL", payloadResponse);

        } catch (Exception e) {
            return buildError("CREATE_CHANNEL", "Ошибка при создании канала: " + e.getMessage());
        }
    }

    // ========================================================================
    // 2. SEARCH_CHANNELS — Поиск каналов
    // Клиент присылает: { "name": "..." } (name может быть пустой строкой)
    // Если name пустой — возвращаются ВСЕ каналы, где состоит пользователь
    // Если name непустой — поиск по частичному совпадению имени (ILIKE)
    // ========================================================================

    /**
     * Обрабатывает поиск каналов по имени.
     * <p>
     * Поиск происходит ТОЛЬКО среди каналов, в которых состоит пользователь
     * (через JOIN с таблицей user_channels).
     *
     * @param userCode код пользователя, выполняющего поиск
     * @param payload  JSON с полем name (строка для поиска)
     * @return JSON-строка с массивом найденных каналов
     */
    private String handleSearchChannels(String userCode, ObjectNode payload) {
        try {
            // Извлекаем строку поиска (может быть пустой — тогда ищем все каналы пользователя)
            String searchName = payload.has("name") ? payload.get("name").asText() : "";

            // Выполняем поиск через DAO
            List<ChannelDto> channels = channelDao.findByUserCodeAndName(userCode, searchName);

            // Формируем JSON-массив с результатами
            ArrayNode channelsArray = MAPPER.createArrayNode();

            for (ChannelDto channel : channels) {
                ObjectNode channelNode = MAPPER.createObjectNode();
                channelNode.put("code", channel.getCode());
                channelNode.put("name", channel.getName());
                channelNode.put("description", channel.getDescription() != null ? channel.getDescription() : "");
                channelNode.put("creationDate", channel.getCreationDate() != null
                        ? channel.getCreationDate().toString() : "");
                channelsArray.add(channelNode);
            }

            // Оборачиваем массив в объект с полем "channels"
            ObjectNode payloadResponse = MAPPER.createObjectNode();
            payloadResponse.set("channels", channelsArray);
            payloadResponse.put("total", channels.size());

            return buildSuccess("SEARCH_CHANNELS", payloadResponse);

        } catch (Exception e) {
            return buildError("SEARCH_CHANNELS", "Ошибка при поиске каналов: " + e.getMessage());
        }
    }

    // ========================================================================
    // 3. DELETE_CHANNEL — Удаление канала
    // Клиент присылает: { "code": "..." }
    // Удаление возможно ТОЛЬКО если пользователь — владелец канала
    // ========================================================================

    /**
     * Обрабатывает удаление канала.
     * <p>
     * Проверка: канал удаляется только если текущий пользователь является
     * его владельцем (owner_code совпадает с userCode).
     * Если канал не принадлежит пользователю — возвращается ошибка.
     *
     * @param userCode код пользователя, запросившего удаление
     * @param payload  JSON с полем code (код удаляемого канала)
     * @return JSON-строка с результатом
     */
    private String handleDeleteChannel(String userCode, ObjectNode payload) {
        try {
            // Извлекаем код канала
            if (!payload.has("code") || payload.get("code").asText().isEmpty()) {
                return buildError("DELETE_CHANNEL", "Не указан код канала для удаления");
            }
            String channelCode = payload.get("code").asText();

            // Шаг 1: удаляем все сообщения в этом канале (через связи user_channels)
            // Если таблицы созданы через DatabaseInit — CASCADE сделает это автоматически,
            // но для совместимости с уже существующими БД чистим вручную
            userChannelDao.deleteByChannelCode(channelCode);

            // Шаг 2: удаляем сам канал (DAO проверяет owner_code)
            boolean deleted = channelDao.delete(channelCode, userCode);

            if (deleted) {
                // Если удаление успешно — канал принадлежал пользователю
                ObjectNode payloadResponse = MAPPER.createObjectNode();
                payloadResponse.put("message", "Канал успешно удалён");
                payloadResponse.put("code", channelCode);
                return buildSuccess("DELETE_CHANNEL", payloadResponse);
            } else {
                // Если ни одна строка не была затронута — канал не принадлежит пользователю
                return buildError("DELETE_CHANNEL",
                        "Канал не найден или вы не являетесь его владельцем");
            }

        } catch (Exception e) {
            return buildError("DELETE_CHANNEL", "Ошибка при удалении канала: " + e.getMessage());
        }
    }

    // ========================================================================
    // 4. UPDATE_CHANNEL — Обновление канала
    // Клиент присылает: { "code": "...", "name": "...", "description": "..." }
    // Обновление возможно ТОЛЬКО если пользователь — владелец канала
    // ========================================================================

    /**
     * Обрабатывает обновление данных канала.
     * <p>
     * Обновляются только название (name) и описание (description).
     * Проверка: обновление происходит только если текущий пользователь
     * является владельцем канала (owner_code совпадает с userCode).
     *
     * @param userCode код пользователя, запросившего обновление
     * @param payload  JSON с полями code (обязательно), name, description
     * @return JSON-строка с результатом
     */
    private String handleUpdateChannel(String userCode, ObjectNode payload) {
        try {
            // Извлекаем код обновляемого канала
            if (!payload.has("code") || payload.get("code").asText().isEmpty()) {
                return buildError("UPDATE_CHANNEL", "Не указан код канала для обновления");
            }
            String channelCode = payload.get("code").asText();

            // Проверяем, что хотя бы одно поле для обновления передано
            if (!payload.has("name") && !payload.has("description")) {
                return buildError("UPDATE_CHANNEL",
                        "Не переданы данные для обновления (name, description)");
            }

            // Получаем текущие данные канала из БД (чтобы не затереть то, что не обновляем)
            ChannelDto existingChannel = channelDao.findByCode(channelCode);
            if (existingChannel == null) {
                return buildError("UPDATE_CHANNEL", "Канал с таким кодом не найден");
            }

            // Обновляем только те поля, которые передал клиент
            String newName = payload.has("name") ? payload.get("name").asText().trim() : existingChannel.getName();
            String newDescription = payload.has("description")
                    ? payload.get("description").asText().trim()
                    : existingChannel.getDescription();

            // Собираем DTO для обновления (включаем owner_code для проверки владельца)
            ChannelDto channel = new ChannelDto();
            channel.setCode(channelCode);
            channel.setName(newName);
            channel.setDescription(newDescription);
            channel.setOwnerCode(userCode); // для проверки в WHERE owner_code = ?

            // Пытаемся обновить (DAO вернёт false, если owner_code не совпадает)
            boolean updated = channelDao.update(channel);

            if (updated) {
                // Обновление успешно
                ObjectNode payloadResponse = MAPPER.createObjectNode();
                payloadResponse.put("code", channelCode);
                payloadResponse.put("name", newName);
                payloadResponse.put("description", newDescription);
                return buildSuccess("UPDATE_CHANNEL", payloadResponse);
            } else {
                // Ни одна строка не обновлена — канал не принадлежит пользователю
                return buildError("UPDATE_CHANNEL",
                        "Канал не найден или вы не являетесь его владельцем");
            }

        } catch (Exception e) {
            return buildError("UPDATE_CHANNEL", "Ошибка при обновлении канала: " + e.getMessage());
        }
    }

    // ========================================================================
    // 5. ADD_MEMBER — Добавление участника в канал
    // Клиент присылает: { "channelCode": "...", "memberCode": "..." }
    // Создаёт связь в user_channels, если её ещё нет
    // ========================================================================

    /**
     * Добавляет пользователя (memberCode) в указанный канал (channelCode).
     * Используется для тестирования — чтобы можно было добавить участника
     * в канал и проверить поиск каналов для этого пользователя.
     *
     * @param userCode   код текущего пользователя (проверка не требуется)
     * @param payload    JSON с полями channelCode и memberCode
     * @return JSON-строка с результатом
     */
    private String handleAddMember(String userCode, ObjectNode payload) {
        try {
            // Извлекаем код канала
            if (!payload.has("channelCode") || payload.get("channelCode").asText().isEmpty()) {
                return buildError("ADD_MEMBER", "Не указан код канала (channelCode)");
            }
            String channelCode = payload.get("channelCode").asText();

            // Извлекаем код добавляемого пользователя
            if (!payload.has("memberCode") || payload.get("memberCode").asText().isEmpty()) {
                return buildError("ADD_MEMBER", "Не указан код пользователя (memberCode)");
            }
            String memberCode = payload.get("memberCode").asText();

            // Проверяем, что канал существует
            ChannelDto channel = channelDao.findByCode(channelCode);
            if (channel == null) {
                return buildError("ADD_MEMBER", "Канал с таким кодом не найден");
            }

            // Проверяем, не состоит ли пользователь уже в канале
            UserChannelDto existing = userChannelDao.findByUserCodeAndChannelCode(memberCode, channelCode);
            if (existing != null) {
                return buildError("ADD_MEMBER", "Пользователь уже состоит в этом канале");
            }

            // Создаём связь
            UserChannelDto userChannel = new UserChannelDto();
            userChannel.setUserCode(memberCode);
            userChannel.setChannelCode(channelCode);
            userChannelDao.save(userChannel);

            // Формируем ответ
            ObjectNode payloadResponse = MAPPER.createObjectNode();
            payloadResponse.put("message", "Пользователь добавлен в канал");
            payloadResponse.put("channelCode", channelCode);
            payloadResponse.put("memberCode", memberCode);

            return buildSuccess("ADD_MEMBER", payloadResponse);

        } catch (Exception e) {
            return buildError("ADD_MEMBER", "Ошибка при добавлении участника: " + e.getMessage());
        }
    }

    // ========================================================================
    // ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ для формирования JSON-ответов
    // ========================================================================

    /**
     * Формирует JSON-строку успешного ответа.
     *
     * @param action  тип выполненной операции
     * @param payload данные для включения в ответ
     * @return JSON-строка вида { "action": "...", "status": "SUCCESS", "payload": {...} }
     */
    private String buildSuccess(String action, ObjectNode payload) {
        ObjectNode response = MAPPER.createObjectNode();
        response.put("action", action);
        response.put("status", "SUCCESS");
        response.set("payload", payload);
        return response.toString();
    }

    /**
     * Формирует JSON-строку ответа с ошибкой.
     *
     * @param action    тип операции, в которой произошла ошибка
     * @param errorText описание ошибки
     * @return JSON-строка вида { "action": "...", "status": "ERROR", "error": "..." }
     */
    private String buildError(String action, String errorText) {
        ObjectNode response = MAPPER.createObjectNode();
        response.put("action", action);
        response.put("status", "ERROR");
        response.put("error", errorText);
        return response.toString();
    }
}
