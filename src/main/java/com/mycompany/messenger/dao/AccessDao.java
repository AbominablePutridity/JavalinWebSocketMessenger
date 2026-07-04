package com.mycompany.messenger.dao;

import com.mycompany.messenger.dto.AccessDto;
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
public class AccessDao {
    
    // CREATE - Создание доступа с получением сгенерированного ID
    public void save(AccessDto access) throws SQLException {
        String sql = "INSERT INTO access (login, password, user_code) VALUES (?, ?, ?)";
        try (Connection conn = DbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            stmt.setString(1, access.getLogin());
            stmt.setString(2, access.getPassword());
            stmt.setString(3, access.getUserCode());
            
            stmt.executeUpdate();
            
            // Получаем сгенерированный БД ID и записываем его в DTO
            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    access.setId(generatedKeys.getLong(1));
                }
            }
        }
    }

    // READ - Поиск по первичному ключу (id)
    public AccessDto findById(long id) throws SQLException {
        String sql = "SELECT * FROM access WHERE id = ?";
        try (Connection conn = DbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRowToDto(rs);
                }
            }
        }
        return null; // Доступ не найден
    }

    // READ - Поиск по внешнему ключу (user_code)
    public AccessDto findByUserCode(String userCode) throws SQLException {
        String sql = "SELECT * FROM access WHERE user_code = ?";
        try (Connection conn = DbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, userCode);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRowToDto(rs);
                }
            }
        }
        return null; // Доступ для данного пользователя не найден
    }

    // READ ALL WITH PAGINATION - Получение доступов с пагинацией
    public List<AccessDto> findAll(int page, int size) throws SQLException {
        String sql = "SELECT * FROM access LIMIT ? OFFSET ?";
        List<AccessDto> accessList = new ArrayList<>();

        // Вычисляем сдвиг: для 1-й страницы (1-1)*size = 0
        int offset = (page - 1) * size; 

        try (Connection conn = DbConfig.getConnection(); 
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, size);
            pstmt.setInt(2, offset);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    accessList.add(mapRowToDto(rs));
                }
            }
        }
        return accessList;
    }

    // UPDATE - Обновление данных (включая user_code, если учетную запись перепривязывают)
    public void update(AccessDto access) throws SQLException {
        String sql = "UPDATE access SET login = ?, password = ?, user_code = ? WHERE id = ?";
        try (Connection conn = DbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, access.getLogin());
            stmt.setString(2, access.getPassword());
            stmt.setString(3, access.getUserCode());
            stmt.setLong(4, access.getId());
            
            stmt.executeUpdate();
        }
    }

    // DELETE - Удаление доступа по id
    public void delete(long id) throws SQLException {
        String sql = "DELETE FROM access WHERE id = ?";
        try (Connection conn = DbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, id);
            stmt.executeUpdate();
        }
    }

    // Вспомогательный метод для маппинга строки БД в объект DTO
    private AccessDto mapRowToDto(ResultSet rs) throws SQLException {
        AccessDto access = new AccessDto();
        access.setId(rs.getLong("id"));
        access.setLogin(rs.getString("login"));
        access.setPassword(rs.getString("password"));
        access.setUserCode(rs.getString("user_code"));
        return access;
    }
}
