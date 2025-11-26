package org.example.zalu.test;

import org.example.zalu.util.database.DBConnection;
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
