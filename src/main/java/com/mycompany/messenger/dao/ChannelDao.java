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

    // ========================================================================
    // CREATE - Создание канала
    // Теперь сохраняем также owner_code — код пользователя-владельца канала
    // ========================================================================
    public void save(ChannelDto channel) throws SQLException {
        String sql = "INSERT INTO channels (code, name, description, creation_date, owner_code) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, channel.getCode());
            stmt.setString(2, channel.getName());
            stmt.setString(3, channel.getDescription());
            stmt.setObject(4, channel.getCreationDate());
            stmt.setString(5, channel.getOwnerCode()); // владелец канала

            stmt.executeUpdate();
        }
    }

    // ========================================================================
    // READ - Поиск канала по первичному ключу (code)
    // ========================================================================
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

    // ========================================================================
    // READ ALL WITH PAGINATION - Получение каналов с пагинацией
    // ========================================================================
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

    // ========================================================================
    // SEARCH - Поиск каналов по имени, в которых состоит пользователь
    // Если channelName пустой — возвращаются ВСЕ каналы пользователя
    // Если channelName непустой — поиск по частичному совпадению имени (LIKE)
    // ========================================================================
    public List<ChannelDto> findByUserCodeAndName(String userCode, String channelName) throws SQLException {
        // Определяем, нужна ли фильтрация по имени
        boolean hasNameFilter = channelName != null && !channelName.trim().isEmpty();

        // Строим динамический SQL с JOIN таблицы user_channels
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT c.* FROM channels c");
        sql.append(" INNER JOIN user_channels uc ON c.code = uc.channel_code");
        sql.append(" WHERE uc.user_code = ?");

        // Если задано имя — добавляем фильтр по названию канала (регистронезависимый поиск)
        if (hasNameFilter) {
            sql.append(" AND c.name ILIKE ?");
        }

        sql.append(" ORDER BY c.creation_date DESC");

        List<ChannelDto> channels = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            stmt.setString(1, userCode);

            // Если есть фильтр по имени — подставляем паттерн для LIKE
            if (hasNameFilter) {
                stmt.setString(2, "%" + channelName.trim() + "%");
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    channels.add(mapRowToDto(rs));
                }
            }
        }
        return channels;
    }

    // ========================================================================
    // UPDATE - Обновление данных канала (кроме code и creationDate)
    // Теперь обновляем запись ТОЛЬКО если текущий пользователь — владелец канала
    // Возвращает true, если обновление произошло, иначе false
    // ========================================================================
    public boolean update(ChannelDto channel) throws SQLException {
        String sql = "UPDATE channels SET name = ?, description = ? WHERE code = ? AND owner_code = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, channel.getName());
            stmt.setString(2, channel.getDescription());
            stmt.setString(3, channel.getCode());
            stmt.setString(4, channel.getOwnerCode()); // проверка владельца

            // executeUpdate() возвращает количество затронутых строк
            // Если 0 — значит условие WHERE не сработало (канал не наш)
            return stmt.executeUpdate() > 0;
        }
    }

    // ========================================================================
    // DELETE - Удаление канала по code, но ТОЛЬКО если пользователь — владелец
    // Возвращает true, если удаление произошло, иначе false
    // ========================================================================
    public boolean delete(String code, String ownerCode) throws SQLException {
        String sql = "DELETE FROM channels WHERE code = ? AND owner_code = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, code);
            stmt.setString(2, ownerCode); // проверка владельца

            return stmt.executeUpdate() > 0;
        }
    }

    // ========================================================================
    // Вспомогательный метод для маппинга строки БД в объект DTO
    // ========================================================================
    private ChannelDto mapRowToDto(ResultSet rs) throws SQLException {
        ChannelDto channel = new ChannelDto();
        channel.setCode(rs.getString("code"));
        channel.setName(rs.getString("name"));
        channel.setDescription(rs.getString("description"));
        channel.setCreationDate(rs.getObject("creation_date", LocalDateTime.class));
        channel.setOwnerCode(rs.getString("owner_code")); // маппинг владельца
        return channel;
    }
}
