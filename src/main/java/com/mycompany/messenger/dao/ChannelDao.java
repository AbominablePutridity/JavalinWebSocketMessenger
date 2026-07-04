package com.mycompany.messenger.dao;

import static com.mycompany.messenger.dao.DbConfig.getConnection;
import com.mycompany.messenger.dto.ChannelDto;
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
public class ChannelDao {
    // CREATE - Создание канала
    public void save(ChannelDto channel) throws SQLException {
        String sql = "INSERT INTO channels (code, name, description, creation_date) VALUES (?, ?, ?, ?)";
        try (Connection conn = DbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, channel.getCode()); // Требуется getCode() в DTO
            stmt.setString(2, channel.getName());
            stmt.setString(3, channel.getDescription());
            stmt.setObject(4, channel.getCreationDate()); // Запись LocalDateTime
            
            stmt.executeUpdate();
        }
    }

    // READ - Поиск канала по первичному ключу (code)
    public ChannelDto findByCode(String code) throws SQLException {
        String sql = "SELECT * FROM channels WHERE code = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, code);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRowToDto(rs);
                }
            }
        }
        return null; // Канал не найден
    }

    // READ ALL WITH PAGINATION - Получение каналов с пагинацией
    public List<ChannelDto> findAll(int page, int size) throws SQLException {
        String sql = "SELECT * FROM channels LIMIT ? OFFSET ?";
        List<ChannelDto> channels = new ArrayList<>();

        // Вычисляем сдвиг: для 1-й страницы (1-1)*size = 0
        int offset = (page - 1) * size; 

        try (Connection conn = getConnection(); 
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, size);
            pstmt.setInt(2, offset);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    channels.add(mapRowToDto(rs));
                }
            }
        }
        return channels;
    }

    // UPDATE - Обновление данных канала (кроме code и creationDate)
    public void update(ChannelDto channel) throws SQLException {
        String sql = "UPDATE channels SET name = ?, description = ? WHERE code = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, channel.getName());
            stmt.setString(2, channel.getDescription());
            stmt.setString(3, channel.getCode()); // Требуется getCode() в DTO
            
            stmt.executeUpdate();
        }
    }

    // DELETE - Удаление канала по code
    public void delete(String code) throws SQLException {
        String sql = "DELETE FROM channels WHERE code = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, code);
            stmt.executeUpdate();
        }
    }

    // Вспомогательный метод для маппинга строки БД в объект DTO
    private ChannelDto mapRowToDto(ResultSet rs) throws SQLException {
        ChannelDto channel = new ChannelDto();
        channel.setCode(rs.getString("code")); // Требуется setCode() в DTO
        channel.setName(rs.getString("name"));
        channel.setDescription(rs.getString("description"));
        // Извлечение TIMESTAMP как LocalDateTime
        channel.setCreationDate(rs.getObject("creation_date", LocalDateTime.class));
        return channel;
    }
}
