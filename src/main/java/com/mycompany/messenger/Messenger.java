package com.mycompany.messenger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mycompany.messenger.controller.AuthController;
import com.mycompany.messenger.controller.ChannelController;
import com.mycompany.messenger.controller.FilesController;
import com.mycompany.messenger.controller.MessageController;
import com.mycompany.messenger.dao.DatabaseInit;
import com.mycompany.messenger.util.JwtService;
import io.javalin.Javalin;
import io.javalin.websocket.WsContext;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

public class Messenger {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    public static void main(String[] args) throws Exception {
        DatabaseInit.createTablesIfNotExist();

        // Карта: userCode -> WebSocket-контекст (для push-уведомлений)
        Map<String, WsContext> userContexts = new ConcurrentHashMap<>();
        // Обратная карта: sessionId -> userCode (для очистки при отключении)
        Map<String, String> sessionToUser = new ConcurrentHashMap<>();

        // Функция для отправки push-уведомлений конкретному пользователю
        BiConsumer<String, String> pusher = (targetUserCode, message) -> {
            WsContext ctx = userContexts.get(targetUserCode);
            if (ctx != null && ctx.session.isOpen()) {
                try {
                    ctx.send(message);
                } catch (Exception e) {
                    System.err.println("Push error to " + targetUserCode + ": " + e.getMessage());
                }
            }
        };

        AuthController authController = new AuthController();
        ChannelController channelController = new ChannelController(pusher);
        MessageController messageController = new MessageController(pusher);
        JwtService jwtService = authController.getJwtService();
        FilesController filesController = new FilesController(jwtService);

        // Создаём директорию для загруженных файлов
        Files.createDirectories(Paths.get("uploads"));

        Javalin app = Javalin.create(config -> {
            config.bundledPlugins.enableCors(cors -> {
                cors.addRule(it -> it.anyHost());
            });

            config.routes.ws("/websocket", ws -> {
                ws.onConnect(ctx -> {
                    System.out.println("Client connected: " + ctx.sessionId);
                });

                ws.onMessage(ctx -> {
                    try {
                        String msg = ctx.message();
                        System.out.println("Received: " + msg);

                        ObjectNode json = MAPPER.readValue(msg, ObjectNode.class);
                        String action = json.has("action") ? json.get("action").asText().toUpperCase() : "";

                        if ("REGISTER".equals(action) || "LOGIN".equals(action)) {
                            String response = authController.handleAction(action, json);
                            if (json.has("requestId")) {
                                ObjectNode respJson = MAPPER.readValue(response, ObjectNode.class);
                                respJson.put("requestId", json.get("requestId").asText());
                                response = respJson.toString();
                            }
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
                            if (json.has("requestId")) {
                                error.put("requestId", json.get("requestId").asText());
                            }
                            ctx.send(error.toString());
                            return;
                        }

                        // Регистрируем контекст пользователя для push-уведомлений
                        String oldSessionId = null;
                        for (Map.Entry<String, String> e : sessionToUser.entrySet()) {
                            if (e.getValue().equals(userCode)) {
                                oldSessionId = e.getKey();
                                break;
                            }
                        }
                        userContexts.put(userCode, ctx);
                        sessionToUser.put(ctx.sessionId, userCode);

                        // Если старый sessionId отличался — чистим
                        if (oldSessionId != null && !oldSessionId.equals(ctx.sessionId)) {
                            sessionToUser.remove(oldSessionId);
                        }

                        String response;
                        if (action.contains("MESSAGE")) {
                            response = messageController.handleAction(action, userCode, json);
                        } else {
                            response = channelController.handleAction(action, userCode, json);
                        }

                        // Добавляем requestId в ответ, если был в запросе
                        if (json.has("requestId")) {
                            ObjectNode respJson = MAPPER.readValue(response, ObjectNode.class);
                            respJson.put("requestId", json.get("requestId").asText());
                            response = respJson.toString();
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
                    System.out.println("Client disconnected: " + ctx.sessionId);
                    String disconnectedUser = sessionToUser.remove(ctx.sessionId);
                    if (disconnectedUser != null) {
                        // Удаляем только если это последняя сессия пользователя
                        WsContext storedCtx = userContexts.get(disconnectedUser);
                        if (storedCtx != null && storedCtx.sessionId.equals(ctx.sessionId)) {
                            userContexts.remove(disconnectedUser);
                        }
                    }
                });

                ws.onError(ctx -> {
                    System.err.println("Session error: " + ctx.sessionId);
                    String errorUser = sessionToUser.remove(ctx.sessionId);
                    if (errorUser != null) {
                        WsContext storedCtx = userContexts.get(errorUser);
                        if (storedCtx != null && storedCtx.sessionId.equals(ctx.sessionId)) {
                            userContexts.remove(errorUser);
                        }
                    }
                });
            });

            // HTTP-роуты для загрузки/скачивания файлов
            config.routes.post("/api/files/upload", filesController::handleUpload);
            config.routes.get("/api/files/{fileId}", filesController::handleDownload);
            config.routes.get("/api/files/by-message/{messageId}", filesController::handleListByMessage);
            config.routes.delete("/api/files/{fileId}", filesController::handleDelete);
            
            config.staticFiles.add("/public");
        }).start(7070);

        System.out.println("Messenger server started on port 7070");
    }
}
