package com.mycompany.messenger.dao;

import static com.mycompany.messenger.dao.DbConfig.getConnection;
import com.mycompany.messenger.dto.ChannelDto;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ChannelDao {

    public void save(ChannelDto channel) throws SQLException {
        String sql = "INSERT INTO channels (code, name, description, creation_date, owner_code) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DbConfig.getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, channel.getCode());
            stmt.setString(2, channel.getName());
            stmt.setString(3, channel.getDescription());
            stmt.setObject(4, channel.getCreationDate());
            stmt.setString(5, channel.getOwnerCode());
            stmt.executeUpdate();
        }
    }

    public ChannelDto findByCode(String code) throws SQLException {
        String sql = "SELECT * FROM channels WHERE code = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, code);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return mapRowToDto(rs);
            }
        }
        return null;
    }

    public List<ChannelDto> findAll(int page, int size) throws SQLException {
        String sql = "SELECT * FROM channels LIMIT ? OFFSET ?";
        List<ChannelDto> channels = new ArrayList<>();
        int offset = (page - 1) * size;
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, size);
            pstmt.setInt(2, offset);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) channels.add(mapRowToDto(rs));
            }
        }
        return channels;
    }

    public List<ChannelDto> findByUserCodeAndName(String userCode, String channelName) throws SQLException {
        return findByUserCodeAndName(userCode, channelName, 1, 1000);
    }

    public List<ChannelDto> findByUserCodeAndName(String userCode, String channelName, int page, int size) throws SQLException {
        boolean hasNameFilter = channelName != null && !channelName.trim().isEmpty();
        int offset = (page - 1) * size;
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT c.* FROM channels c");
        sql.append(" INNER JOIN user_channels uc ON c.code = uc.channel_code");
        sql.append(" WHERE uc.user_code = ?");
        if (hasNameFilter) sql.append(" AND c.name ILIKE ?");
        sql.append(" ORDER BY c.creation_date DESC");
        sql.append(" LIMIT ? OFFSET ?");

        List<ChannelDto> channels = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            int idx = 1;
            stmt.setString(idx++, userCode);
            if (hasNameFilter) stmt.setString(idx++, "%" + channelName.trim() + "%");
            stmt.setInt(idx++, size);
            stmt.setInt(idx, offset);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) channels.add(mapRowToDto(rs));
            }
        }
        return channels;
    }

    public boolean update(ChannelDto channel) throws SQLException {
        String sql = "UPDATE channels SET name = ?, description = ? WHERE code = ? AND owner_code = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, channel.getName());
            stmt.setString(2, channel.getDescription());
            stmt.setString(3, channel.getCode());
            stmt.setString(4, channel.getOwnerCode());
            return stmt.executeUpdate() > 0;
        }
    }

    public boolean delete(String code, String ownerCode) throws SQLException {
        String sql = "DELETE FROM channels WHERE code = ? AND owner_code = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, code);
            stmt.setString(2, ownerCode);
            return stmt.executeUpdate() > 0;
        }
    }

    private ChannelDto mapRowToDto(ResultSet rs) throws SQLException {
        ChannelDto channel = new ChannelDto();
        channel.setCode(rs.getString("code"));
        channel.setName(rs.getString("name"));
        channel.setDescription(rs.getString("description"));
        channel.setCreationDate(rs.getObject("creation_date", LocalDateTime.class));
        channel.setOwnerCode(rs.getString("owner_code"));
        return channel;
    }
}
