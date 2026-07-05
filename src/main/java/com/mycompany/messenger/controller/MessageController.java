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

public class MessageController {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    private final MessageDao messageDao;
    private final UserChannelDao userChannelDao;

    public MessageController() {
        this.messageDao = new MessageDao();
        this.userChannelDao = new UserChannelDao();
    }

    public String handleAction(String action, String userCode, ObjectNode payload) {
        return switch (action != null ? action.toUpperCase() : "") {
            case "CREATE_MESSAGE" -> handleCreateMessage(userCode, payload);
            case "UPDATE_MESSAGE" -> handleUpdateMessage(userCode, payload);
            case "DELETE_MESSAGE" -> handleDeleteMessage(userCode, payload);
            case "SEARCH_MESSAGES" -> handleSearchMessages(userCode, payload);
            default -> buildError(action, "Неизвестный тип действия: " + action);
        };
    }

    private String handleCreateMessage(String userCode, ObjectNode payload) {
        try {
            if (!payload.has("channelCode") || payload.get("channelCode").asText().isEmpty())
                return buildError("CREATE_MESSAGE", "Не указан код канала (channelCode)");
            String channelCode = payload.get("channelCode").asText();

            if (!payload.has("text") || payload.get("text").asText().trim().isEmpty())
                return buildError("CREATE_MESSAGE", "Текст сообщения не может быть пустым");
            String text = payload.get("text").asText().trim();

            UserChannelDto userChannel = userChannelDao.findByUserCodeAndChannelCode(userCode, channelCode);
            if (userChannel == null)
                return buildError("CREATE_MESSAGE", "Вы не являетесь участником этого канала");

            MessageDto message = new MessageDto();
            message.setText(text);
            message.setDateSend(LocalDateTime.now());
            message.setChannelId(userChannel.getId());
            messageDao.save(message);

            ObjectNode payloadResponse = MAPPER.createObjectNode();
            payloadResponse.put("id", message.getId());
            payloadResponse.put("text", message.getText());
            payloadResponse.put("dateSend", message.getDateSend().toString());
            payloadResponse.put("channelCode", channelCode);
            payloadResponse.put("userCode", userCode);
            return buildSuccess("CREATE_MESSAGE", payloadResponse);
        } catch (Exception e) {
            return buildError("CREATE_MESSAGE", "Ошибка при отправке сообщения: " + e.getMessage());
        }
    }

    private String handleUpdateMessage(String userCode, ObjectNode payload) {
        try {
            if (!payload.has("messageId"))
                return buildError("UPDATE_MESSAGE", "Не указан ID сообщения (messageId)");
            long messageId = payload.get("messageId").asLong();

            if (!payload.has("text") || payload.get("text").asText().trim().isEmpty())
                return buildError("UPDATE_MESSAGE", "Текст сообщения не может быть пустым");
            String newText = payload.get("text").asText().trim();

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

    private String handleDeleteMessage(String userCode, ObjectNode payload) {
        try {
            if (!payload.has("messageId"))
                return buildError("DELETE_MESSAGE", "Не указан ID сообщения (messageId)");
            long messageId = payload.get("messageId").asLong();

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

    private String handleSearchMessages(String userCode, ObjectNode payload) {
        try {
            if (!payload.has("channelCode") || payload.get("channelCode").asText().isEmpty())
                return buildError("SEARCH_MESSAGES", "Не указан код канала (channelCode)");
            String channelCode = payload.get("channelCode").asText();

            String searchText = payload.has("text") ? payload.get("text").asText() : "";
            int page = Math.max(1, payload.has("page") ? payload.get("page").asInt(1) : 1);
            int size = Math.max(1, Math.min(100, payload.has("size") ? payload.get("size").asInt(20) : 20));

            List<MessageDto> messages = messageDao.findByChannelCode(channelCode, searchText, page, size);

            Map<Long, String> userChannelToUserCode = new HashMap<>();
            for (UserChannelDto uc : userChannelDao.findAllByChannelCode(channelCode)) {
                userChannelToUserCode.put(uc.getId(), uc.getUserCode());
            }

            ArrayNode messagesArray = MAPPER.createArrayNode();
            for (MessageDto msg : messages) {
                ObjectNode msgNode = MAPPER.createObjectNode();
                msgNode.put("id", msg.getId());
                msgNode.put("text", msg.getText());
                msgNode.put("dateSend", msg.getDateSend() != null ? msg.getDateSend().toString() : "");
                msgNode.put("channelCode", channelCode);
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
