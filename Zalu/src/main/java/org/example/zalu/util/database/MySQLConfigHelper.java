package org.example.zalu.util.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Helper class để cấu hình MySQL max_allowed_packet
 * Tự động set GLOBAL nếu có quyền, hoặc hiển thị hướng dẫn
 */
public class MySQLConfigHelper {
    private static final Logger logger = LoggerFactory.getLogger(MySQLConfigHelper.class);

    /**
     * Kiểm tra và cố gắng set max_allowed_packet
     * 
     * @return true nếu thành công, false nếu không có quyền
     */
    public static boolean checkAndSetMaxAllowedPacket() {
        try (Connection conn = DBConnection.getConnection();
                Statement stmt = conn.createStatement()) {

            // Kiểm tra giá trị hiện tại
            try (ResultSet rs = stmt.executeQuery("SHOW VARIABLES LIKE 'max_allowed_packet'")) {
                if (rs.next()) {
                    long currentValue = rs.getLong("Value");
                    logger.info("MySQL max_allowed_packet hiện tại: {} bytes ({} MB)", currentValue,
                            currentValue / 1024 / 1024);

                    if (currentValue < 16777216) { // < 16MB
                        logger.warn("max_allowed_packet quá nhỏ, đang thử set GLOBAL...");

                        try {
                            stmt.execute("SET GLOBAL max_allowed_packet=16777216");
                            logger.info("✓ Đã set GLOBAL max_allowed_packet=16MB thành công!");
                            logger.info("  Lưu ý: Giá trị này sẽ mất khi restart MySQL.");
                            logger.info("  Để cấu hình vĩnh viễn, xem file SETUP_MYSQL.md");
                            return true;
                        } catch (SQLException e) {
                            if (e.getMessage().contains("Access denied") || e.getMessage().contains("super")) {
                                logger.error("✗ Không có quyền set GLOBAL max_allowed_packet");
                                logger.error("  Vui lòng chạy lệnh SQL sau với quyền admin:");
                                logger.error("  SET GLOBAL max_allowed_packet=16777216;");
                                logger.error("  Hoặc xem hướng dẫn trong file SETUP_MYSQL.md");
                                return false;
                            } else {
                                throw e;
                            }
                        }
                    } else {
                        logger.info("✓ max_allowed_packet đã đủ lớn ({} MB)", currentValue / 1024 / 1024);
                        return true;
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Lỗi khi kiểm tra max_allowed_packet: {}", e.getMessage(), e);
            return false;
        }
        return false;
    }

    /**
     * Chỉ kiểm tra giá trị hiện tại, không thay đổi
     */
    public static void checkMaxAllowedPacket() {
        try (Connection conn = DBConnection.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SHOW VARIABLES LIKE 'max_allowed_packet'")) {

            if (rs.next()) {
                long currentValue = rs.getLong("Value");
                logger.info("MySQL max_allowed_packet: {} bytes ({} MB)", currentValue, currentValue / 1024 / 1024);

                if (currentValue < 16777216) {
                    logger.warn("⚠ Cảnh báo: max_allowed_packet quá nhỏ, có thể không lưu được file lớn!");
                    logger.warn("  Xem hướng dẫn trong file SETUP_MYSQL.md");
                }
            }
        } catch (SQLException e) {
            logger.error("Lỗi khi kiểm tra max_allowed_packet: {}", e.getMessage(), e);
        }
    }
}
