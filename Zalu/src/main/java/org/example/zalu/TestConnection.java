package org.example.zalu;

import org.example.zalu.model.DBConnection;
import java.sql.Connection;

public class TestConnection {
    public static void main(String[] args) {
        try (Connection conn = DBConnection.getConnection()) {
            System.out.println("✅ Kết nối MySQL thành công!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
