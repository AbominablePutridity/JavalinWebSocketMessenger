package com.mycompany.messenger.dao;

import com.mycompany.messenger.dto.MessageDto;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class MessageDao {

    public List<MessageDto> findByChannelId(long channelId) throws SQLException {
        String sql = "SELECT * FROM messages WHERE channel_id = ? ORDER BY date_send ASC";
        List<MessageDto> messages = new ArrayList<>();
        try (Connection conn = DbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, channelId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) messages.add(mapRowToDto(rs));
            }
        }
        return messages;
    }

    public void save(MessageDto message) throws SQLException {
        String sql = "INSERT INTO messages (text, date_send, channel_id) VALUES (?, ?, ?)";
        try (Connection conn = DbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, message.getText());
            stmt.setObject(2, message.getDateSend());
            stmt.setLong(3, message.getChannelId());
            stmt.executeUpdate();
            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) message.setId(generatedKeys.getLong(1));
            }
        }
    }

    public MessageDto findById(long id) throws SQLException {
        String sql = "SELECT * FROM messages WHERE id = ?";
        try (Connection conn = DbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return mapRowToDto(rs);
            }
        }
        return null;
    }

    public List<MessageDto> findAll(int page, int size) throws SQLException {
        String sql = "SELECT * FROM messages ORDER BY id LIMIT ? OFFSET ?";
        List<MessageDto> messages = new ArrayList<>();
        int offset = (page - 1) * size;
        try (Connection conn = DbConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, size);
            pstmt.setInt(2, offset);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) messages.add(mapRowToDto(rs));
            }
        }
        return messages;
    }

    public String findChannelCodeById(long messageId) throws SQLException {
        String sql = "SELECT uc.channel_code FROM messages m INNER JOIN user_channels uc ON m.channel_id = uc.id WHERE m.id = ?";
        try (Connection conn = DbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, messageId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getString("channel_code");
            }
        }
        return null;
    }

    public boolean update(long messageId, String newText, String userCode) throws SQLException {
        String sql = """
            UPDATE messages SET text = ? WHERE id = ?
            AND channel_id IN (SELECT id FROM user_channels WHERE user_code = ?)
        """;
        try (Connection conn = DbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, newText);
            stmt.setLong(2, messageId);
            stmt.setString(3, userCode);
            return stmt.executeUpdate() > 0;
        }
    }

    public boolean delete(long id, String userCode) throws SQLException {
        String sql = """
            DELETE FROM messages WHERE id = ?
            AND channel_id IN (SELECT id FROM user_channels WHERE user_code = ?)
        """;
        try (Connection conn = DbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, id);
            stmt.setString(2, userCode);
            return stmt.executeUpdate() > 0;
        }
    }

    public List<MessageDto> findByChannelCode(String channelCode, String searchText, int page, int size) throws SQLException {
        boolean hasTextFilter = searchText != null && !searchText.trim().isEmpty();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT m.* FROM messages m");
        sql.append(" INNER JOIN user_channels uc ON m.channel_id = uc.id");
        sql.append(" WHERE uc.channel_code = ?");
        if (hasTextFilter) sql.append(" AND m.text ILIKE ?");
        sql.append(" ORDER BY m.date_send DESC");
        sql.append(" LIMIT ? OFFSET ?");

        int offset = (page - 1) * size;
        List<MessageDto> messages = new ArrayList<>();
        try (Connection conn = DbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            int idx = 1;
            stmt.setString(idx++, channelCode);
            if (hasTextFilter) stmt.setString(idx++, "%" + searchText.trim() + "%");
            stmt.setInt(idx++, size);
            stmt.setInt(idx, offset);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) messages.add(mapRowToDto(rs));
            }
        }
        return messages;
    }

    private MessageDto mapRowToDto(ResultSet rs) throws SQLException {
        MessageDto message = new MessageDto();
        message.setId(rs.getLong("id"));
        message.setText(rs.getString("text"));
        message.setDateSend(rs.getObject("date_send", LocalDateTime.class));
        message.setChannelId(rs.getLong("channel_id"));
        return message;
    }
}
