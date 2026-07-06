package com.mycompany.messenger.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mycompany.messenger.dao.FileDao;
import com.mycompany.messenger.dao.MessageDao;
import com.mycompany.messenger.dao.UserChannelDao;
import com.mycompany.messenger.dto.FileDto;
import com.mycompany.messenger.dto.MessageDto;
import com.mycompany.messenger.dto.UserChannelDto;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public class MessageController {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    private final MessageDao messageDao;
    private final UserChannelDao userChannelDao;
    private final FileDao fileDao;
    private final BiConsumer<String, String> pusher;

    public MessageController() {
        this((targetUserCode, message) -> {});
    }

    public MessageController(BiConsumer<String, String> pusher) {
        this.messageDao = new MessageDao();
        this.userChannelDao = new UserChannelDao();
        this.fileDao = new FileDao();
        this.pusher = pusher;
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

    private void broadcastToChannel(String channelCode, String excludeUserCode, String message) {
        try {
            List<UserChannelDto> members = userChannelDao.findAllByChannelCode(channelCode);
            for (UserChannelDto uc : members) {
                if (!uc.getUserCode().equals(excludeUserCode)) {
                    pusher.accept(uc.getUserCode(), message);
                }
            }
        } catch (Exception e) {
            System.err.println("Broadcast error: " + e.getMessage());
        }
    }

    private String handleCreateMessage(String userCode, ObjectNode payload) {
        try {
            if (!payload.has("channelCode") || payload.get("channelCode").asText().isEmpty())
                return buildError("CREATE_MESSAGE", "Не указан код канала (channelCode)");
            String channelCode = payload.get("channelCode").asText();

            String text = payload.has("text") ? payload.get("text").asText().trim() : "";

            UserChannelDto userChannel = userChannelDao.findByUserCodeAndChannelCode(userCode, channelCode);
            if (userChannel == null)
                return buildError("CREATE_MESSAGE", "Вы не являетесь участником этого канала");

            MessageDto message = new MessageDto();
            message.setText(text);
            message.setDateSend(LocalDateTime.now());
            message.setChannelId(userChannel.getId());
            messageDao.save(message);

            // Привязываем файлы к сообщению, если они были переданы
            if (payload.has("fileIds")) {
                var fileIds = payload.get("fileIds");
                for (var idNode : fileIds) {
                    long fileId = idNode.asLong();
                    fileDao.updateMessageId(fileId, message.getId());
                }
            }

            // Загружаем файлы для ответа
            List<FileDto> files = fileDao.findByMessageId(message.getId());

            ObjectNode payloadResponse = MAPPER.createObjectNode();
            payloadResponse.put("id", message.getId());
            payloadResponse.put("text", message.getText());
            payloadResponse.put("dateSend", message.getDateSend().toString());
            payloadResponse.put("channelCode", channelCode);
            payloadResponse.put("userCode", userCode);
            payloadResponse.set("files", filesToArray(files));

            // Push NEW_MESSAGE всем участникам канала (кроме отправителя)
            ObjectNode pushMsg = MAPPER.createObjectNode();
            pushMsg.put("action", "NEW_MESSAGE");
            pushMsg.set("payload", payloadResponse.deepCopy());
            String pushStr = pushMsg.toString();
            broadcastToChannel(channelCode, userCode, pushStr);

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

            // Находим channelCode по messageId через messageDao
            String channelCode = messageDao.findChannelCodeById(messageId);
            if (channelCode == null) {
                return buildError("UPDATE_MESSAGE", "Сообщение не найдено");
            }

            boolean updated = messageDao.update(messageId, newText, userCode);
            if (updated) {
                ObjectNode payloadResponse = MAPPER.createObjectNode();
                payloadResponse.put("id", messageId);
                payloadResponse.put("text", newText);
                payloadResponse.put("channelCode", channelCode);
                payloadResponse.put("userCode", userCode);

                // Push MESSAGE_UPDATED всем участникам (кроме отправителя)
                ObjectNode pushMsg = MAPPER.createObjectNode();
                pushMsg.put("action", "MESSAGE_UPDATED");
                pushMsg.set("payload", payloadResponse.deepCopy());
                broadcastToChannel(channelCode, userCode, pushMsg.toString());

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

            // Находим channelCode до удаления
            String channelCode = messageDao.findChannelCodeById(messageId);
            if (channelCode == null) {
                return buildError("DELETE_MESSAGE", "Сообщение не найдено");
            }

            // Удаляем физические файлы с диска перед удалением сообщения
            List<FileDto> attachedFiles = fileDao.findByMessageId(messageId);
            for (FileDto f : attachedFiles) {
                try {
                    Files.deleteIfExists(Paths.get(f.getFilePath()));
                } catch (Exception e) {
                    System.err.println("Ошибка удаления файла с диска: " + e.getMessage());
                }
            }

            boolean deleted = messageDao.delete(messageId, userCode);
            if (deleted) {
                // messageDao.delete сработал — CASCADE удалит записи files из БД
                ObjectNode payloadResponse = MAPPER.createObjectNode();
                payloadResponse.put("message", "Сообщение успешно удалено");
                payloadResponse.put("id", messageId);
                payloadResponse.put("channelCode", channelCode);

                // Push MESSAGE_DELETED всем участникам (кроме отправителя)
                ObjectNode pushMsg = MAPPER.createObjectNode();
                pushMsg.put("action", "MESSAGE_DELETED");
                pushMsg.set("payload", payloadResponse.deepCopy());
                broadcastToChannel(channelCode, userCode, pushMsg.toString());

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

            // Загружаем файлы для всех сообщений одним запросом
            List<Long> msgIds = messages.stream().map(MessageDto::getId).toList();
            List<FileDto> allFiles = fileDao.findByMessageIds(msgIds);
            Map<Long, List<FileDto>> msgFileMap = new HashMap<>();
            for (FileDto f : allFiles) {
                Long mid = f.getMessageId();
                if (mid != null) {
                    msgFileMap.computeIfAbsent(mid, k -> new java.util.ArrayList<>()).add(f);
                }
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

                List<FileDto> msgFiles = msgFileMap.get(msg.getId());
                if (msgFiles != null && !msgFiles.isEmpty()) {
                    msgNode.set("files", filesToArray(msgFiles));
                } else {
                    msgNode.set("files", MAPPER.createArrayNode());
                }

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

    private ArrayNode filesToArray(List<FileDto> files) {
        ArrayNode arr = MAPPER.createArrayNode();
        for (FileDto f : files) {
            ObjectNode fn = MAPPER.createObjectNode();
            fn.put("id", f.getId());
            fn.put("fileName", f.getFileName());
            fn.put("fileSize", f.getFileSize());
            fn.put("fileType", f.getFileType() != null ? f.getFileType() : "");
            arr.add(fn);
        }
        return arr;
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
