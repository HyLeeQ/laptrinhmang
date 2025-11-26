package org.example.zalu.util.database;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Helper class để cấu hình MySQL max_allowed_packet
 * Tự động set GLOBAL nếu có quyền, hoặc hiển thị hướng dẫn
 */
public class MySQLConfigHelper {
    
    /**
     * Kiểm tra và cố gắng set max_allowed_packet
     * @return true nếu thành công, false nếu không có quyền
     */
    public static boolean checkAndSetMaxAllowedPacket() {
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            
            // Kiểm tra giá trị hiện tại
            try (ResultSet rs = stmt.executeQuery("SHOW VARIABLES LIKE 'max_allowed_packet'")) {
                if (rs.next()) {
                    long currentValue = rs.getLong("Value");
                    System.out.println("MySQL max_allowed_packet hiện tại: " + currentValue + " bytes (" + (currentValue / 1024 / 1024) + " MB)");
                    
                    if (currentValue < 16777216) { // < 16MB
                        System.out.println("⚠ max_allowed_packet quá nhỏ, đang thử set GLOBAL...");
                        
                        try {
                            stmt.execute("SET GLOBAL max_allowed_packet=16777216");
                            System.out.println("✓ Đã set GLOBAL max_allowed_packet=16MB thành công!");
                            System.out.println("  Lưu ý: Giá trị này sẽ mất khi restart MySQL.");
                            System.out.println("  Để cấu hình vĩnh viễn, xem file SETUP_MYSQL.md");
                            return true;
                        } catch (SQLException e) {
                            if (e.getMessage().contains("Access denied") || e.getMessage().contains("super")) {
                                System.err.println("✗ Không có quyền set GLOBAL max_allowed_packet");
                                System.err.println("  Vui lòng chạy lệnh SQL sau với quyền admin:");
                                System.err.println("  SET GLOBAL max_allowed_packet=16777216;");
                                System.err.println("  Hoặc xem hướng dẫn trong file SETUP_MYSQL.md");
                                return false;
                            } else {
                                throw e;
                            }
                        }
                    } else {
                        System.out.println("✓ max_allowed_packet đã đủ lớn (" + (currentValue / 1024 / 1024) + " MB)");
                        return true;
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Lỗi khi kiểm tra max_allowed_packet: " + e.getMessage());
            e.printStackTrace();
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
                System.out.println("MySQL max_allowed_packet: " + currentValue + " bytes (" + (currentValue / 1024 / 1024) + " MB)");
                
                if (currentValue < 16777216) {
                    System.out.println("⚠ Cảnh báo: max_allowed_packet quá nhỏ, có thể không lưu được file lớn!");
                    System.out.println("  Xem hướng dẫn trong file SETUP_MYSQL.md");
                }
            }
        } catch (SQLException e) {
            System.err.println("Lỗi khi kiểm tra max_allowed_packet: " + e.getMessage());
        }
    }
}

