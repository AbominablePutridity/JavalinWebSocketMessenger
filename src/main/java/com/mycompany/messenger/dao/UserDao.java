package com.mycompany.messenger.dao;

import com.mycompany.messenger.dto.UserDto;
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
public class UserDao {
    // CREATE - Создание пользователя
    public void save(UserDto user) throws SQLException {
        String sql = "INSERT INTO users (code, name, surname, registration_date, login) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, user.getCode());
            stmt.setString(2, user.getName());
            stmt.setString(3, user.getSurname());
            // Конвертация LocalDateTime для JDBC
            stmt.setObject(4, user.getRegistrationDate());
            
            stmt.executeUpdate();
        }
    }

    // READ - Поиск по первичному ключу (code)
    public UserDto findByCode(String code) throws SQLException {
        String sql = "SELECT * FROM users WHERE code = ?";
        try (Connection conn = DbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, code);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRowToDto(rs);
                }
            }
        }
        return null; // Пользователь не найден
    }

    // READ ALL WITH PAGINATION - Получение пользователей с пагинацией
    public List<UserDto> findAll(int page, int size) throws SQLException {
        String sql = "SELECT * FROM users LIMIT ? OFFSET ?";
        List<UserDto> users = new ArrayList<>();

        // Вычисляем сдвиг: для 1-й страницы (1-1)*size = 0
        int offset = (page - 1) * size; 

        try (Connection conn = DbConfig.getConnection(); 
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, size);
            pstmt.setInt(2, offset);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    users.add(mapRowToDto(rs));
                }
            }
        }
        return users;
    }

    // UPDATE - Обновление данных (кроме code и registrationDate)
    public void update(UserDto user) throws SQLException {
        String sql = "UPDATE users SET name = ?, surname = ?, login = ? WHERE code = ?";
        try (Connection conn = DbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, user.getName());
            stmt.setString(2, user.getSurname());
            stmt.setString(4, user.getCode());
            
            stmt.executeUpdate();
        }
    }

    // DELETE - Удаление пользователя по code
    public void delete(String code) throws SQLException {
        String sql = "DELETE FROM users WHERE code = ?";
        try (Connection conn = DbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, code);
            stmt.executeUpdate();
        }
    }

    // Вспомогательный метод для маппинга строки БД в объект DTO
    private UserDto mapRowToDto(ResultSet rs) throws SQLException {
        UserDto user = new UserDto();
        user.setCode(rs.getString("code"));
        user.setName(rs.getString("name"));
        user.setSurname(rs.getString("surname"));
        // Извлечение TIMESTAMP как LocalDateTime
        user.setRegistrationDate(rs.getObject("registration_date", LocalDateTime.class));
        return user;
    }
}
