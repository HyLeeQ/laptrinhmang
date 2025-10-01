package org.example.zalu.dao;

import org.example.zalu.model.User;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class UserDAO {
    private Connection connection;

    public UserDAO(Connection connection) {
        this.connection = connection;
        if (this.connection == null) {
            throw new IllegalStateException("Database connection is null");
        }
    }

    public boolean register(User user) throws SQLException {
        if (connection == null) throw new SQLException("No database connection");
        String hashedPassword = BCrypt.hashpw(user.getPassword(), BCrypt.gensalt());
        String sql = "INSERT INTO users (username, password, email, status) VALUES (?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, user.getUsername());
            stmt.setString(2, hashedPassword); // Lưu hashed password
            stmt.setString(3, user.getEmail());
            stmt.setString(4, user.getStatus());
            return stmt.executeUpdate() > 0;
        }
    }

    public User login(String username, String password) throws SQLException {
        if (connection == null) throw new SQLException("No database connection");
        String sql = "SELECT * FROM users WHERE username = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String hashedPassword = rs.getString("password");
                    try {
                        if (BCrypt.checkpw(password, hashedPassword)) {
                            return new User(
                                    rs.getInt("id"),
                                    rs.getString("username"),
                                    hashedPassword,
                                    rs.getString("email"),
                                    rs.getString("status")
                            );
                        }
                    } catch (IllegalArgumentException e) {
                        System.out.println("Invalid hashed password in database: " + e.getMessage());
                    }
                }
            }
        }
        return null;
    }

    public User getUserById(int id) throws SQLException {
        if (connection == null) throw new SQLException("No database connection");
        String sql = "SELECT * FROM users WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new User(
                            rs.getInt("id"),
                            rs.getString("username"),
                            rs.getString("password"),
                            rs.getString("email"),
                            rs.getString("status")
                    );
                }
            }
        }
        return null;
    }

    public boolean updateUser(User user) throws SQLException {
        if (connection == null) throw new SQLException("No database connection");
        String sql = "UPDATE users SET username = ?, password = ?, email = ?, status = ? WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, user.getUsername());
            stmt.setString(2, BCrypt.hashpw(user.getPassword(), BCrypt.gensalt())); // Hash lại nếu cập nhật password
            stmt.setString(3, user.getEmail());
            stmt.setString(4, user.getStatus());
            stmt.setInt(5, user.getId());
            return stmt.executeUpdate() > 0;
        }
    }

    public boolean updateStatus(int userId, String status) throws SQLException {
        if (connection == null) throw new SQLException("No database connection");
        String sql = "UPDATE users SET status = ? WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, status);
            stmt.setInt(2, userId);
            return stmt.executeUpdate() > 0;
        }
    }

    // Tìm kiếm người dùng theo username hoặc email
    public List<User> searchUsers(String query) throws SQLException {
        List<User> users = new ArrayList<>();
        String sql = "SELECT * FROM users WHERE username LIKE ? OR email LIKE ? AND id != ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            String searchPattern = "%" + query + "%";
            stmt.setString(1, searchPattern);
            stmt.setString(2, searchPattern);
            stmt.setInt(3, 0); // Thay bằng currentUserId nếu muốn loại trừ chính mình
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    users.add(new User(
                            rs.getInt("id"),
                            rs.getString("username"),
                            rs.getString("password"),
                            rs.getString("email"),
                            rs.getString("status")
                    ));
                }
            }
        }
        return users;
    }
}
