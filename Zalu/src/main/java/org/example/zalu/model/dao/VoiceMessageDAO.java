package org.example.zalu.model.dao;

import org.example.zalu.model.VoiceMessage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

public class VoiceMessageDAO {
    private Connection connection;

    public VoiceMessageDAO (Connection connection){
        this.connection = connection;
    }

    public boolean saveVoiceMessage(VoiceMessage voiceMessage) throws SQLException{
        String sql = "INSERT INTO voice_messages (sender_id, receiver_id, file_path, created_at) VALUES (?, ?, ?, ?)";
        try(PreparedStatement pstmt = connection.prepareStatement(sql)){
            pstmt.setInt(1, voiceMessage.getSenderId());
            pstmt.setInt(2, voiceMessage.getReceiverId());
            pstmt.setString(3, voiceMessage.getFilePath());
            pstmt.setObject(4, voiceMessage.getCreatedAt());
            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;
        }
    }

    public VoiceMessage findVoiceMessageById(int id) throws SQLException{
            String sql = "SELECT * FROM voice_messages WHERE id = ?";
            try(PreparedStatement pstmt = connection.prepareStatement(sql)){
                pstmt.setInt(1, id);
                try(ResultSet rs = pstmt.executeQuery()){
                    if(rs.next()) {
                        return new VoiceMessage(
                                rs.getInt("id"),
                                rs.getInt("sender_id"),
                                rs.getInt("receiver_id"),
                                rs.getString("file_path"),
                                rs.getObject("created_at", LocalDateTime.class)
                        );
                    }
                }
            }
        return null;
    }
}
