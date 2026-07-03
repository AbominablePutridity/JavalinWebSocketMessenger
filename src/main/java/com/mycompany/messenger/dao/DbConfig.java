package com.mycompany.messenger.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 *
 * @author User
 */
public class DbConfig {
    // Метод получения соединения (замените на ваш ConnectionPool или DataSource)
    public static Connection getConnection() throws SQLException {
        String url = "jdbc:postgresql://localhost:5432/messenger_db";
        String user = "postgres";
        String password = "root";
        return DriverManager.getConnection(url, user, password);
    }
}
