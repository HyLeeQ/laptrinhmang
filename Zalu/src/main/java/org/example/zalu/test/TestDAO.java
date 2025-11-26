package org.example.zalu.test;

import org.example.zalu.dao.MessageDAO;
import org.example.zalu.model.Message;
import org.example.zalu.util.database.DBConnection;
import java.sql.Connection;
import java.time.LocalDateTime;

public class TestDAO {
    public static void main(String[] args) {
        try {
            MessageDAO dao = new MessageDAO();
            byte[] testData = "Test file content for debug".getBytes();  // Byte nhỏ
            Message testMsg = new Message(0, 3, 5, null, testData, "test-debug.txt", false, LocalDateTime.now());  // content=null cho file
            boolean saved = dao.saveMessage(testMsg);
            System.out.println("Test save file: " + saved + ", ID=" + testMsg.getId());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
