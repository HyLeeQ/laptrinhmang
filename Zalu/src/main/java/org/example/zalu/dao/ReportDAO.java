package org.example.zalu.dao;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.example.zalu.exception.database.DatabaseConnectionException;
import org.example.zalu.exception.database.DatabaseException;
import org.example.zalu.model.UserReport;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ReportDAO {
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
            System.err.println("Lỗi khởi tạo pool kết nối cho ReportDAO: " + e.getMessage());
        }
    }

    private Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("DataSource chưa được khởi tạo");
        }
        return dataSource.getConnection();
    }

    public boolean createReport(UserReport report) throws DatabaseException, DatabaseConnectionException {
        String sql = "INSERT INTO user_reports (reporter_id, reported_user_id, reason, description) VALUES (?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
             
            ps.setInt(1, report.getReporterId());
            ps.setInt(2, report.getReportedUserId());
            ps.setString(3, report.getReason());
            ps.setString(4, report.getDescription());
            
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            if (e.getMessage() != null && (e.getMessage().toLowerCase().contains("connection") || 
                e.getMessage().toLowerCase().contains("timeout"))) {
                throw new DatabaseConnectionException("Lỗi kết nối CSDL", e);
            }
            throw new DatabaseException("Lỗi tạo báo cáo", e);
        }
    }
    
    public List<UserReport> getAllReports() throws DatabaseException, DatabaseConnectionException {
        List<UserReport> reports = new ArrayList<>();
        String sql = "SELECT r.*, u1.username AS reporter_name, u2.username AS reported_name " +
                     "FROM user_reports r " +
                     "JOIN users u1 ON r.reporter_id = u1.id " +
                     "JOIN users u2 ON r.reported_user_id = u2.id " +
                     "ORDER BY r.created_at DESC";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
             
            while (rs.next()) {
                UserReport r = new UserReport();
                r.setId(rs.getInt("id"));
                r.setReporterId(rs.getInt("reporter_id"));
                r.setReportedUserId(rs.getInt("reported_user_id"));
                r.setReason(rs.getString("reason"));
                r.setDescription(rs.getString("description"));
                r.setStatus(rs.getString("status"));
                if (rs.getTimestamp("created_at") != null) {
                    r.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                }
                r.setReporterName(rs.getString("reporter_name"));
                r.setReportedName(rs.getString("reported_name"));
                reports.add(r);
            }
        } catch (SQLException e) {
            if (e.getMessage() != null && (e.getMessage().toLowerCase().contains("connection") || 
                e.getMessage().toLowerCase().contains("timeout"))) {
                throw new DatabaseConnectionException("Lỗi kết nối CSDL", e);
            }
            throw new DatabaseException("Lỗi lấy danh sách báo cáo", e);
        }
        return reports;
    }
    
    public boolean updateReportStatus(int reportId, String status) throws DatabaseException, DatabaseConnectionException {
        String sql = "UPDATE user_reports SET status = ? WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, reportId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            if (e.getMessage() != null && (e.getMessage().toLowerCase().contains("connection") || 
                e.getMessage().toLowerCase().contains("timeout"))) {
                throw new DatabaseConnectionException("Lỗi kết nối CSDL", e);
            }
            throw new DatabaseException("Lỗi cập nhật trạng thái báo cáo", e);
        }
    }
}
