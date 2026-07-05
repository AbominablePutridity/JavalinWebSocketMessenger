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

public class ChannelController {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    private final ChannelDao channelDao;
    private final UserChannelDao userChannelDao;

    public ChannelController() {
        this.channelDao = new ChannelDao();
        this.userChannelDao = new UserChannelDao();
    }

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
            List<ChannelDto> channels = channelDao.findByUserCodeAndName(userCode, searchName);
            ArrayNode channelsArray = MAPPER.createArrayNode();
            for (ChannelDto channel : channels) {
                ObjectNode channelNode = MAPPER.createObjectNode();
                channelNode.put("code", channel.getCode());
                channelNode.put("name", channel.getName());
                channelNode.put("description", channel.getDescription() != null ? channel.getDescription() : "");
                channelNode.put("creationDate", channel.getCreationDate() != null ? channel.getCreationDate().toString() : "");
                channelsArray.add(channelNode);
            }
            ObjectNode payloadResponse = MAPPER.createObjectNode();
            payloadResponse.set("channels", channelsArray);
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

            userChannelDao.deleteByChannelCode(channelCode);
            boolean deleted = channelDao.delete(channelCode, userCode);

            if (deleted) {
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

            ObjectNode payloadResponse = MAPPER.createObjectNode();
            payloadResponse.put("message", "Пользователь добавлен в канал");
            payloadResponse.put("channelCode", channelCode);
            payloadResponse.put("memberCode", memberCode);
            return buildSuccess("ADD_MEMBER", payloadResponse);
        } catch (Exception e) {
            return buildError("ADD_MEMBER", "Ошибка при добавлении участника: " + e.getMessage());
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
