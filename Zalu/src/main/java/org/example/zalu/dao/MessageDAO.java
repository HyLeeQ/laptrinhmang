package org.example.zalu.dao;

import org.example.zalu.model.Message;
import org.example.zalu.util.database.DBConnection;
import org.example.zalu.exception.database.DatabaseException;
import org.example.zalu.exception.database.DatabaseConnectionException;
import org.example.zalu.exception.message.MessageException;
import org.example.zalu.exception.message.MessageSendFailedException;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MessageDAO {

    // Không cần constructor nhận Connection nữa
    public MessageDAO() {
        // Pool sẽ được lấy tự động từ DBConnection
    }

    private Connection getConnection() throws SQLException {
        return DBConnection.getConnection();
    }

    public boolean saveMessage(Message message) throws MessageSendFailedException, DatabaseException, DatabaseConnectionException {
        // Kiểm tra xem có cột is_pinned không
        boolean hasPinnedColumn = hasPinnedColumn();
        String sql = hasPinnedColumn
            ? "INSERT INTO messages (sender_id, receiver_id, content, file_data, file_name, created_at, is_read, group_id, " +
              "is_deleted, is_recalled, is_edited, edited_content, replied_to_message_id, replied_to_content, is_pinned) " +
              "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
            : "INSERT INTO messages (sender_id, receiver_id, content, file_data, file_name, created_at, is_read, group_id, " +
              "is_deleted, is_recalled, is_edited, edited_content, replied_to_message_id, replied_to_content) " +
              "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setInt(1, message.getSenderId());
            pstmt.setObject(2, message.getGroupId() > 0 ? null : message.getReceiverId());
            pstmt.setString(3, message.getContent());

            if (message.getFileData() != null && message.getFileData().length > 0) {
                pstmt.setBytes(4, message.getFileData());
                System.out.println("MessageDAO: Lưu file data - size: " + message.getFileData().length + " bytes, fileName: " + message.getFileName());
            } else {
                pstmt.setNull(4, Types.BLOB);
            }

            pstmt.setString(5, message.getFileName());
            pstmt.setTimestamp(6, Timestamp.valueOf(message.getCreatedAt()));
            pstmt.setBoolean(7, message.getIsRead());
            pstmt.setObject(8, message.getGroupId() > 0 ? message.getGroupId() : null);
            pstmt.setBoolean(9, message.isDeleted());
            pstmt.setBoolean(10, message.isRecalled());
            pstmt.setBoolean(11, message.isEdited());
            pstmt.setString(12, message.getEditedContent());
            if (message.getRepliedToMessageId() > 0) {
                pstmt.setInt(13, message.getRepliedToMessageId());
            } else {
                pstmt.setNull(13, Types.INTEGER);
            }
            pstmt.setString(14, message.getRepliedToContent());
            if (hasPinnedColumn) {
                pstmt.setBoolean(15, message.isPinned());
            }

            int rows = pstmt.executeUpdate();
            if (rows > 0) {
                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        message.setId(rs.getInt(1));
                        System.out.println("MessageDAO: Lưu message thành công! ID: " + message.getId());
                    }
                }
                return true;
            } else {
                throw new MessageSendFailedException("Không thể lưu tin nhắn vào database");
            }
        } catch (MessageSendFailedException e) {
            throw e;
        } catch (SQLException e) {
            System.err.println("MessageDAO: Lỗi SQL khi lưu message: " + e.getMessage());
            // Wrap SQLException thành DatabaseConnectionException nếu là lỗi kết nối
            if (e.getMessage() != null && (e.getMessage().toLowerCase().contains("connection") || 
                e.getMessage().toLowerCase().contains("timeout") || 
                e.getMessage().toLowerCase().contains("cannot establish"))) {
                throw new DatabaseConnectionException("Không thể kết nối đến database", e);
            }
            throw new MessageSendFailedException("Lỗi khi gửi tin nhắn: " + e.getMessage(), e);
        }
    }

    public List<Message> getMessagesBetween(int user1, int user2) throws MessageException, DatabaseException, DatabaseConnectionException {
        List<Message> messages = new ArrayList<>();
        boolean hasPinnedColumn = hasPinnedColumn();
        String sql;
        try (Connection conn = getConnection()) {
            hasPinnedColumn = hasPinnedColumn(conn);
            sql = hasPinnedColumn
                ? "SELECT id, sender_id, receiver_id, content, file_data, file_name, created_at, is_read, group_id, " +
                  "is_deleted, is_recalled, is_edited, edited_content, replied_to_message_id, replied_to_content, is_pinned " +
                  "FROM messages " +
                  "WHERE ((sender_id = ? AND receiver_id = ?) OR (sender_id = ? AND receiver_id = ?)) " +
                  "AND (group_id = 0 OR group_id IS NULL) ORDER BY is_pinned DESC, created_at ASC"
                : "SELECT id, sender_id, receiver_id, content, file_data, file_name, created_at, is_read, group_id, " +
                  "is_deleted, is_recalled, is_edited, edited_content, replied_to_message_id, replied_to_content " +
                  "FROM messages " +
                  "WHERE ((sender_id = ? AND receiver_id = ?) OR (sender_id = ? AND receiver_id = ?)) " +
                  "AND (group_id = 0 OR group_id IS NULL) ORDER BY created_at ASC";
        } catch (SQLException e) {
            // Fallback to basic query
            sql = "SELECT id, sender_id, receiver_id, content, file_data, file_name, created_at, is_read, group_id, " +
                  "is_deleted, is_recalled, is_edited, edited_content, replied_to_message_id, replied_to_content " +
                  "FROM messages " +
                  "WHERE ((sender_id = ? AND receiver_id = ?) OR (sender_id = ? AND receiver_id = ?)) " +
                  "AND (group_id = 0 OR group_id IS NULL) ORDER BY created_at ASC";
            hasPinnedColumn = false;
        }

        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, user1); pstmt.setInt(2, user2);
            pstmt.setInt(3, user2); pstmt.setInt(4, user1);
            // Re-check hasPinnedColumn with this connection
            boolean actuallyHasPinned = hasPinnedColumn(conn);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    byte[] fileData = rs.getBytes("file_data");
                    String fileName = rs.getString("file_name");
                    Message m = new Message(
                            rs.getInt("id"),
                            rs.getInt("sender_id"),
                            rs.getInt("receiver_id"),
                            rs.getString("content"),
                            fileData,
                            fileName,
                            rs.getBoolean("is_read"),
                            rs.getTimestamp("created_at").toLocalDateTime(),
                            rs.getObject("group_id") != null ? rs.getInt("group_id") : 0
                    );
                    m.setDeleted(rs.getBoolean("is_deleted"));
                    m.setRecalled(rs.getBoolean("is_recalled"));
                    m.setEdited(rs.getBoolean("is_edited"));
                    m.setEditedContent(rs.getString("edited_content"));
                    int repliedToId = rs.getInt("replied_to_message_id");
                    if (!rs.wasNull()) {
                        m.setRepliedToMessageId(repliedToId);
                    }
                    m.setRepliedToContent(rs.getString("replied_to_content"));
                    if (actuallyHasPinned) {
                        try {
                            m.setPinned(rs.getBoolean("is_pinned"));
                        } catch (SQLException e) {
                            // Column doesn't exist, ignore
                            m.setPinned(false);
                        }
                    }
                    if (fileName != null && fileData != null) {
                        System.out.println("MessageDAO: Load message từ DB - ID: " + m.getId() + ", fileName: " + fileName + ", fileData size: " + fileData.length);
                    }
                    messages.add(m);
                }
            }
        } catch (SQLException e) {
            // Wrap SQLException thành DatabaseConnectionException nếu là lỗi kết nối
            if (e.getMessage() != null && (e.getMessage().toLowerCase().contains("connection") || 
                e.getMessage().toLowerCase().contains("timeout") || 
                e.getMessage().toLowerCase().contains("cannot establish"))) {
                throw new DatabaseConnectionException("Không thể kết nối đến database", e);
            }
            throw new MessageException("Lỗi khi lấy tin nhắn: " + e.getMessage(), e);
        }
        return messages;
    }

    public List<Message> getAllMessagesForUser(int userId) throws MessageException, DatabaseException, DatabaseConnectionException {
        List<Message> messages = new ArrayList<>();
        String sql = """
            SELECT id, sender_id, receiver_id, content, file_data, file_name, created_at, is_read, group_id
            FROM messages
            WHERE sender_id = ? OR receiver_id = ?
            ORDER BY created_at ASC
            """;

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, userId);
            pstmt.setInt(2, userId);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Message m = new Message(
                            rs.getInt("id"),
                            rs.getInt("sender_id"),
                            rs.getInt("receiver_id"),
                            rs.getString("content"),
                            rs.getBytes("file_data"),
                            rs.getString("file_name"),
                            rs.getBoolean("is_read"),
                            rs.getTimestamp("created_at").toLocalDateTime(),
                            rs.getObject("group_id") != null ? rs.getInt("group_id") : 0
                    );
                    messages.add(m);
                }
            }
        } catch (SQLException e) {
            // Wrap SQLException thành DatabaseConnectionException nếu là lỗi kết nối
            if (e.getMessage() != null && (e.getMessage().toLowerCase().contains("connection") || 
                e.getMessage().toLowerCase().contains("timeout") || 
                e.getMessage().toLowerCase().contains("cannot establish"))) {
                throw new DatabaseConnectionException("Không thể kết nối đến database", e);
            }
            throw new MessageException("Lỗi khi lấy tin nhắn cho user: " + e.getMessage(), e);
        }
        return messages;
    }

    public List<Message> getLastMessagesPerFriend(int userId, int friendId, int limit) throws MessageException, DatabaseException, DatabaseConnectionException {
        List<Message> messages = new ArrayList<>();
        String sql = "SELECT id, sender_id, receiver_id, content, file_data, file_name, created_at, is_read, group_id " +
                "FROM messages " +
                "WHERE ((sender_id = ? AND receiver_id = ?) OR (sender_id = ? AND receiver_id = ?)) " +
                "AND group_id IS NULL ORDER BY created_at DESC LIMIT ?";

        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);   pstmt.setInt(2, friendId);
            pstmt.setInt(3, friendId); pstmt.setInt(4, userId);
            pstmt.setInt(5, limit);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Message m = new Message(
                            rs.getInt("id"),
                            rs.getInt("sender_id"),
                            rs.getInt("receiver_id"),
                            rs.getString("content"),
                            rs.getBytes("file_data"),
                            rs.getString("file_name"),
                            rs.getBoolean("is_read"),
                            rs.getTimestamp("created_at").toLocalDateTime()
                    );
                    if (rs.getObject("group_id") != null) m.setGroupId(rs.getInt("group_id"));
                    messages.add(m);
                }
            }
        } catch (SQLException e) {
            // Wrap SQLException thành DatabaseConnectionException nếu là lỗi kết nối
            if (e.getMessage() != null && (e.getMessage().toLowerCase().contains("connection") || 
                e.getMessage().toLowerCase().contains("timeout") || 
                e.getMessage().toLowerCase().contains("cannot establish"))) {
                throw new DatabaseConnectionException("Không thể kết nối đến database", e);
            }
            throw new MessageException("Lỗi khi lấy tin nhắn cuối: " + e.getMessage(), e);
        }
        Collections.reverse(messages); // Để tin cũ hiện trước
        return messages;
    }

    public boolean markAsRead(int messageId) throws DatabaseException, DatabaseConnectionException {
        String sql = "UPDATE messages SET is_read = TRUE WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, messageId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            // Wrap SQLException thành DatabaseConnectionException nếu là lỗi kết nối
            if (e.getMessage() != null && (e.getMessage().toLowerCase().contains("connection") || 
                e.getMessage().toLowerCase().contains("timeout") || 
                e.getMessage().toLowerCase().contains("cannot establish"))) {
                throw new DatabaseConnectionException("Không thể kết nối đến database", e);
            }
            throw new DatabaseException("Lỗi khi đánh dấu tin nhắn đã đọc: " + e.getMessage(), e);
        }
    }
    
    // Mark multiple messages as read (cho một conversation)
    public boolean markMessagesAsRead(int receiverId, int senderId) throws DatabaseException, DatabaseConnectionException {
        String sql = "UPDATE messages SET is_read = TRUE WHERE receiver_id = ? AND sender_id = ? AND is_read = FALSE AND (group_id IS NULL OR group_id = 0)";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, receiverId);
            pstmt.setInt(2, senderId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            // Wrap SQLException thành DatabaseConnectionException nếu là lỗi kết nối
            if (e.getMessage() != null && (e.getMessage().toLowerCase().contains("connection") || 
                e.getMessage().toLowerCase().contains("timeout") || 
                e.getMessage().toLowerCase().contains("cannot establish"))) {
                throw new DatabaseConnectionException("Không thể kết nối đến database", e);
            }
            throw new DatabaseException("Lỗi khi đánh dấu tin nhắn đã đọc: " + e.getMessage(), e);
        }
    }
    
    // Mark group messages as read
    public boolean markGroupMessagesAsRead(int userId, int groupId) throws DatabaseException, DatabaseConnectionException {
        String sql = "UPDATE messages SET is_read = TRUE WHERE group_id = ? AND receiver_id = ? AND is_read = FALSE";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, groupId);
            pstmt.setInt(2, userId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            // Wrap SQLException thành DatabaseConnectionException nếu là lỗi kết nối
            if (e.getMessage() != null && (e.getMessage().toLowerCase().contains("connection") || 
                e.getMessage().toLowerCase().contains("timeout") || 
                e.getMessage().toLowerCase().contains("cannot establish"))) {
                throw new DatabaseConnectionException("Không thể kết nối đến database", e);
            }
            throw new DatabaseException("Lỗi khi đánh dấu tin nhắn nhóm đã đọc: " + e.getMessage(), e);
        }
    }

    public int getUnreadCount(int userId) throws DatabaseException, DatabaseConnectionException {
        String sql = "SELECT COUNT(*) FROM messages WHERE receiver_id = ? AND is_read = FALSE AND group_id IS NULL";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            // Wrap SQLException thành DatabaseConnectionException nếu là lỗi kết nối
            if (e.getMessage() != null && (e.getMessage().toLowerCase().contains("connection") || 
                e.getMessage().toLowerCase().contains("timeout") || 
                e.getMessage().toLowerCase().contains("cannot establish"))) {
                throw new DatabaseConnectionException("Không thể kết nối đến database", e);
            }
            throw new DatabaseException("Lỗi khi lấy số tin nhắn chưa đọc: " + e.getMessage(), e);
        }
    }
    
    /**
     * Lấy số tin nhắn chưa đọc cho một conversation cụ thể (1-1 chat)
     */
    public int getUnreadCountForConversation(int userId, int friendId) throws DatabaseException, DatabaseConnectionException {
        String sql = "SELECT COUNT(*) FROM messages WHERE receiver_id = ? AND sender_id = ? AND is_read = FALSE AND (group_id IS NULL OR group_id = 0)";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            pstmt.setInt(2, friendId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            // Wrap SQLException thành DatabaseConnectionException nếu là lỗi kết nối
            if (e.getMessage() != null && (e.getMessage().toLowerCase().contains("connection") || 
                e.getMessage().toLowerCase().contains("timeout") || 
                e.getMessage().toLowerCase().contains("cannot establish"))) {
                throw new DatabaseConnectionException("Không thể kết nối đến database", e);
            }
            throw new DatabaseException("Lỗi khi lấy số tin nhắn chưa đọc", e);
        }
    }
    
    /**
     * Lấy số tin nhắn chưa đọc cho một nhóm
     */
    public int getUnreadCountForGroup(int userId, int groupId) throws DatabaseException, DatabaseConnectionException {
        String sql = "SELECT COUNT(*) FROM messages WHERE group_id = ? AND receiver_id = ? AND is_read = FALSE";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, groupId);
            pstmt.setInt(2, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            // Wrap SQLException thành DatabaseConnectionException nếu là lỗi kết nối
            if (e.getMessage() != null && (e.getMessage().toLowerCase().contains("connection") || 
                e.getMessage().toLowerCase().contains("timeout") || 
                e.getMessage().toLowerCase().contains("cannot establish"))) {
                throw new DatabaseConnectionException("Không thể kết nối đến database", e);
            }
            throw new DatabaseException("Lỗi khi lấy số tin nhắn chưa đọc nhóm", e);
        }
    }

    public List<Message> getMessagesForGroup(int groupId) throws MessageException, DatabaseException, DatabaseConnectionException {
        List<Message> messages = new ArrayList<>();
        boolean hasPinnedColumn = false;
        String sql;
        try (Connection conn = getConnection()) {
            hasPinnedColumn = hasPinnedColumn(conn);
            sql = hasPinnedColumn
                ? "SELECT id, sender_id, receiver_id, content, file_data, file_name, created_at, is_read, group_id, " +
                  "is_deleted, is_recalled, is_edited, edited_content, replied_to_message_id, replied_to_content, is_pinned " +
                  "FROM messages WHERE group_id = ? ORDER BY is_pinned DESC, created_at ASC"
                : "SELECT id, sender_id, receiver_id, content, file_data, file_name, created_at, is_read, group_id, " +
                  "is_deleted, is_recalled, is_edited, edited_content, replied_to_message_id, replied_to_content " +
                  "FROM messages WHERE group_id = ? ORDER BY created_at ASC";
        } catch (SQLException e) {
            sql = "SELECT id, sender_id, receiver_id, content, file_data, file_name, created_at, is_read, group_id, " +
                  "is_deleted, is_recalled, is_edited, edited_content, replied_to_message_id, replied_to_content " +
                  "FROM messages WHERE group_id = ? ORDER BY created_at ASC";
            hasPinnedColumn = false;
        }

        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, groupId);
            boolean actuallyHasPinned = hasPinnedColumn(conn);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Message m = new Message(
                            rs.getInt("id"),
                            rs.getInt("sender_id"),
                            rs.getInt("receiver_id"),
                            rs.getString("content"),
                            rs.getBytes("file_data"),
                            rs.getString("file_name"),
                            rs.getBoolean("is_read"),
                            rs.getTimestamp("created_at").toLocalDateTime(),
                            groupId
                    );
                    m.setDeleted(rs.getBoolean("is_deleted"));
                    m.setRecalled(rs.getBoolean("is_recalled"));
                    m.setEdited(rs.getBoolean("is_edited"));
                    m.setEditedContent(rs.getString("edited_content"));
                    int repliedToId = rs.getInt("replied_to_message_id");
                    if (!rs.wasNull()) {
                        m.setRepliedToMessageId(repliedToId);
                    }
                    m.setRepliedToContent(rs.getString("replied_to_content"));
                    if (actuallyHasPinned) {
                        try {
                            m.setPinned(rs.getBoolean("is_pinned"));
                        } catch (SQLException e) {
                            // Column doesn't exist, ignore
                            m.setPinned(false);
                        }
                    }
                    messages.add(m);
                }
            }
        } catch (SQLException e) {
            // Wrap SQLException thành DatabaseConnectionException nếu là lỗi kết nối
            if (e.getMessage() != null && (e.getMessage().toLowerCase().contains("connection") || 
                e.getMessage().toLowerCase().contains("timeout") || 
                e.getMessage().toLowerCase().contains("cannot establish"))) {
                throw new DatabaseConnectionException("Không thể kết nối đến database", e);
            }
            throw new MessageException("Lỗi khi lấy tin nhắn nhóm: " + e.getMessage(), e);
        }
        return messages;
    }

    // Nếu bạn có thêm method khác (updateMessage, getMessageById, v.v.) thì cứ thêm vào, mình để sẵn cấu trúc
    
    /**
     * Lấy message theo ID
     */
    public Message getMessageById(int messageId) throws MessageException, DatabaseException, DatabaseConnectionException {
        boolean hasPinnedColumn = hasPinnedColumn();
        String sql = hasPinnedColumn
            ? "SELECT id, sender_id, receiver_id, content, file_data, file_name, created_at, is_read, group_id, " +
              "is_deleted, is_recalled, is_edited, edited_content, replied_to_message_id, replied_to_content, is_pinned " +
              "FROM messages WHERE id = ?"
            : "SELECT id, sender_id, receiver_id, content, file_data, file_name, created_at, is_read, group_id, " +
              "is_deleted, is_recalled, is_edited, edited_content, replied_to_message_id, replied_to_content " +
              "FROM messages WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, messageId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    byte[] fileData = rs.getBytes("file_data");
                    String fileName = rs.getString("file_name");
                    Message m = new Message(
                            rs.getInt("id"),
                            rs.getInt("sender_id"),
                            rs.getInt("receiver_id"),
                            rs.getString("content"),
                            fileData,
                            fileName,
                            rs.getBoolean("is_read"),
                            rs.getTimestamp("created_at").toLocalDateTime(),
                            rs.getObject("group_id") != null ? rs.getInt("group_id") : 0
                    );
                    m.setDeleted(rs.getBoolean("is_deleted"));
                    m.setRecalled(rs.getBoolean("is_recalled"));
                    m.setEdited(rs.getBoolean("is_edited"));
                    m.setEditedContent(rs.getString("edited_content"));
                    int repliedToId = rs.getInt("replied_to_message_id");
                    if (!rs.wasNull()) {
                        m.setRepliedToMessageId(repliedToId);
                    }
                    m.setRepliedToContent(rs.getString("replied_to_content"));
                    if (hasPinnedColumn) {
                        m.setPinned(rs.getBoolean("is_pinned"));
                    }
                    return m;
                }
            }
        } catch (SQLException e) {
            if (e.getMessage() != null && (e.getMessage().toLowerCase().contains("connection") || 
                e.getMessage().toLowerCase().contains("timeout") || 
                e.getMessage().toLowerCase().contains("cannot establish"))) {
                throw new DatabaseConnectionException("Không thể kết nối đến database", e);
            }
            throw new MessageException("Lỗi khi lấy tin nhắn: " + e.getMessage(), e);
        }
        return null;
    }
    
    /**
     * Xóa tin nhắn cho mình (is_deleted = true)
     */
    public boolean deleteMessage(int messageId, int userId) throws DatabaseException, DatabaseConnectionException {
        String sql = "UPDATE messages SET is_deleted = TRUE WHERE id = ? AND sender_id = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, messageId);
            pstmt.setInt(2, userId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            if (e.getMessage() != null && (e.getMessage().toLowerCase().contains("connection") || 
                e.getMessage().toLowerCase().contains("timeout") || 
                e.getMessage().toLowerCase().contains("cannot establish"))) {
                throw new DatabaseConnectionException("Không thể kết nối đến database", e);
            }
            throw new DatabaseException("Lỗi khi xóa tin nhắn: " + e.getMessage(), e);
        }
    }
    
    /**
     * Thu hồi tin nhắn (xóa cho cả hai: is_recalled = true)
     */
    public boolean recallMessage(int messageId, int userId) throws DatabaseException, DatabaseConnectionException {
        String sql = "UPDATE messages SET is_recalled = TRUE, content = ? WHERE id = ? AND sender_id = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, "Tin nhắn đã được thu hồi");
            pstmt.setInt(2, messageId);
            pstmt.setInt(3, userId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            if (e.getMessage() != null && (e.getMessage().toLowerCase().contains("connection") || 
                e.getMessage().toLowerCase().contains("timeout") || 
                e.getMessage().toLowerCase().contains("cannot establish"))) {
                throw new DatabaseConnectionException("Không thể kết nối đến database", e);
            }
            throw new DatabaseException("Lỗi khi thu hồi tin nhắn: " + e.getMessage(), e);
        }
    }
    
    /**
     * Chỉnh sửa tin nhắn
     */
    public boolean editMessage(int messageId, int userId, String newContent) throws DatabaseException, DatabaseConnectionException {
        String sql = "UPDATE messages SET content = ?, is_edited = TRUE, edited_content = ? WHERE id = ? AND sender_id = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, newContent);
            pstmt.setString(2, newContent);
            pstmt.setInt(3, messageId);
            pstmt.setInt(4, userId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            if (e.getMessage() != null && (e.getMessage().toLowerCase().contains("connection") || 
                e.getMessage().toLowerCase().contains("timeout") || 
                e.getMessage().toLowerCase().contains("cannot establish"))) {
                throw new DatabaseConnectionException("Không thể kết nối đến database", e);
            }
            throw new DatabaseException("Lỗi khi chỉnh sửa tin nhắn: " + e.getMessage(), e);
        }
    }
    
    /**
     * Kiểm tra xem bảng messages có cột is_pinned không
     */
    private static volatile Boolean messagesHasPinnedColumn = null;
    
    private boolean hasPinnedColumn() {
        if (messagesHasPinnedColumn != null) {
            return messagesHasPinnedColumn;
        }
        synchronized (MessageDAO.class) {
            if (messagesHasPinnedColumn == null) {
                try (Connection conn = getConnection()) {
                    messagesHasPinnedColumn = detectPinnedColumn(conn);
                    System.out.println("MessageDAO: messages.is_pinned column available? " + messagesHasPinnedColumn);
                } catch (SQLException e) {
                    System.out.println("MessageDAO: không thể kiểm tra cột is_pinned, mặc định = false. Lỗi: " + e.getMessage());
                    messagesHasPinnedColumn = false;
                }
            }
        }
        return messagesHasPinnedColumn;
    }
    
    private boolean hasPinnedColumn(Connection conn) {
        if (messagesHasPinnedColumn != null) {
            return messagesHasPinnedColumn;
        }
        synchronized (MessageDAO.class) {
            if (messagesHasPinnedColumn == null) {
                messagesHasPinnedColumn = detectPinnedColumn(conn);
                System.out.println("MessageDAO: messages.is_pinned column available? " + messagesHasPinnedColumn);
            }
        }
        return messagesHasPinnedColumn;
    }
    
    private boolean detectPinnedColumn(Connection conn) {
        try {
            DatabaseMetaData meta = conn.getMetaData();
            String catalog = conn.getCatalog();
            if (columnExists(meta, catalog, "messages", "is_pinned")) {
                return true;
            }
            if (columnExists(meta, catalog, "MESSAGES", "IS_PINNED")) {
                return true;
            }
        } catch (SQLException e) {
            System.out.println("MessageDAO: không thể kiểm tra cột is_pinned: " + e.getMessage());
        }
        return false;
    }
    
    private boolean columnExists(DatabaseMetaData meta, String catalog, String tableName, String columnName) throws SQLException {
        try (ResultSet rs = meta.getColumns(catalog, null, tableName, columnName)) {
            return rs.next();
        }
    }
    
    /**
     * Tìm kiếm tin nhắn trong cuộc trò chuyện (1-1)
     */
    public List<Message> searchMessages(int user1, int user2, String searchText) throws MessageException, DatabaseException, DatabaseConnectionException {
        List<Message> messages = new ArrayList<>();
        boolean hasPinnedColumn = hasPinnedColumn();
        String sql = hasPinnedColumn
            ? "SELECT id, sender_id, receiver_id, content, file_data, file_name, created_at, is_read, group_id, " +
              "is_deleted, is_recalled, is_edited, edited_content, replied_to_message_id, replied_to_content, is_pinned " +
              "FROM messages " +
              "WHERE ((sender_id = ? AND receiver_id = ?) OR (sender_id = ? AND receiver_id = ?)) " +
              "AND (group_id = 0 OR group_id IS NULL) " +
              "AND (content LIKE ? OR file_name LIKE ?) " +
              "ORDER BY created_at ASC"
            : "SELECT id, sender_id, receiver_id, content, file_data, file_name, created_at, is_read, group_id, " +
              "is_deleted, is_recalled, is_edited, edited_content, replied_to_message_id, replied_to_content " +
              "FROM messages " +
              "WHERE ((sender_id = ? AND receiver_id = ?) OR (sender_id = ? AND receiver_id = ?)) " +
              "AND (group_id = 0 OR group_id IS NULL) " +
              "AND (content LIKE ? OR file_name LIKE ?) " +
              "ORDER BY created_at ASC";
        
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            String searchPattern = "%" + searchText + "%";
            pstmt.setInt(1, user1);
            pstmt.setInt(2, user2);
            pstmt.setInt(3, user2);
            pstmt.setInt(4, user1);
            pstmt.setString(5, searchPattern);
            pstmt.setString(6, searchPattern);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    byte[] fileData = rs.getBytes("file_data");
                    String fileName = rs.getString("file_name");
                    Message m = new Message(
                            rs.getInt("id"),
                            rs.getInt("sender_id"),
                            rs.getInt("receiver_id"),
                            rs.getString("content"),
                            fileData,
                            fileName,
                            rs.getBoolean("is_read"),
                            rs.getTimestamp("created_at").toLocalDateTime(),
                            rs.getObject("group_id") != null ? rs.getInt("group_id") : 0
                    );
                    m.setDeleted(rs.getBoolean("is_deleted"));
                    m.setRecalled(rs.getBoolean("is_recalled"));
                    m.setEdited(rs.getBoolean("is_edited"));
                    m.setEditedContent(rs.getString("edited_content"));
                    int repliedToId = rs.getInt("replied_to_message_id");
                    if (!rs.wasNull()) {
                        m.setRepliedToMessageId(repliedToId);
                    }
                    m.setRepliedToContent(rs.getString("replied_to_content"));
                    if (hasPinnedColumn) {
                        m.setPinned(rs.getBoolean("is_pinned"));
                    }
                    messages.add(m);
                }
            }
        } catch (SQLException e) {
            if (e.getMessage() != null && (e.getMessage().toLowerCase().contains("connection") || 
                e.getMessage().toLowerCase().contains("timeout") || 
                e.getMessage().toLowerCase().contains("cannot establish"))) {
                throw new DatabaseConnectionException("Không thể kết nối đến database", e);
            }
            throw new MessageException("Lỗi khi tìm kiếm tin nhắn: " + e.getMessage(), e);
        }
        return messages;
    }
    
    /**
     * Tìm kiếm tin nhắn trong nhóm
     */
    public List<Message> searchGroupMessages(int groupId, String searchText) throws MessageException, DatabaseException, DatabaseConnectionException {
        List<Message> messages = new ArrayList<>();
        boolean hasPinnedColumn = hasPinnedColumn();
        String sql = hasPinnedColumn
            ? "SELECT id, sender_id, receiver_id, content, file_data, file_name, created_at, is_read, group_id, " +
              "is_deleted, is_recalled, is_edited, edited_content, replied_to_message_id, replied_to_content, is_pinned " +
              "FROM messages " +
              "WHERE group_id = ? " +
              "AND (content LIKE ? OR file_name LIKE ?) " +
              "ORDER BY created_at ASC"
            : "SELECT id, sender_id, receiver_id, content, file_data, file_name, created_at, is_read, group_id, " +
              "is_deleted, is_recalled, is_edited, edited_content, replied_to_message_id, replied_to_content " +
              "FROM messages " +
              "WHERE group_id = ? " +
              "AND (content LIKE ? OR file_name LIKE ?) " +
              "ORDER BY created_at ASC";
        
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            String searchPattern = "%" + searchText + "%";
            pstmt.setInt(1, groupId);
            pstmt.setString(2, searchPattern);
            pstmt.setString(3, searchPattern);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    byte[] fileData = rs.getBytes("file_data");
                    String fileName = rs.getString("file_name");
                    Message m = new Message(
                            rs.getInt("id"),
                            rs.getInt("sender_id"),
                            rs.getInt("receiver_id"),
                            rs.getString("content"),
                            fileData,
                            fileName,
                            rs.getBoolean("is_read"),
                            rs.getTimestamp("created_at").toLocalDateTime(),
                            groupId
                    );
                    m.setDeleted(rs.getBoolean("is_deleted"));
                    m.setRecalled(rs.getBoolean("is_recalled"));
                    m.setEdited(rs.getBoolean("is_edited"));
                    m.setEditedContent(rs.getString("edited_content"));
                    int repliedToId = rs.getInt("replied_to_message_id");
                    if (!rs.wasNull()) {
                        m.setRepliedToMessageId(repliedToId);
                    }
                    m.setRepliedToContent(rs.getString("replied_to_content"));
                    if (hasPinnedColumn) {
                        m.setPinned(rs.getBoolean("is_pinned"));
                    }
                    messages.add(m);
                }
            }
        } catch (SQLException e) {
            if (e.getMessage() != null && (e.getMessage().toLowerCase().contains("connection") || 
                e.getMessage().toLowerCase().contains("timeout") || 
                e.getMessage().toLowerCase().contains("cannot establish"))) {
                throw new DatabaseConnectionException("Không thể kết nối đến database", e);
            }
            throw new MessageException("Lỗi khi tìm kiếm tin nhắn nhóm: " + e.getMessage(), e);
        }
        return messages;
    }
    
    /**
     * Ghim/Bỏ ghim tin nhắn
     */
    public boolean pinMessage(int messageId, boolean pinned) throws DatabaseException, DatabaseConnectionException {
        if (!hasPinnedColumn()) {
            return false; // Database chưa hỗ trợ
        }
        
        String sql = "UPDATE messages SET is_pinned = ? WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setBoolean(1, pinned);
            pstmt.setInt(2, messageId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            if (e.getMessage() != null && (e.getMessage().toLowerCase().contains("connection") || 
                e.getMessage().toLowerCase().contains("timeout") || 
                e.getMessage().toLowerCase().contains("cannot establish"))) {
                throw new DatabaseConnectionException("Không thể kết nối đến database", e);
            }
            throw new DatabaseException("Lỗi khi ghim/bỏ ghim tin nhắn: " + e.getMessage(), e);
        }
    }
    
    /**
     * Lấy danh sách tin nhắn đã ghim trong cuộc trò chuyện
     */
    public List<Message> getPinnedMessages(int user1, int user2) throws MessageException, DatabaseException, DatabaseConnectionException {
        if (!hasPinnedColumn()) {
            return new ArrayList<>(); // Database chưa hỗ trợ
        }
        
        List<Message> messages = new ArrayList<>();
        String sql = "SELECT id, sender_id, receiver_id, content, file_data, file_name, created_at, is_read, group_id, " +
                "is_deleted, is_recalled, is_edited, edited_content, replied_to_message_id, replied_to_content, is_pinned " +
                "FROM messages " +
                "WHERE ((sender_id = ? AND receiver_id = ?) OR (sender_id = ? AND receiver_id = ?)) " +
                "AND (group_id = 0 OR group_id IS NULL) " +
                "AND is_pinned = TRUE " +
                "ORDER BY created_at DESC";
        
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, user1);
            pstmt.setInt(2, user2);
            pstmt.setInt(3, user2);
            pstmt.setInt(4, user1);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    byte[] fileData = rs.getBytes("file_data");
                    String fileName = rs.getString("file_name");
                    Message m = new Message(
                            rs.getInt("id"),
                            rs.getInt("sender_id"),
                            rs.getInt("receiver_id"),
                            rs.getString("content"),
                            fileData,
                            fileName,
                            rs.getBoolean("is_read"),
                            rs.getTimestamp("created_at").toLocalDateTime(),
                            rs.getObject("group_id") != null ? rs.getInt("group_id") : 0
                    );
                    m.setDeleted(rs.getBoolean("is_deleted"));
                    m.setRecalled(rs.getBoolean("is_recalled"));
                    m.setEdited(rs.getBoolean("is_edited"));
                    m.setEditedContent(rs.getString("edited_content"));
                    int repliedToId = rs.getInt("replied_to_message_id");
                    if (!rs.wasNull()) {
                        m.setRepliedToMessageId(repliedToId);
                    }
                    m.setRepliedToContent(rs.getString("replied_to_content"));
                    m.setPinned(true);
                    messages.add(m);
                }
            }
        } catch (SQLException e) {
            if (e.getMessage() != null && (e.getMessage().toLowerCase().contains("connection") || 
                e.getMessage().toLowerCase().contains("timeout") || 
                e.getMessage().toLowerCase().contains("cannot establish"))) {
                throw new DatabaseConnectionException("Không thể kết nối đến database", e);
            }
            throw new MessageException("Lỗi khi lấy tin nhắn đã ghim: " + e.getMessage(), e);
        }
        return messages;
    }
    
    /**
     * Lấy danh sách tin nhắn đã ghim trong nhóm
     */
    public List<Message> getPinnedGroupMessages(int groupId) throws MessageException, DatabaseException, DatabaseConnectionException {
        if (!hasPinnedColumn()) {
            return new ArrayList<>(); // Database chưa hỗ trợ
        }
        
        List<Message> messages = new ArrayList<>();
        String sql = "SELECT id, sender_id, receiver_id, content, file_data, file_name, created_at, is_read, group_id, " +
                "is_deleted, is_recalled, is_edited, edited_content, replied_to_message_id, replied_to_content, is_pinned " +
                "FROM messages " +
                "WHERE group_id = ? " +
                "AND is_pinned = TRUE " +
                "ORDER BY created_at DESC";
        
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, groupId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    byte[] fileData = rs.getBytes("file_data");
                    String fileName = rs.getString("file_name");
                    Message m = new Message(
                            rs.getInt("id"),
                            rs.getInt("sender_id"),
                            rs.getInt("receiver_id"),
                            rs.getString("content"),
                            fileData,
                            fileName,
                            rs.getBoolean("is_read"),
                            rs.getTimestamp("created_at").toLocalDateTime(),
                            groupId
                    );
                    m.setDeleted(rs.getBoolean("is_deleted"));
                    m.setRecalled(rs.getBoolean("is_recalled"));
                    m.setEdited(rs.getBoolean("is_edited"));
                    m.setEditedContent(rs.getString("edited_content"));
                    int repliedToId = rs.getInt("replied_to_message_id");
                    if (!rs.wasNull()) {
                        m.setRepliedToMessageId(repliedToId);
                    }
                    m.setRepliedToContent(rs.getString("replied_to_content"));
                    m.setPinned(true);
                    messages.add(m);
                }
            }
        } catch (SQLException e) {
            if (e.getMessage() != null && (e.getMessage().toLowerCase().contains("connection") || 
                e.getMessage().toLowerCase().contains("timeout") || 
                e.getMessage().toLowerCase().contains("cannot establish"))) {
                throw new DatabaseConnectionException("Không thể kết nối đến database", e);
            }
            throw new MessageException("Lỗi khi lấy tin nhắn đã ghim nhóm: " + e.getMessage(), e);
        }
        return messages;
    }
}