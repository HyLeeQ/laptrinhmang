package org.example.zalu.dao;

import org.example.zalu.model.Message;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class MessageDAO {
    private Connection connection;

    public MessageDAO(Connection connection) {
        this.connection = connection;
        if (this.connection == null) {
            throw new IllegalStateException("Database connection is null");
        }
    }

    public boolean saveMessage(Message message) throws SQLException {
        if (connection == null) throw new SQLException("No database connection");
        // Kiểm tra quan hệ bạn bè
        String checkSql = "SELECT COUNT(*) FROM friends WHERE (user_id = ? AND friend_id = ?) AND status = 'accepted'";
        try (PreparedStatement checkStmt = connection.prepareStatement(checkSql)) {
            checkStmt.setInt(1, message.getSenderId());
            checkStmt.setInt(2, message.getReceiverId());
            try (ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) == 0) {
                    throw new SQLException("Cannot send message: Users are not accepted friends");
                }
            }
        }

        String sql = "INSERT INTO messages (sender_id, receiver_id, content, created_at, is_read) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, message.getSenderId());
            pstmt.setInt(2, message.getReceiverId());
            pstmt.setString(3, message.getContent());
            pstmt.setObject(4, message.getCreatedAt());
            pstmt.setBoolean(5, message.getIsRead());
            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            System.err.println("Error saving message: " + e.getMessage());
            throw e;
        }
    }

    public Message findMessageById(int id) throws SQLException {
        if (connection == null) throw new SQLException("No database connection");
        String sql = "SELECT id, sender_id, receiver_id, content, created_at, is_read FROM messages WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new Message(
                            rs.getInt("id"),
                            rs.getInt("sender_id"),
                            rs.getInt("receiver_id"),
                            rs.getString("content"),
                            rs.getBoolean("is_read"),
                            rs.getTimestamp("created_at").toLocalDateTime()

                    );
                }
            }
        } catch (SQLException e) {
            System.err.println("Error finding message by ID: " + e.getMessage());
            throw e;
        }
        return null;
    }

    public List<Message> getMessagesByUserAndFriend(int userId, int friendId) throws SQLException {
        if (connection == null) throw new SQLException("No database connection");
        List<Message> messages = new ArrayList<>();
        String sql = "SELECT id, sender_id, receiver_id, content, created_at, is_read " +
                "FROM messages WHERE (sender_id = ? AND receiver_id = ?) OR (sender_id = ? AND receiver_id = ?) " +
                "ORDER BY created_at ASC";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            pstmt.setInt(2, friendId);
            pstmt.setInt(3, friendId);
            pstmt.setInt(4, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Message message = new Message(
                            rs.getInt("id"),
                            rs.getInt("sender_id"),
                            rs.getInt("receiver_id"),
                            rs.getString("content"),
                            rs.getBoolean("is_read"),
                            rs.getTimestamp("created_at").toLocalDateTime()
                    );
                    messages.add(message);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting messages by user and friend: " + e.getMessage());
            throw e;
        }
        return messages;
    }

    public List<Message> getMessagesByIds(int senderId, int receiverId) throws SQLException {
        if (connection == null) throw new SQLException("No database connection");
        List<Message> messages = new ArrayList<>();
        String sql = "SELECT id, sender_id, receiver_id, content, created_at, is_read " +
                "FROM messages WHERE (sender_id = ? AND receiver_id = ?) OR (sender_id = ? AND receiver_id = ?) " +
                "ORDER BY created_at ASC";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, senderId);
            pstmt.setInt(2, receiverId);
            pstmt.setInt(3, receiverId);
            pstmt.setInt(4, senderId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Message message = new Message(
                            rs.getInt("id"),
                            rs.getInt("sender_id"),
                            rs.getInt("receiver_id"),
                            rs.getString("content"),
                            rs.getBoolean("is_read"),
                            rs.getTimestamp("created_at").toLocalDateTime()

                    );
                    messages.add(message);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting messages by IDs: " + e.getMessage());
            throw e;
        }
        return messages;
    }

    public boolean markAsRead(int messageId) throws SQLException {
        if (connection == null) throw new SQLException("No database connection");
        String sql = "UPDATE messages SET is_read = TRUE WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, messageId);
            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            System.err.println("Error marking message as read: " + e.getMessage());
            throw e;
        }
    }
}