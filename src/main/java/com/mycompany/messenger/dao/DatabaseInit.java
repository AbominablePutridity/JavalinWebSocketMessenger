package com.mycompany.messenger.dao;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseInit {

    public static void createTablesIfNotExist() throws SQLException {
        try (Connection conn = DbConfig.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS users (
                    code              VARCHAR(255) PRIMARY KEY,
                    name              VARCHAR(255) NOT NULL,
                    surname           VARCHAR(255) NOT NULL,
                    registration_date TIMESTAMP NOT NULL,
                    login             VARCHAR(255)
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS access (
                    id              BIGSERIAL PRIMARY KEY,
                    login           VARCHAR(255) NOT NULL,
                    password        VARCHAR(255) NOT NULL,
                    user_code       VARCHAR(255) REFERENCES users(code)
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS channels (
                    code            VARCHAR(255) PRIMARY KEY,
                    name            VARCHAR(255) NOT NULL,
                    description     TEXT,
                    creation_date   TIMESTAMP NOT NULL,
                    owner_code      VARCHAR(255) NOT NULL REFERENCES users(code)
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS user_channels (
                    id              BIGSERIAL PRIMARY KEY,
                    user_code       VARCHAR(255) NOT NULL REFERENCES users(code),
                    channel_code    VARCHAR(255) NOT NULL REFERENCES channels(code) ON DELETE CASCADE
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS messages (
                    id              BIGSERIAL PRIMARY KEY,
                    text            TEXT NOT NULL,
                    date_send       TIMESTAMP NOT NULL,
                    channel_id      BIGINT NOT NULL REFERENCES user_channels(id) ON DELETE CASCADE
                )
            """);

            System.out.println("[DB] Все таблицы успешно созданы/проверены");
        }
    }
}
