package com.mycompany.messenger.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mycompany.messenger.dao.FileDao;
import com.mycompany.messenger.dao.UserChannelDao;
import com.mycompany.messenger.dto.FileDto;
import com.mycompany.messenger.util.JwtService;
import io.javalin.http.Context;
import io.javalin.http.UploadedFile;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class FilesController {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final Path UPLOAD_DIR = Paths.get("uploads");

    private final FileDao fileDao;
    private final UserChannelDao userChannelDao;
    private final JwtService jwtService;

    public FilesController(JwtService jwtService) {
        this.fileDao = new FileDao();
        this.userChannelDao = new UserChannelDao();
        this.jwtService = jwtService;
    }

    private String extractUserCode(Context ctx) {
        String token = ctx.queryParam("token");
        if (token == null || token.isEmpty()) {
            token = ctx.header("Authorization");
            if (token != null && token.startsWith("Bearer ")) {
                token = token.substring(7);
            }
        }
        if (token == null || token.isEmpty()) {
            token = ctx.formParam("token");
        }
        return token != null ? jwtService.validateToken(token) : null;
    }

    private ObjectNode errorJson(String message) {
        ObjectNode err = MAPPER.createObjectNode();
        err.put("status", "ERROR");
        err.put("error", message);
        return err;
    }

    private ObjectNode successJson(ObjectNode payload) {
        ObjectNode res = MAPPER.createObjectNode();
        res.put("status", "SUCCESS");
        if (payload != null) res.set("payload", payload);
        return res;
    }

    // POST /api/files/upload — загрузка файла (multipart)
    public void handleUpload(Context ctx) {
        try {
            String userCode = extractUserCode(ctx);
            if (userCode == null) {
                ctx.status(401).json(errorJson("Требуется аутентификация"));
                return;
            }

            UploadedFile uploadedFile = ctx.uploadedFile("file");
            if (uploadedFile == null) {
                ctx.status(400).json(errorJson("Файл не передан"));
                return;
            }

            Files.createDirectories(UPLOAD_DIR);

            String originalName = uploadedFile.filename();
            String storedName = UUID.randomUUID().toString() + "_" + originalName;
            Path targetPath = UPLOAD_DIR.resolve(storedName).normalize();

            try (InputStream is = uploadedFile.content()) {
                Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }

            long fileSize = Files.size(targetPath);
            String contentType = uploadedFile.contentType();
            if (contentType == null || contentType.isEmpty()) contentType = "application/octet-stream";

            FileDto fileDto = new FileDto();
            fileDto.setFileName(originalName);
            fileDto.setStoredName(storedName);
            fileDto.setFilePath(targetPath.toAbsolutePath().toString());
            fileDto.setFileSize(fileSize);
            fileDto.setFileType(contentType);
            fileDto.setUploadDate(LocalDateTime.now());
            fileDto.setUserCode(userCode);

            fileDao.save(fileDto);

            ObjectNode payload = MAPPER.createObjectNode();
            payload.put("id", fileDto.getId());
            payload.put("fileName", fileDto.getFileName());
            payload.put("fileSize", fileDto.getFileSize());
            payload.put("fileType", fileDto.getFileType());

            ctx.status(200).json(successJson(payload));

        } catch (Exception e) {
            ctx.status(500).json(errorJson("Ошибка загрузки файла: " + e.getMessage()));
        }
    }

    // GET /api/files/{fileId} — скачивание файла
    public void handleDownload(Context ctx) {
        try {
            String userCode = extractUserCode(ctx);
            if (userCode == null) {
                ctx.status(401).json(errorJson("Требуется аутентификация"));
                return;
            }

            long fileId = Long.parseLong(ctx.pathParam("fileId"));
            FileDto fileDto = fileDao.findById(fileId);
            if (fileDto == null) {
                ctx.status(404).json(errorJson("Файл не найден"));
                return;
            }

            Long messageId = fileDto.getMessageId();
            if (messageId == null) {
                ctx.status(400).json(errorJson("Файл ещё не привязан к сообщению"));
                return;
            }

            String channelCode = findChannelCodeByMessageId(messageId);
            if (channelCode == null) {
                ctx.status(404).json(errorJson("Сообщение не найдено"));
                return;
            }

            boolean isMember = userChannelDao.findByUserCodeAndChannelCode(userCode, channelCode) != null;
            if (!isMember) {
                ctx.status(403).json(errorJson("Вы не являетесь участником этого канала"));
                return;
            }

            Path filePath = Paths.get(fileDto.getFilePath());
            if (!Files.exists(filePath)) {
                ctx.status(404).json(errorJson("Файл не найден на диске"));
                return;
            }

            ctx.contentType(fileDto.getFileType() != null ? fileDto.getFileType() : "application/octet-stream");
            ctx.header("Content-Disposition", "attachment; filename=\"" + fileDto.getFileName() + "\"");
            ctx.result(Files.newInputStream(filePath));

        } catch (NumberFormatException e) {
            ctx.status(400).json(errorJson("Неверный ID файла"));
        } catch (Exception e) {
            ctx.status(500).json(errorJson("Ошибка скачивания файла: " + e.getMessage()));
        }
    }

    // GET /api/files/by-message/{messageId} — список файлов сообщения
    public void handleListByMessage(Context ctx) {
        try {
            String userCode = extractUserCode(ctx);
            if (userCode == null) {
                ctx.status(401).json(errorJson("Требуется аутентификация"));
                return;
            }

            long messageId = Long.parseLong(ctx.pathParam("messageId"));

            String channelCode = findChannelCodeByMessageId(messageId);
            if (channelCode == null) {
                ctx.status(404).json(errorJson("Сообщение не найдено"));
                return;
            }

            boolean isMember = userChannelDao.findByUserCodeAndChannelCode(userCode, channelCode) != null;
            if (!isMember) {
                ctx.status(403).json(errorJson("Вы не являетесь участником этого канала"));
                return;
            }

            List<FileDto> files = fileDao.findByMessageId(messageId);
            ArrayNode filesArray = MAPPER.createArrayNode();
            for (FileDto f : files) {
                ObjectNode fn = MAPPER.createObjectNode();
                fn.put("id", f.getId());
                fn.put("fileName", f.getFileName());
                fn.put("fileSize", f.getFileSize());
                fn.put("fileType", f.getFileType());
                filesArray.add(fn);
            }

            ObjectNode payload = MAPPER.createObjectNode();
            payload.set("files", filesArray);
            ctx.status(200).json(successJson(payload));

        } catch (NumberFormatException e) {
            ctx.status(400).json(errorJson("Неверный ID сообщения"));
        } catch (Exception e) {
            ctx.status(500).json(errorJson("Ошибка получения файлов: " + e.getMessage()));
        }
    }

    // DELETE /api/files/{fileId} — удаление файла (только автор)
    public void handleDelete(Context ctx) {
        try {
            String userCode = extractUserCode(ctx);
            if (userCode == null) {
                ctx.status(401).json(errorJson("Требуется аутентификация"));
                return;
            }

            long fileId = Long.parseLong(ctx.pathParam("fileId"));
            FileDto fileDto = fileDao.findById(fileId);
            if (fileDto == null) {
                ctx.status(404).json(errorJson("Файл не найден"));
                return;
            }

            if (!fileDto.getUserCode().equals(userCode)) {
                ctx.status(403).json(errorJson("Только автор может удалить файл"));
                return;
            }

            Path filePath = Paths.get(fileDto.getFilePath());
            Files.deleteIfExists(filePath);
            fileDao.delete(fileId);

            ctx.status(200).json(successJson(null));

        } catch (NumberFormatException e) {
            ctx.status(400).json(errorJson("Неверный ID файла"));
        } catch (Exception e) {
            ctx.status(500).json(errorJson("Ошибка удаления файла: " + e.getMessage()));
        }
    }

    private String findChannelCodeByMessageId(long messageId) throws Exception {
        var dao = new com.mycompany.messenger.dao.MessageDao();
        return dao.findChannelCodeById(messageId);
    }
}
