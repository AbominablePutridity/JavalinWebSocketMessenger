package com.mycompany.messenger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mycompany.messenger.controller.AuthController;
import com.mycompany.messenger.controller.ChannelController;
import com.mycompany.messenger.controller.MessageController;
import com.mycompany.messenger.dao.DatabaseInit;
import com.mycompany.messenger.util.JwtService;
import io.javalin.Javalin;

public class Messenger {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    public static void main(String[] args) throws Exception {
        DatabaseInit.createTablesIfNotExist();

        AuthController authController = new AuthController();
        ChannelController channelController = new ChannelController();
        MessageController messageController = new MessageController();
        JwtService jwtService = authController.getJwtService();

        Javalin app = Javalin.create(config -> {
            config.bundledPlugins.enableCors(cors -> {
                cors.addRule(it -> it.anyHost());
            });

            config.routes.ws("/websocket", ws -> {
                ws.onConnect(ctx -> {
                    System.out.println("Client connected: " + ctx.sessionId());
                });

                ws.onMessage(ctx -> {
                    try {
                        String msg = ctx.message();
                        System.out.println("Received: " + msg);

                        ObjectNode json = MAPPER.readValue(msg, ObjectNode.class);
                        String action = json.has("action") ? json.get("action").asText().toUpperCase() : "";

                        if ("REGISTER".equals(action) || "LOGIN".equals(action)) {
                            String response = authController.handleAction(action, json);
                            ctx.send(response);
                            return;
                        }

                        String userCode = null;
                        if (json.has("token")) {
                            userCode = jwtService.validateToken(json.get("token").asText());
                        }
                        if (userCode == null && json.has("userCode")) {
                            userCode = json.get("userCode").asText();
                        }
                        if (userCode == null || userCode.isEmpty()) {
                            ObjectNode error = MAPPER.createObjectNode();
                            error.put("action", action);
                            error.put("status", "ERROR");
                            error.put("error", "Требуется аутентификация (token или userCode)");
                            ctx.send(error.toString());
                            return;
                        }

                        String response;
                        if (action.contains("MESSAGE")) {
                            response = messageController.handleAction(action, userCode, json);
                        } else {
                            response = channelController.handleAction(action, userCode, json);
                        }
                        ctx.send(response);

                    } catch (Exception e) {
                        System.err.println("Error handling message: " + e.getMessage());
                        e.printStackTrace();
                        ObjectNode error = MAPPER.createObjectNode();
                        error.put("action", "UNKNOWN");
                        error.put("status", "ERROR");
                        error.put("error", "Внутренняя ошибка сервера: " + e.getMessage());
                        ctx.send(error.toString());
                    }
                });

                ws.onClose(ctx -> {
                    System.out.println("Client disconnected: " + ctx.sessionId());
                });

                ws.onError(ctx -> {
                    System.err.println("Session error: " + ctx.sessionId());
                });
            });
        }).start(7070);

        System.out.println("Messenger server started on port 7070");
    }
}
