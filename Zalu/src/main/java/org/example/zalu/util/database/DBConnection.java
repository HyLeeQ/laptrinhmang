package org.example.zalu.util.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
            
            // #region agent log
            try {
                String logPath = "d:\\Java\\LTM\\Zalu\\.cursor\\debug.log";
                String defaultHost = extractHostFromJdbcUrl(jdbcUrl);
                Files.write(Paths.get(logPath), (String.format("{\"id\":\"log_%d_A\",\"timestamp\":%d,\"location\":\"DBConnection.java:31\",\"message\":\"Default JDBC URL before reading properties\",\"data\":{\"jdbcUrl\":\"%s\",\"host\":\"%s\"},\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"A\"}\n", System.currentTimeMillis(), System.currentTimeMillis(), jdbcUrl, defaultHost)).getBytes(), java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            } catch (Exception e) {}
            // #endregion
            
            try {
                InputStream is = DBConnection.class.getClassLoader()
                        .getResourceAsStream("database.properties");
                if (is != null) {
                    props.load(is);
                    String originalUrl = jdbcUrl;
                    jdbcUrl = props.getProperty("db.url", jdbcUrl);
                    username = props.getProperty("db.username", username);
                    password = props.getProperty("db.password", password);
                    driver = props.getProperty("db.driver", driver);
                    is.close();
                    System.out.println("✓ Đã đọc cấu hình database từ database.properties");
                    
                    // #region agent log
                    try {
                        String logPath = "d:\\Java\\LTM\\Zalu\\.cursor\\debug.log";
                        String finalHost = extractHostFromJdbcUrl(jdbcUrl);
                        boolean urlChanged = !originalUrl.equals(jdbcUrl);
                        Files.write(Paths.get(logPath), (String.format("{\"id\":\"log_%d_B\",\"timestamp\":%d,\"location\":\"DBConnection.java:47\",\"message\":\"Properties loaded successfully\",\"data\":{\"urlChanged\":%s,\"finalJdbcUrl\":\"%s\",\"finalHost\":\"%s\",\"username\":\"%s\"},\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"B\"}\n", System.currentTimeMillis(), System.currentTimeMillis(), urlChanged, jdbcUrl, finalHost, username)).getBytes(), java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
                    } catch (Exception e) {}
                    // #endregion
                } else {
                    System.out.println("⚠ Không tìm thấy database.properties, sử dụng giá trị mặc định");
                    
                    // #region agent log
                    try {
                        String logPath = "d:\\Java\\LTM\\Zalu\\.cursor\\debug.log";
                        String defaultHost = extractHostFromJdbcUrl(jdbcUrl);
                        Files.write(Paths.get(logPath), (String.format("{\"id\":\"log_%d_B\",\"timestamp\":%d,\"location\":\"DBConnection.java:49\",\"message\":\"Properties file not found, using defaults\",\"data\":{\"jdbcUrl\":\"%s\",\"host\":\"%s\"},\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"B\"}\n", System.currentTimeMillis(), System.currentTimeMillis(), jdbcUrl, defaultHost)).getBytes(), java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
                    } catch (Exception e) {}
                    // #endregion
                }
            } catch (Exception e) {
                System.err.println("⚠ Lỗi đọc database.properties: " + e.getMessage() + ", sử dụng giá trị mặc định");
                
                // #region agent log
                try {
                    String logPath = "d:\\Java\\LTM\\Zalu\\.cursor\\debug.log";
                    Files.write(Paths.get(logPath), (String.format("{\"id\":\"log_%d_B\",\"timestamp\":%d,\"location\":\"DBConnection.java:52\",\"message\":\"Error reading properties file\",\"data\":{\"error\":\"%s\",\"jdbcUrl\":\"%s\"},\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"B\"}\n", System.currentTimeMillis(), System.currentTimeMillis(), e.getMessage(), jdbcUrl)).getBytes(), java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
                } catch (Exception ex) {}
                // #endregion
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

            // #region agent log
            try {
                String logPath = "d:\\Java\\LTM\\Zalu\\.cursor\\debug.log";
                String finalHost = extractHostFromJdbcUrl(jdbcUrl);
                int port = extractPortFromJdbcUrl(jdbcUrl);
                Files.write(Paths.get(logPath), (String.format("{\"id\":\"log_%d_C\",\"timestamp\":%d,\"location\":\"DBConnection.java:73\",\"message\":\"Creating HikariDataSource with final config\",\"data\":{\"jdbcUrl\":\"%s\",\"host\":\"%s\",\"port\":%d,\"username\":\"%s\",\"isLocalhost\":%s},\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"C\"}\n", System.currentTimeMillis(), System.currentTimeMillis(), jdbcUrl, finalHost, port, username, "localhost".equals(finalHost) || "127.0.0.1".equals(finalHost))).getBytes(), java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            } catch (Exception e) {}
            // #endregion

            dataSource = new HikariDataSource(config);
            
            // #region agent log
            try {
                String logPath = "d:\\Java\\LTM\\Zalu\\.cursor\\debug.log";
                String finalHost = extractHostFromJdbcUrl(jdbcUrl);
                Files.write(Paths.get(logPath), (String.format("{\"id\":\"log_%d_F\",\"timestamp\":%d,\"location\":\"DBConnection.java:119\",\"message\":\"HikariDataSource created\",\"data\":{\"host\":\"%s\",\"maxPoolSize\":10,\"minIdle\":2,\"canSupportMultipleClients\":true},\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"F\"}\n", System.currentTimeMillis(), System.currentTimeMillis(), finalHost)).getBytes(), java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            } catch (Exception e) {}
            // #endregion
            
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
        // #region agent log
        try {
            String logPath = "d:\\Java\\LTM\\Zalu\\.cursor\\debug.log";
            String currentUrl = dataSource != null ? dataSource.getJdbcUrl() : "not initialized";
            String host = extractHostFromJdbcUrl(currentUrl);
            int activeConnections = dataSource != null ? dataSource.getHikariPoolMXBean().getActiveConnections() : -1;
            int idleConnections = dataSource != null ? dataSource.getHikariPoolMXBean().getIdleConnections() : -1;
            int totalConnections = dataSource != null ? dataSource.getHikariPoolMXBean().getTotalConnections() : -1;
            int threadsWaiting = dataSource != null ? dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection() : -1;
            Files.write(Paths.get(logPath), (String.format("{\"id\":\"log_%d_E\",\"timestamp\":%d,\"location\":\"DBConnection.java:134\",\"message\":\"Attempting to get connection - pool stats\",\"data\":{\"host\":\"%s\",\"activeConnections\":%d,\"idleConnections\":%d,\"totalConnections\":%d,\"threadsWaiting\":%d,\"threadName\":\"%s\"},\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"E\"}\n", System.currentTimeMillis(), System.currentTimeMillis(), host, activeConnections, idleConnections, totalConnections, threadsWaiting, Thread.currentThread().getName())).getBytes(), java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception e) {}
        // #endregion
        
        Connection conn = getDataSource().getConnection();
        
        // #region agent log
        try {
            String logPath = "d:\\Java\\LTM\\Zalu\\.cursor\\debug.log";
            String catalog = conn.getCatalog();
            String metaUrl = conn.getMetaData().getURL();
            String metaHost = extractHostFromJdbcUrl(metaUrl);
            int activeAfter = dataSource != null ? dataSource.getHikariPoolMXBean().getActiveConnections() : -1;
            int idleAfter = dataSource != null ? dataSource.getHikariPoolMXBean().getIdleConnections() : -1;
            Files.write(Paths.get(logPath), (String.format("{\"id\":\"log_%d_E\",\"timestamp\":%d,\"location\":\"DBConnection.java:147\",\"message\":\"Connection obtained - pool stats after\",\"data\":{\"catalog\":\"%s\",\"host\":\"%s\",\"activeConnections\":%d,\"idleConnections\":%d,\"threadName\":\"%s\"},\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"E\"}\n", System.currentTimeMillis(), System.currentTimeMillis(), catalog, metaHost, activeAfter, idleAfter, Thread.currentThread().getName())).getBytes(), java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception e) {}
        // #endregion
        
        return conn;
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
            
            // #region agent log
            try {
                String logPath = "d:\\Java\\LTM\\Zalu\\.cursor\\debug.log";
                String metaUrl = conn.getMetaData().getURL();
                String host = extractHostFromJdbcUrl(metaUrl);
                Files.write(Paths.get(logPath), (String.format("{\"id\":\"log_%d_D\",\"timestamp\":%d,\"location\":\"DBConnection.java:123\",\"message\":\"Connection test successful\",\"data\":{\"catalog\":\"%s\",\"host\":\"%s\",\"canConnectFromRemote\":%s},\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"D\"}\n", System.currentTimeMillis(), System.currentTimeMillis(), conn.getCatalog(), host, !"localhost".equals(host) && !"127.0.0.1".equals(host))).getBytes(), java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            } catch (Exception e) {}
            // #endregion
            
            return true;
        } catch (SQLException e) {
            System.err.println("DB Connection test: FAIL - " + e.getMessage());
            
            // #region agent log
            try {
                String logPath = "d:\\Java\\LTM\\Zalu\\.cursor\\debug.log";
                String errorMsg = e.getMessage();
                boolean isConnectionRefused = errorMsg != null && (errorMsg.contains("Connection refused") || errorMsg.contains("Communications link failure"));
                Files.write(Paths.get(logPath), (String.format("{\"id\":\"log_%d_D\",\"timestamp\":%d,\"location\":\"DBConnection.java:130\",\"message\":\"Connection test failed\",\"data\":{\"error\":\"%s\",\"isConnectionRefused\":%s,\"possibleRemoteIssue\":%s},\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"D\"}\n", System.currentTimeMillis(), System.currentTimeMillis(), errorMsg, isConnectionRefused, isConnectionRefused)).getBytes(), java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            } catch (Exception ex) {}
            // #endregion
            
            return false;
        }
    }
    
    /**
     * Extract host từ JDBC URL để kiểm tra
     */
    private static String extractHostFromJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:mysql://")) {
            return "unknown";
        }
        try {
            Pattern pattern = Pattern.compile("jdbc:mysql://([^:/]+)");
            Matcher matcher = pattern.matcher(jdbcUrl);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (Exception e) {}
        return "unknown";
    }
    
    /**
     * Extract port từ JDBC URL
     */
    private static int extractPortFromJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:mysql://")) {
            return 3306;
        }
        try {
            Pattern pattern = Pattern.compile("jdbc:mysql://[^:]+:(\\d+)");
            Matcher matcher = pattern.matcher(jdbcUrl);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        } catch (Exception e) {}
        return 3306;
    }
}

