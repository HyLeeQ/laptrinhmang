package org.example.zalu.model.dao;

import org.example.zalu.model.Message;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

public class MessageDAO {
    private Connection connection;

    public MessageDAO(Connection connection) {
        this.connection = connection;
    }

    public boolean saveMessage(Message message) throws SQLException {
        String sql = "INSERT INTO messages (sender_id, receiver_id, content, created_at) VALUES (?, ?, ?, ?)"; // Sửa thành created_at
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, message.getSenderId());
            pstmt.setInt(2, message.getReceiverId());
            pstmt.setString(3, message.getContent());
            pstmt.setObject(4, message.getCreatedAt()); // Sử dụng LocalDateTime
            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;
        }
    }

    public Message findMessageById(int id) throws SQLException {
        String sql = "SELECT * FROM messages WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new Message(
                            rs.getInt("id"),
                            rs.getInt("sender_id"),
                            rs.getInt("receiver_id"),
                            rs.getString("content"),
                            rs.getObject("created_at", LocalDateTime.class) // Sửa thành created_at
                    );
                }
            }
        }
        return null;
    }
}