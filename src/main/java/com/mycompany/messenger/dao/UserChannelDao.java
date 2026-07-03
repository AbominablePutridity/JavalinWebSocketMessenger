package com.mycompany.messenger.dao;

import static com.mycompany.messenger.dao.DbConfig.getConnection;
import com.mycompany.messenger.dto.UserChannelDto;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author User
 */
public class UserChannelDao {
 // ЦЕЛЕВОЙ ПОИСК: Найти связь по коду пользователя И коду канала
    public UserChannelDto findByUserCodeAndChannelCode(String userCode, String channelCode) throws SQLException {
        String sql = "SELECT * FROM user_channels WHERE user_code = ? AND channel_code = ?";
        try (Connection conn = DbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, userCode);
            stmt.setString(2, channelCode);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRowToDto(rs);
                }
            }
        }
        return null; // Связь не найдена
    }

    // CREATE - Создание связи (id генерируется базой данных автоматически)
    public void save(UserChannelDto userChannel) throws SQLException {
        String sql = "INSERT INTO user_channels (user_code, channel_code) VALUES (?, ?)";
        try (Connection conn = DbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            stmt.setString(1, userChannel.getUserCode());
            stmt.setString(2, userChannel.getChannelCode());
            
            stmt.executeUpdate();
            
            // Получаем сгенерированный БД id и записываем обратно в DTO
            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    userChannel.setId(generatedKeys.getLong(1));
                }
            }
        }
    }

    // READ - Поиск по первичному ключу id
    public UserChannelDto findById(long id) throws SQLException {
        String sql = "SELECT * FROM user_channels WHERE id = ?";
        try (Connection conn = DbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRowToDto(rs);
                }
            }
        }
        return null;
    }

    // READ ALL - Получение всех связей
    public List<UserChannelDto> findAll() throws SQLException {
        String sql = "SELECT * FROM user_channels";
        List<UserChannelDto> list = new ArrayList<>();
        try (Connection conn = DbConfig.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                list.add(mapRowToDto(rs));
            }
        }
        return list;
    }

    // UPDATE - Обновление кодов по id
    public void update(UserChannelDto userChannel) throws SQLException {
        String sql = "UPDATE user_channels SET user_code = ?, channel_code = ? WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, userChannel.getUserCode());
            stmt.setString(2, userChannel.getChannelCode());
            stmt.setLong(3, userChannel.getId());
            
            stmt.executeUpdate();
        }
    }

    // DELETE - Удаление связи по id
    public void delete(long id) throws SQLException {
        String sql = "DELETE FROM user_channels WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, id);
            stmt.executeUpdate();
        }
    }

    // Вспомогательный метод для маппинга строки БД в объект DTO
    private UserChannelDto mapRowToDto(ResultSet rs) throws SQLException {
        UserChannelDto userChannel = new UserChannelDto();
        userChannel.setId(rs.getLong("id"));
        userChannel.setUserCode(rs.getString("user_code"));
        userChannel.setChannelCode(rs.getString("channel_code"));
        return userChannel;
    }    
}
