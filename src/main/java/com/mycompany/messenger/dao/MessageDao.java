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

/**
 *
 * @author User
 */
public class MessageDao {
    
    // ЦЕЛЕВОЙ ПОИСК: Найти все сообщения по ID связи UserChannel
    public List<MessageDto> findByChannelId(long channelId) throws SQLException {
        String sql = "SELECT * FROM messages WHERE channel_id = ? ORDER BY date_send ASC";
        List<MessageDto> messages = new ArrayList<>();
        
        try (Connection conn = DbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, channelId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    messages.add(mapRowToDto(rs));
                }
            }
        }
        return messages;
    }

    // CREATE - Создание сообщения (id генерируется БД автоматически)
    public void save(MessageDto message) throws SQLException {
        String sql = "INSERT INTO messages (text, date_send, channel_id) VALUES (?, ?, ?)";
        try (Connection conn = DbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            stmt.setString(1, message.getText());
            stmt.setObject(2, message.getDateSend()); // Запись LocalDateTime
            stmt.setLong(3, message.getChannelId());  // ID из таблицы user_channels
            
            stmt.executeUpdate();
            
            // Получаем сгенерированный базой данных id
            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    message.setId(generatedKeys.getLong(1));
                }
            }
        }
    }

    // READ - Поиск одного сообщения по его первичному ключу id
    public MessageDto findById(long id) throws SQLException {
        String sql = "SELECT * FROM messages WHERE id = ?";
        try (Connection conn = DbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRowToDto(rs);
                }
            }
        }
        return null; // Сообщение не найдено
    }

    // READ ALL - Получение абсолютно всех сообщений из БД
    public List<MessageDto> findAll() throws SQLException {
        String sql = "SELECT * FROM messages";
        List<MessageDto> messages = new ArrayList<>();
        try (Connection conn = DbConfig.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                messages.add(mapRowToDto(rs));
            }
        }
        return messages;
    }

    // UPDATE - Обновление текста сообщения по его id
    public void update(MessageDto message) throws SQLException {
        String sql = "UPDATE messages SET text = ? WHERE id = ?";
        try (Connection conn = DbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, message.getText());
            stmt.setLong(2, message.getId());
            
            stmt.executeUpdate();
        }
    }

    // DELETE - Удаление сообщения по id
    public void delete(long id) throws SQLException {
        String sql = "DELETE FROM messages WHERE id = ?";
        try (Connection conn = DbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, id);
            stmt.executeUpdate();
        }
    }

    // Вспомогательный метод для маппинга строки БД в объект DTO
    private MessageDto mapRowToDto(ResultSet rs) throws SQLException {
        MessageDto message = new MessageDto();
        message.setId(rs.getLong("id"));
        message.setText(rs.getString("text"));
        // Извлечение TIMESTAMP как LocalDateTime
        message.setDateSend(rs.getObject("date_send", LocalDateTime.class));
        message.setChannelId(rs.getLong("channel_id"));
        return message;
    }
}
