package com.mycompany.messenger.dao;

import com.mycompany.messenger.dto.FileDto;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class FileDao {

    public void save(FileDto file) throws SQLException {
        String sql = "INSERT INTO files (file_name, stored_name, file_path, file_size, file_type, message_id, upload_date, user_code) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, file.getFileName());
            stmt.setString(2, file.getStoredName());
            stmt.setString(3, file.getFilePath());
            stmt.setLong(4, file.getFileSize());
            stmt.setString(5, file.getFileType());
            if (file.getMessageId() != null) {
                stmt.setLong(6, file.getMessageId());
            } else {
                stmt.setNull(6, java.sql.Types.BIGINT);
            }
            stmt.setObject(7, file.getUploadDate());
            stmt.setString(8, file.getUserCode());
            stmt.executeUpdate();
            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) file.setId(generatedKeys.getLong(1));
            }
        }
    }

    public FileDto findById(long id) throws SQLException {
        String sql = "SELECT * FROM files WHERE id = ?";
        try (Connection conn = DbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return mapRowToDto(rs);
            }
        }
        return null;
    }

    public List<FileDto> findByMessageId(long messageId) throws SQLException {
        String sql = "SELECT * FROM files WHERE message_id = ? ORDER BY id";
        List<FileDto> files = new ArrayList<>();
        try (Connection conn = DbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, messageId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) files.add(mapRowToDto(rs));
            }
        }
        return files;
    }

    public List<FileDto> findByMessageIds(List<Long> messageIds) throws SQLException {
        if (messageIds.isEmpty()) return new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM files WHERE message_id IN (");
        for (int i = 0; i < messageIds.size(); i++) {
            sql.append(i > 0 ? ",?" : "?");
        }
        sql.append(") ORDER BY id");
        List<FileDto> files = new ArrayList<>();
        try (Connection conn = DbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < messageIds.size(); i++) {
                stmt.setLong(i + 1, messageIds.get(i));
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) files.add(mapRowToDto(rs));
            }
        }
        return files;
    }

    public void updateMessageId(long fileId, long messageId) throws SQLException {
        String sql = "UPDATE files SET message_id = ? WHERE id = ?";
        try (Connection conn = DbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, messageId);
            stmt.setLong(2, fileId);
            stmt.executeUpdate();
        }
    }

    public void delete(long id) throws SQLException {
        String sql = "DELETE FROM files WHERE id = ?";
        try (Connection conn = DbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, id);
            stmt.executeUpdate();
        }
    }

    public void deleteByMessageId(long messageId) throws SQLException {
        String sql = "DELETE FROM files WHERE message_id = ?";
        try (Connection conn = DbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, messageId);
            stmt.executeUpdate();
        }
    }

    private FileDto mapRowToDto(ResultSet rs) throws SQLException {
        FileDto file = new FileDto();
        file.setId(rs.getLong("id"));
        file.setFileName(rs.getString("file_name"));
        file.setStoredName(rs.getString("stored_name"));
        file.setFilePath(rs.getString("file_path"));
        file.setFileSize(rs.getLong("file_size"));
        file.setFileType(rs.getString("file_type"));
        long msgId = rs.getLong("message_id");
        if (!rs.wasNull()) file.setMessageId(msgId);
        file.setUploadDate(rs.getObject("upload_date", LocalDateTime.class));
        file.setUserCode(rs.getString("user_code"));
        return file;
    }
}
