package org.example.zalu.dao;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.example.zalu.exception.database.DatabaseConnectionException;
import org.example.zalu.exception.database.DatabaseException;
import org.example.zalu.model.UserActivity;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class UserActivityDAO {
    private static HikariDataSource dataSource;

    static {
        try {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:mysql://localhost:3306/laptrinhmang_db?useUnicode=true&characterEncoding=UTF-8&useSSL=false&allowPublicKeyRetrieval=true");
            config.setUsername("root");
            config.setPassword("");
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setIdleTimeout(300000);
            config.setConnectionTimeout(20000);
            
            dataSource = new HikariDataSource(config);
        } catch (Exception e) {
            System.err.println("Lỗi khởi tạo pool kết nối cho UserActivityDAO: " + e.getMessage());
        }
    }

    private Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("DataSource chưa được khởi tạo");
        }
        return dataSource.getConnection();
    }

    public void logActivity(UserActivity activity) throws DatabaseException, DatabaseConnectionException {
        String sql = "INSERT INTO user_activity_logs (user_id, username, activity_type, target_user_id, group_id, encrypted_content, status, created_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
             
            ps.setInt(1, activity.getUserId());
            ps.setString(2, activity.getUsername());
            ps.setString(3, activity.getActivityType());
            
            if (activity.getTargetUserId() > 0) {
                ps.setInt(4, activity.getTargetUserId());
            } else {
                ps.setNull(4, java.sql.Types.INTEGER);
            }
            
            if (activity.getGroupId() > 0) {
                ps.setInt(5, activity.getGroupId());
            } else {
                ps.setNull(5, java.sql.Types.INTEGER);
            }
            
            ps.setString(6, activity.getEncryptedContent());
            ps.setString(7, activity.getStatus());
            
            if (activity.getTimestamp() != null) {
                ps.setTimestamp(8, java.sql.Timestamp.valueOf(activity.getTimestamp()));
            } else {
                ps.setTimestamp(8, new java.sql.Timestamp(System.currentTimeMillis()));
            }
            
            ps.executeUpdate();
        } catch (SQLException e) {
            if (e.getMessage() != null && (e.getMessage().toLowerCase().contains("connection") || 
                e.getMessage().toLowerCase().contains("timeout"))) {
                throw new DatabaseConnectionException("Lỗi kết nối CSDL khi ghi log", e);
            }
            throw new DatabaseException("Lỗi ghi user activity log", e);
        }
    }
}
