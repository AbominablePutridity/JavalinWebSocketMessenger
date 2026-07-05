package com.mycompany.messenger.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mycompany.messenger.dao.AccessDao;
import com.mycompany.messenger.dao.UserDao;
import com.mycompany.messenger.dto.AccessDto;
import com.mycompany.messenger.dto.UserDto;
import com.mycompany.messenger.util.JwtService;
import org.mindrot.jbcrypt.BCrypt;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Контроллер аутентификации и регистрации через WebSocket.
 * <p>
 * После успешного входа/регистрации клиент получает JWT-токен.
 * Все последующие запросы отправляются с полем "token" вместо "userCode".
 * Сервер извлекает userCode из токена перед передачей в контроллеры.
 * <p>
 * Формат ответа (успех):
 * <pre>{ "action": "REGISTER", "status": "SUCCESS", "payload": { "token": "...", "userCode": "..." } }</pre>
 */
public class AuthController {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .findAndRegisterModules();

    private final JwtService jwtService;
    private final UserDao userDao;
    private final AccessDao accessDao;

    public AuthController() {
        this.jwtService = new JwtService();
        this.userDao = new UserDao();
        this.accessDao = new AccessDao();
    }

    /**
     * Возвращает ссылку на JwtService, чтобы Messenger мог валидировать токены
     * для всех остальных запросов (каналы, сообщения и т.д.).
     */
    public JwtService getJwtService() {
        return jwtService;
    }

    // ========================================================================
    // ДИСПЕТЧЕР
    // ========================================================================

    /**
     * Центральный метод-диспетчер. Вызывается из WebSocket-обработчика.
     *
     * @param action  тип операции (REGISTER, LOGIN)
     * @param payload JSON-объект с параметрами
     * @return JSON-строка с результатом
     */
    public String handleAction(String action, ObjectNode payload) {
        return switch (action != null ? action.toUpperCase() : "") {
            case "REGISTER" -> handleRegister(payload);
            case "LOGIN" -> handleLogin(payload);
            default -> buildError(action, "Неизвестный тип действия: " + action);
        };
    }

    // ========================================================================
    // 1. REGISTER — Регистрация нового пользователя
    // Клиент присылает: { "login": "...", "password": "...", "name": "...", "surname": "..." }
    // Сервер создаёт пользователя + учётные данные → возвращает JWT
    // ========================================================================

    /**
     * Регистрирует нового пользователя.
     * <p>
     * Логика:
     * <ol>
     *   <li>Валидируем поля (login, password, name, surname)</li>
     *   <li>Проверяем, что login ещё не занят</li>
     *   <li>Создаём пользователя (users) с UUID-кодом</li>
     *   <li>Создаём учётную запись (access) с хешированным паролем</li>
     *   <li>Генерируем JWT-токен</li>
     * </ol>
     */
    private String handleRegister(ObjectNode payload) {
        try {
            // Извлекаем и валидируем логин
            String login = payload.has("login") ? payload.get("login").asText().trim() : "";
            if (login.isEmpty()) {
                return buildError("REGISTER", "Логин не может быть пустым");
            }

            // Извлекаем и валидируем пароль
            String password = payload.has("password") ? payload.get("password").asText() : "";
            if (password.isEmpty()) {
                return buildError("REGISTER", "Пароль не может быть пустым");
            }
            if (password.length() < 4) {
                return buildError("REGISTER", "Пароль должен быть не менее 4 символов");
            }

            // Извлекаем и валидируем имя
            String name = payload.has("name") ? payload.get("name").asText().trim() : "";
            if (name.isEmpty()) {
                return buildError("REGISTER", "Имя не может быть пустым");
            }

            // Извлекаем фамилию (опционально, но если передана — не пустая)
            String surname = payload.has("surname") ? payload.get("surname").asText().trim() : "";

            // Проверяем, что логин ещё не занят
            AccessDto existingAccess = accessDao.findByLogin(login);
            if (existingAccess != null) {
                return buildError("REGISTER", "Пользователь с таким логином уже существует");
            }

            // Генерируем уникальный код пользователя
            String userCode = UUID.randomUUID().toString();

            // Создаём пользователя
            UserDto user = new UserDto();
            user.setCode(userCode);
            user.setName(name);
            user.setSurname(surname);
            user.setRegistrationDate(LocalDateTime.now());
            userDao.save(user);

            // Хешируем пароль и создаём учётную запись
            String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());
            AccessDto access = new AccessDto();
            access.setLogin(login);
            access.setPassword(hashedPassword);
            access.setUserCode(userCode);
            accessDao.save(access);

            // Генерируем JWT-токен
            String token = jwtService.generateToken(userCode);

            // Формируем ответ
            ObjectNode payloadResponse = MAPPER.createObjectNode();
            payloadResponse.put("token", token);
            payloadResponse.put("userCode", userCode);
            payloadResponse.put("name", name);
            payloadResponse.put("surname", surname);

            return buildSuccess("REGISTER", payloadResponse);

        } catch (Exception e) {
            return buildError("REGISTER", "Ошибка при регистрации: " + e.getMessage());
        }
    }

    // ========================================================================
    // 2. LOGIN — Вход в систему
    // Клиент присылает: { "login": "...", "password": "..." }
    // Сервер проверяет пароль → возвращает JWT
    // ========================================================================

    /**
     * Аутентифицирует пользователя по логину и паролю.
     * <p>
     * Логика:
     * <ol>
     *   <li>Ищем access по логину</li>
     *   <li>Проверяем пароль через BCrypt</li>
     *   <li>Генерируем JWT-токен</li>
     * </ol>
     */
    private String handleLogin(ObjectNode payload) {
        try {
            // Извлекаем логин
            String login = payload.has("login") ? payload.get("login").asText().trim() : "";
            if (login.isEmpty()) {
                return buildError("LOGIN", "Логин не может быть пустым");
            }

            // Извлекаем пароль
            String password = payload.has("password") ? payload.get("password").asText() : "";
            if (password.isEmpty()) {
                return buildError("LOGIN", "Пароль не может быть пустым");
            }

            // Ищем учётную запись по логину
            AccessDto access = accessDao.findByLogin(login);
            if (access == null) {
                return buildError("LOGIN", "Неверный логин или пароль");
            }

            // Проверяем пароль через BCrypt
            if (!BCrypt.checkpw(password, access.getPassword())) {
                return buildError("LOGIN", "Неверный логин или пароль");
            }

            // Генерируем JWT-токен
            String token = jwtService.generateToken(access.getUserCode());

            // Формируем ответ
            ObjectNode payloadResponse = MAPPER.createObjectNode();
            payloadResponse.put("token", token);
            payloadResponse.put("userCode", access.getUserCode());

            return buildSuccess("LOGIN", payloadResponse);

        } catch (Exception e) {
            return buildError("LOGIN", "Ошибка при входе: " + e.getMessage());
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
