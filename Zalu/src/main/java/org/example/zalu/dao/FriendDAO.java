package org.example.zalu.dao;

import org.example.zalu.model.Friend;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class FriendDAO {
    private Connection connection;

    public FriendDAO(Connection connection) {
        this.connection = connection;
    }

    public boolean saveFriend(Friend friend) throws SQLException {
        String sql = "INSERT INTO friends (user_id, friend_id, status) VALUES (?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, friend.getUserId());
            pstmt.setInt(2, friend.getFriendId());
            pstmt.setString(3, friend.getStatus());
            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;
        }
    }

    public Friend findFriendById(int userId, int friendId) throws SQLException {
        String sql = "SELECT * FROM friends WHERE user_id = ? AND friend_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            pstmt.setInt(2, friendId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new Friend(
                            rs.getInt("user_id"),
                            rs.getInt("friend_id"),
                            rs.getString("status")
                    );
                }
            }
        }
        return null;
    }

    // Lấy danh sách bạn bè của người dùng (accepted)
    public List<Integer> getFriendsByUserId(int userId) throws SQLException {
        List<Integer> friendIds = new ArrayList<>();
        String sql = "SELECT friend_id FROM friends WHERE user_id = ? AND status = 'accepted'";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    friendIds.add(rs.getInt("friend_id"));
                }
            }
        }
        return friendIds;
    }

    // Lấy danh sách lời mời kết bạn (pending requests)
    public List<Integer> getPendingRequests(int userId) throws SQLException {
        List<Integer> senderIds = new ArrayList<>();
        String sql = "SELECT user_id FROM friends WHERE friend_id = ? AND status = 'pending'";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    senderIds.add(rs.getInt("user_id"));
                }
            }
        }
        return senderIds;
    }

    // Gửi lời mời kết bạn (status = 'pending') - Lưu hai chiều với transaction
    public boolean sendFriendRequest(int userId, int friendId) throws SQLException {
        if (userId == friendId) return false; // Không tự kết bạn

        // Kiểm tra đã tồn tại chưa (hai chiều)
        String checkSql = "SELECT COUNT(*) FROM friends WHERE (user_id = ? AND friend_id = ?) OR (user_id = ? AND friend_id = ?)";
        try (PreparedStatement checkStmt = connection.prepareStatement(checkSql)) {
            checkStmt.setInt(1, userId);
            checkStmt.setInt(2, friendId);
            checkStmt.setInt(3, friendId);
            checkStmt.setInt(4, userId);
            ResultSet rs = checkStmt.executeQuery();
            if (rs.next() && rs.getInt(1) > 0) {
                System.out.println("Friend request already exists for " + userId + " - " + friendId);
                return false;
            }
        }

        // Sử dụng transaction để đảm bảo cả hai chiều thành công
        connection.setAutoCommit(false); // Bắt đầu transaction
        try {
            // Lưu chiều 1: sender -> receiver
            String sql = "INSERT INTO friends (user_id, friend_id, status, created_at) VALUES (?, ?, 'pending', CURRENT_TIMESTAMP)";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setInt(1, userId);
                pstmt.setInt(2, friendId);
                int rowsAffected1 = pstmt.executeUpdate();
                if (rowsAffected1 == 0) {
                    connection.rollback();
                    return false;
                }
                System.out.println("Inserted first direction: " + userId + " -> " + friendId);
            }

            // Lưu chiều 2: receiver -> sender
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setInt(1, friendId);
                pstmt.setInt(2, userId);
                int rowsAffected2 = pstmt.executeUpdate();
                if (rowsAffected2 == 0) {
                    connection.rollback();
                    return false;
                }
                System.out.println("Inserted second direction: " + friendId + " -> " + userId);
            }

            connection.commit(); // Commit nếu cả hai thành công
            System.out.println("Friend request sent successfully (both directions)");
            return true;
        } catch (SQLException e) {
            connection.rollback(); // Rollback nếu lỗi
            e.printStackTrace();
            System.out.println("Error sending friend request: " + e.getMessage());
            throw e;
        } finally {
            connection.setAutoCommit(true); // Kết thúc transaction
        }
    }

    // Chấp nhận lời mời kết bạn (cập nhật hai chiều)
    public boolean acceptFriendRequest(int userId, int friendId) throws SQLException {
        String sql = "UPDATE friends SET status = 'accepted' WHERE user_id = ? AND friend_id = ? AND status = 'pending'";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            // Cập nhật chiều 1: sender -> receiver
            pstmt.setInt(1, friendId); // Người gửi lời mời
            pstmt.setInt(2, userId); // Người nhận lời mời
            int rowsAffected1 = pstmt.executeUpdate();
            System.out.println("Rows affected in first direction: " + rowsAffected1);
            if (rowsAffected1 > 0) {
                // Cập nhật chiều 2: receiver -> sender
                pstmt.setInt(1, userId);
                pstmt.setInt(2, friendId);
                int rowsAffected2 = pstmt.executeUpdate();
                System.out.println("Rows affected in second direction: " + rowsAffected2);
                if (rowsAffected2 > 0) {
                    return true;
                }
            }
            return false;
        }
    }

    // Từ chối lời mời kết bạn (xóa hai chiều)
    public boolean rejectFriendRequest(int userId, int friendId) throws SQLException {
        String sql = "DELETE FROM friends WHERE user_id = ? AND friend_id = ? AND status = 'pending'";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            // Xóa chiều 1: sender -> receiver
            pstmt.setInt(1, friendId); // Người gửi lời mời
            pstmt.setInt(2, userId); // Người nhận lời mời
            int rowsAffected1 = pstmt.executeUpdate();
            System.out.println("Rows deleted in first direction: " + rowsAffected1);
            if (rowsAffected1 > 0) {
                // Xóa chiều 2: receiver -> sender
                pstmt.setInt(1, userId);
                pstmt.setInt(2, friendId);
                int rowsAffected2 = pstmt.executeUpdate();
                System.out.println("Rows deleted in second direction: " + rowsAffected2);
                if (rowsAffected2 > 0) {
                    return true;
                }
            }
            return false;
        }
    }

    // Cập nhật trạng thái bạn bè
    public boolean updateFriendStatus(int userId, int friendId, String status) throws SQLException {
        String sql = "UPDATE friends SET status = ? WHERE user_id = ? AND friend_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, status);
            pstmt.setInt(2, userId);
            pstmt.setInt(3, friendId);
            return pstmt.executeUpdate() > 0;
        }
    }
}