package org.example.zalu.util.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

/**
 * DBConnection với HikariCP Pool để tránh connection timeout/failure.
 * Singleton pattern cho pool.
 */
public class DBConnection {
    private static HikariDataSource dataSource;

    // Private constructor để singleton
    private DBConnection() {}

    /**
     * Khởi tạo pool nếu chưa có (lazy init).
     * Đọc cấu hình từ database.properties, fallback về giá trị mặc định nếu không có file.
     */
    private static synchronized HikariDataSource getDataSource() {
        if (dataSource == null) {
            HikariConfig config = new HikariConfig();
            
            // Đọc cấu hình từ database.properties
            Properties props = new Properties();
            String jdbcUrl = "jdbc:mysql://localhost:3306/laptrinhmang_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true&connectTimeout=30000&socketTimeout=60000&autoReconnect=true&maxAllowedPacket=16777216";
            String username = "root";
            String password = "";
            String driver = "com.mysql.cj.jdbc.Driver";
            
            try {
                InputStream is = DBConnection.class.getClassLoader()
                        .getResourceAsStream("database.properties");
                if (is != null) {
                    props.load(is);
                    jdbcUrl = props.getProperty("db.url", jdbcUrl);
                    username = props.getProperty("db.username", username);
                    password = props.getProperty("db.password", password);
                    driver = props.getProperty("db.driver", driver);
                    is.close();
                    System.out.println("✓ Đã đọc cấu hình database từ database.properties");
                } else {
                    System.out.println("⚠ Không tìm thấy database.properties, sử dụng giá trị mặc định");
                }
            } catch (Exception e) {
                System.err.println("⚠ Lỗi đọc database.properties: " + e.getMessage() + ", sử dụng giá trị mặc định");
            }
            
            config.setJdbcUrl(jdbcUrl);
            // Lưu ý: maxAllowedPacket trong JDBC URL chỉ là hint, cần cấu hình MySQL server:
            // SET GLOBAL max_allowed_packet=16777216; (16MB)
            // Hoặc thêm vào my.ini/my.cnf: max_allowed_packet=16M
            config.setUsername(username);
            config.setPassword(password);
            config.setDriverClassName(driver);

            // Pool settings (tối ưu cho app chat nhỏ)
            config.setMaximumPoolSize(10);  // Max 10 connections đồng thời
            config.setMinimumIdle(2);  // Giữ 2 idle connections
            config.setConnectionTimeout(30000);  // 30s timeout khi get connection
            config.setIdleTimeout(600000);  // 10 phút idle trước khi close
            config.setMaxLifetime(1800000);  // 30 phút lifetime mỗi connection
            // Optimize cho MySQL
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

            dataSource = new HikariDataSource(config);
            System.out.println("HikariCP DB Pool initialized successfully in DBConnection");
            System.out.println("Note: maxAllowedPacket in JDBC URL is set to 16MB (hint for client)");
            System.out.println("IMPORTANT: MySQL server must have max_allowed_packet >= 16MB");
            System.out.println("  To set: SET GLOBAL max_allowed_packet=16777216; (requires admin)");
            System.out.println("  Or add to my.ini/my.cnf: max_allowed_packet=16M");
        }
        return dataSource;
    }

    /**
     * Get connection từ pool (thay thế DriverManager cũ).
     * @return Connection mới từ pool.
     * @throws SQLException nếu pool fail.
     */
    public static Connection getConnection() throws SQLException {
        return getDataSource().getConnection();
    }

    /**
     * Close pool khi app shutdown (gọi ở ChatServer hoặc ZaluApplication).
     * LƯU Ý: KHÔNG gọi method này khi logout, chỉ gọi khi app tắt hoàn toàn!
     */
    public static void closePool() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            dataSource = null;  // Reset để có thể tạo pool mới nếu cần
            System.out.println("HikariCP DB Pool closed in DBConnection");
        }
    }
    
    /**
     * Kiểm tra xem pool có đang hoạt động không
     */
    public static boolean isPoolActive() {
        return dataSource != null && !dataSource.isClosed();
    }

    /**
     * Test connection (cho TestConnection.java).
     * @return true nếu connect OK.
     */
    public static boolean testConnection() {
        try (Connection conn = getConnection()) {
            System.out.println("DB Connection test: SUCCESS - " + conn.getCatalog());
            return true;
        } catch (SQLException e) {
            System.err.println("DB Connection test: FAIL - " + e.getMessage());
            return false;
        }
    }
}

