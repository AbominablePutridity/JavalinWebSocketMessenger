package com.mycompany.messenger.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mycompany.messenger.dao.ChannelDao;
import com.mycompany.messenger.dao.UserChannelDao;
import com.mycompany.messenger.dao.UserDao;
import com.mycompany.messenger.dto.ChannelDto;
import com.mycompany.messenger.dto.UserChannelDto;
import com.mycompany.messenger.dto.UserDto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

public class ChannelController {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    private final ChannelDao channelDao;
    private final UserChannelDao userChannelDao;
    private final UserDao userDao;
    private final BiConsumer<String, String> pusher;

    public ChannelController() {
        this((targetUserCode, message) -> {});
    }

    public ChannelController(BiConsumer<String, String> pusher) {
        this.channelDao = new ChannelDao();
        this.userChannelDao = new UserChannelDao();
        this.userDao = new UserDao();
        this.pusher = pusher;
    }

    public String handleAction(String action, String userCode, ObjectNode payload) {
        return switch (action != null ? action.toUpperCase() : "") {
            case "CREATE_CHANNEL" -> handleCreateChannel(userCode, payload);
            case "SEARCH_CHANNELS" -> handleSearchChannels(userCode, payload);
            case "DELETE_CHANNEL" -> handleDeleteChannel(userCode, payload);
            case "UPDATE_CHANNEL" -> handleUpdateChannel(userCode, payload);
            case "ADD_MEMBER" -> handleAddMember(userCode, payload);
            case "REMOVE_MEMBER" -> handleRemoveMember(userCode, payload);
            case "GET_CHANNEL_MEMBERS" -> handleGetChannelMembers(userCode, payload);
            default -> buildError(action, "Неизвестный тип действия: " + action);
        };
    }

    private String handleCreateChannel(String userCode, ObjectNode payload) {
        try {
            String name = payload.has("name") ? payload.get("name").asText().trim() : "";
            if (name.isEmpty()) return buildError("CREATE_CHANNEL", "Название канала не может быть пустым");
            String description = payload.has("description") ? payload.get("description").asText().trim() : "";

            String channelCode = UUID.randomUUID().toString();
            ChannelDto channel = new ChannelDto();
            channel.setCode(channelCode);
            channel.setName(name);
            channel.setDescription(description);
            channel.setCreationDate(LocalDateTime.now());
            channel.setOwnerCode(userCode);
            channelDao.save(channel);

            UserChannelDto userChannel = new UserChannelDto();
            userChannel.setUserCode(userCode);
            userChannel.setChannelCode(channelCode);
            userChannelDao.save(userChannel);

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

    private String handleSearchChannels(String userCode, ObjectNode payload) {
        try {
            String searchName = payload.has("name") ? payload.get("name").asText() : "";
            int page = Math.max(1, payload.has("page") ? payload.get("page").asInt(1) : 1);
            int size = Math.max(1, Math.min(100, payload.has("size") ? payload.get("size").asInt(50) : 50));
            List<ChannelDto> channels = channelDao.findByUserCodeAndName(userCode, searchName, page, size);
            ArrayNode channelsArray = MAPPER.createArrayNode();
            for (ChannelDto channel : channels) {
                ObjectNode channelNode = MAPPER.createObjectNode();
                channelNode.put("code", channel.getCode());
                channelNode.put("name", channel.getName());
                channelNode.put("description", channel.getDescription() != null ? channel.getDescription() : "");
                channelNode.put("creationDate", channel.getCreationDate() != null ? channel.getCreationDate().toString() : "");
                channelNode.put("ownerCode", channel.getOwnerCode() != null ? channel.getOwnerCode() : "");
                channelsArray.add(channelNode);
            }
            ObjectNode payloadResponse = MAPPER.createObjectNode();
            payloadResponse.set("channels", channelsArray);
            payloadResponse.put("page", page);
            payloadResponse.put("size", size);
            payloadResponse.put("total", channels.size());
            return buildSuccess("SEARCH_CHANNELS", payloadResponse);
        } catch (Exception e) {
            return buildError("SEARCH_CHANNELS", "Ошибка при поиске каналов: " + e.getMessage());
        }
    }

    private String handleDeleteChannel(String userCode, ObjectNode payload) {
        try {
            if (!payload.has("code") || payload.get("code").asText().isEmpty())
                return buildError("DELETE_CHANNEL", "Не указан код канала для удаления");
            String channelCode = payload.get("code").asText();

            // Получаем всех участников ДО удаления
            List<UserChannelDto> members = userChannelDao.findAllByChannelCode(channelCode);

            userChannelDao.deleteByChannelCode(channelCode);
            boolean deleted = channelDao.delete(channelCode, userCode);

            if (deleted) {
                // Оповещаем всех участников (кроме владельца — он уже знает)
                ObjectNode pushPayload = MAPPER.createObjectNode();
                pushPayload.put("action", "CHANNEL_DELETED");
                ObjectNode pushData = MAPPER.createObjectNode();
                pushData.put("channelCode", channelCode);
                pushPayload.set("payload", pushData);
                String pushMsg = pushPayload.toString();

                for (UserChannelDto uc : members) {
                    if (!uc.getUserCode().equals(userCode)) {
                        pusher.accept(uc.getUserCode(), pushMsg);
                    }
                }

                ObjectNode payloadResponse = MAPPER.createObjectNode();
                payloadResponse.put("message", "Канал успешно удалён");
                payloadResponse.put("code", channelCode);
                return buildSuccess("DELETE_CHANNEL", payloadResponse);
            } else {
                return buildError("DELETE_CHANNEL", "Канал не найден или вы не являетесь его владельцем");
            }
        } catch (Exception e) {
            return buildError("DELETE_CHANNEL", "Ошибка при удалении канала: " + e.getMessage());
        }
    }

    private String handleUpdateChannel(String userCode, ObjectNode payload) {
        try {
            if (!payload.has("code") || payload.get("code").asText().isEmpty())
                return buildError("UPDATE_CHANNEL", "Не указан код канала для обновления");
            String channelCode = payload.get("code").asText();

            ChannelDto existingChannel = channelDao.findByCode(channelCode);
            if (existingChannel == null)
                return buildError("UPDATE_CHANNEL", "Канал с таким кодом не найден");

            String newName = payload.has("name") ? payload.get("name").asText().trim() : existingChannel.getName();
            String newDescription = payload.has("description") ? payload.get("description").asText().trim() : existingChannel.getDescription();

            ChannelDto channel = new ChannelDto();
            channel.setCode(channelCode);
            channel.setName(newName);
            channel.setDescription(newDescription);
            channel.setOwnerCode(userCode);

            boolean updated = channelDao.update(channel);
            if (updated) {
                // Оповещаем всех участников канала
                List<UserChannelDto> members = userChannelDao.findAllByChannelCode(channelCode);
                ObjectNode pushPayload = MAPPER.createObjectNode();
                pushPayload.put("action", "CHANNEL_UPDATED");
                ObjectNode pushData = MAPPER.createObjectNode();
                pushData.put("channelCode", channelCode);
                pushData.put("name", newName);
                pushData.put("description", newDescription);
                pushPayload.set("payload", pushData);
                String pushMsg = pushPayload.toString();

                for (UserChannelDto uc : members) {
                    if (!uc.getUserCode().equals(userCode)) {
                        pusher.accept(uc.getUserCode(), pushMsg);
                    }
                }

                ObjectNode payloadResponse = MAPPER.createObjectNode();
                payloadResponse.put("code", channelCode);
                payloadResponse.put("name", newName);
                payloadResponse.put("description", newDescription);
                return buildSuccess("UPDATE_CHANNEL", payloadResponse);
            } else {
                return buildError("UPDATE_CHANNEL", "Канал не найден или вы не являетесь его владельцем");
            }
        } catch (Exception e) {
            return buildError("UPDATE_CHANNEL", "Ошибка при обновлении канала: " + e.getMessage());
        }
    }

    private String handleAddMember(String userCode, ObjectNode payload) {
        try {
            if (!payload.has("channelCode") || payload.get("channelCode").asText().isEmpty())
                return buildError("ADD_MEMBER", "Не указан код канала (channelCode)");
            String channelCode = payload.get("channelCode").asText();
            if (!payload.has("memberCode") || payload.get("memberCode").asText().isEmpty())
                return buildError("ADD_MEMBER", "Не указан код пользователя (memberCode)");
            String memberCode = payload.get("memberCode").asText();

            ChannelDto channel = channelDao.findByCode(channelCode);
            if (channel == null) return buildError("ADD_MEMBER", "Канал с таким кодом не найден");

            UserChannelDto existing = userChannelDao.findByUserCodeAndChannelCode(memberCode, channelCode);
            if (existing != null) return buildError("ADD_MEMBER", "Пользователь уже состоит в этом канале");

            UserChannelDto userChannel = new UserChannelDto();
            userChannel.setUserCode(memberCode);
            userChannel.setChannelCode(channelCode);
            userChannelDao.save(userChannel);

            // Push-уведомление новому участнику: CHANNEL_ADDED
            ObjectNode pushPayload = MAPPER.createObjectNode();
            pushPayload.put("action", "CHANNEL_ADDED");
            ObjectNode channelData = MAPPER.createObjectNode();
            channelData.put("code", channel.getCode());
            channelData.put("name", channel.getName());
            channelData.put("description", channel.getDescription() != null ? channel.getDescription() : "");
            channelData.put("creationDate", channel.getCreationDate() != null ? channel.getCreationDate().toString() : "");
            channelData.put("ownerCode", channel.getOwnerCode() != null ? channel.getOwnerCode() : "");
            pushPayload.set("payload", channelData);
            pusher.accept(memberCode, pushPayload.toString());

            ObjectNode payloadResponse = MAPPER.createObjectNode();
            payloadResponse.put("message", "Пользователь добавлен в канал");
            payloadResponse.put("channelCode", channelCode);
            payloadResponse.put("memberCode", memberCode);
            return buildSuccess("ADD_MEMBER", payloadResponse);
        } catch (Exception e) {
            return buildError("ADD_MEMBER", "Ошибка при добавлении участника: " + e.getMessage());
        }
    }

    private String handleGetChannelMembers(String userCode, ObjectNode payload) {
        try {
            if (!payload.has("channelCode") || payload.get("channelCode").asText().isEmpty())
                return buildError("GET_CHANNEL_MEMBERS", "Не указан код канала (channelCode)");
            String channelCode = payload.get("channelCode").asText();

            List<UserChannelDto> userChannels = userChannelDao.findAllByChannelCode(channelCode);
            ArrayNode membersArray = MAPPER.createArrayNode();
            for (UserChannelDto uc : userChannels) {
                ObjectNode memberNode = MAPPER.createObjectNode();
                memberNode.put("userCode", uc.getUserCode());
                try {
                    UserDto user = userDao.findByCode(uc.getUserCode());
                    memberNode.put("name", user != null ? user.getName() : "Неизвестно");
                    memberNode.put("surname", user != null ? user.getSurname() : "");
                } catch (Exception e) {
                    memberNode.put("name", "Неизвестно");
                    memberNode.put("surname", "");
                }
                membersArray.add(memberNode);
            }

            ObjectNode payloadResponse = MAPPER.createObjectNode();
            payloadResponse.put("channelCode", channelCode);
            payloadResponse.set("members", membersArray);
            payloadResponse.put("total", membersArray.size());
            return buildSuccess("GET_CHANNEL_MEMBERS", payloadResponse);
        } catch (Exception e) {
            return buildError("GET_CHANNEL_MEMBERS", "Ошибка при получении участников: " + e.getMessage());
        }
    }

    private String handleRemoveMember(String userCode, ObjectNode payload) {
        try {
            if (!payload.has("channelCode") || payload.get("channelCode").asText().isEmpty())
                return buildError("REMOVE_MEMBER", "Не указан код канала (channelCode)");
            String channelCode = payload.get("channelCode").asText();
            if (!payload.has("memberCode") || payload.get("memberCode").asText().isEmpty())
                return buildError("REMOVE_MEMBER", "Не указан код пользователя (memberCode)");
            String memberCode = payload.get("memberCode").asText();

            ChannelDto channel = channelDao.findByCode(channelCode);
            if (channel == null) return buildError("REMOVE_MEMBER", "Канал с таким кодом не найден");
            if (!channel.getOwnerCode().equals(userCode))
                return buildError("REMOVE_MEMBER", "Только владелец канала может удалять участников");

            userChannelDao.deleteByUserCodeAndChannelCode(memberCode, channelCode);

            // Push-уведомление удалённому участнику
            ObjectNode pushPayload = MAPPER.createObjectNode();
            pushPayload.put("action", "REMOVED_FROM_CHANNEL");
            ObjectNode pushData = MAPPER.createObjectNode();
            pushData.put("channelCode", channelCode);
            pushPayload.set("payload", pushData);
            pusher.accept(memberCode, pushPayload.toString());

            ObjectNode payloadResponse = MAPPER.createObjectNode();
            payloadResponse.put("message", "Пользователь удалён из канала");
            payloadResponse.put("channelCode", channelCode);
            payloadResponse.put("memberCode", memberCode);
            return buildSuccess("REMOVE_MEMBER", payloadResponse);
        } catch (Exception e) {
            return buildError("REMOVE_MEMBER", "Ошибка при удалении участника: " + e.getMessage());
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
