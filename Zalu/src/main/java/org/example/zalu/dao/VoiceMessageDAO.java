package org.example.zalu.dao;

import org.example.zalu.model.VoiceMessage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class VoiceMessageDAO {
    private Connection connection;

    public VoiceMessageDAO(Connection connection) {
        this.connection = connection;
    }

    public boolean saveVoiceMessage(VoiceMessage voiceMessage) throws SQLException {
        String sql = "INSERT INTO voice_messages (sender_id, receiver_id, file_path, created_at, is_read) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, voiceMessage.getSenderId());
            pstmt.setInt(2, voiceMessage.getReceiverId());
            pstmt.setString(3, voiceMessage.getFilePath());
            pstmt.setObject(4, voiceMessage.getCreatedAt());
            pstmt.setBoolean(5, voiceMessage.getIsRead());
            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;
        }
    }

    public List<VoiceMessage> getVoiceMessagesByUserAndFriend(int userId, int friendId) throws SQLException {
        List<VoiceMessage> voiceMessages = new ArrayList<>();
        String sql = "SELECT * FROM voice_messages WHERE (sender_id = ? AND receiver_id = ?) OR (sender_id = ? AND receiver_id = ?) ORDER BY created_at ASC";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            pstmt.setInt(2, friendId);
            pstmt.setInt(3, friendId);
            pstmt.setInt(4, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    VoiceMessage voiceMessage = new VoiceMessage(
                            rs.getInt("id"),
                            rs.getInt("sender_id"),
                            rs.getInt("receiver_id"),
                            rs.getString("file_path"),
                            rs.getTimestamp("created_at").toLocalDateTime(),
                            rs.getBoolean("is_read")
                    );
                    voiceMessages.add(voiceMessage);
                }
            }
        }
        return voiceMessages;
    }

    // Lấy voice message theo ID
    public VoiceMessage findVoiceMessageById(int id) throws SQLException {
        String sql = "SELECT * FROM voice_messages WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new VoiceMessage(
                            rs.getInt("id"),
                            rs.getInt("sender_id"),
                            rs.getInt("receiver_id"),
                            rs.getString("file_path"),
                            rs.getTimestamp("created_at").toLocalDateTime(),
                            rs.getBoolean("is_read")
                    );
                }
            }
        }
        return null;
    }

    // Cập nhật trạng thái đã đọc
    public boolean markVoiceAsRead(int messageId) throws SQLException {
        String sql = "UPDATE voice_messages SET is_read = TRUE WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, messageId);
            return pstmt.executeUpdate() > 0;
        }
    }
}