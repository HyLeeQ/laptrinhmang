package org.example.zalu.dao;

import org.example.zalu.model.Friend;
import org.example.zalu.model.User;
import org.example.zalu.util.database.DBConnection;
import org.example.zalu.exception.database.DatabaseException;
import org.example.zalu.exception.database.DatabaseConnectionException;
import org.example.zalu.exception.auth.UserNotFoundException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class FriendDAO {

    // Không cần gì nữa, chỉ dùng DBConnection.getConnection()

    private Connection getConnection() throws SQLException {
        return DBConnection.getConnection();   // ← Dùng pool xịn của bạn
    }

    // ===================== TOÀN BỘ METHOD GIỮ NGUYÊN =====================

    public boolean isExistingFriendOrRequest(int userId, int friendId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM friends " +
                "WHERE ((user_id = ? AND friend_id = ?) OR (user_id = ? AND friend_id = ?)) " +
                "AND status IN ('accepted', 'pending')";
        try (Connection conn = getConnection(); PreparedStatement p = conn.prepareStatement(sql)) {
            p.setInt(1, userId);
            p.setInt(2, friendId);
            p.setInt(3, friendId);
            p.setInt(4, userId);

            try(ResultSet rs = p.executeQuery()) {
                if(rs.next()){
                    int count = rs.getInt(1);
                    System.out.println("Kiểm tra quan hệ: count = " + count + " giữa " + userId + " và " + friendId);
                    return count > 0;
                }
            }
        }
        return false;
    }

    public boolean saveFriend(Friend friend) throws SQLException {
        String sql = "INSERT INTO friends (user_id, friend_id, status) VALUES (?, ?, ?)";
        try (Connection conn = getConnection(); PreparedStatement p = conn.prepareStatement(sql)) {
            p.setInt(1, friend.getUserId());
            p.setInt(2, friend.getFriendId());
            p.setString(3, friend.getStatus());
            return p.executeUpdate() > 0;
        }
    }

    public Friend findFriendById(int userId, int friendId) throws SQLException {
        String sql = "SELECT * FROM friends WHERE user_id = ? AND friend_id = ?";
        try (Connection conn = getConnection(); PreparedStatement p = conn.prepareStatement(sql)) {
            p.setInt(1, userId);
            p.setInt(2, friendId);
            try (ResultSet rs = p.executeQuery()) {
                if (rs.next()) {
                    return new Friend(rs.getInt("user_id"), rs.getInt("friend_id"), rs.getString("status"));
                }
            }
        }
        return null;
    }

    public List<Integer> getFriendsByUserId(int userId) throws SQLException {
        List<Integer> list = new ArrayList<>();
        String sql = """
        SELECT friend_id FROM friends WHERE user_id = ? AND status = 'accepted'
        UNION
        SELECT user_id FROM friends WHERE friend_id = ? AND status = 'accepted'
        """;
        try (Connection conn = getConnection();
             PreparedStatement p = conn.prepareStatement(sql)) {
            p.setInt(1, userId);
            p.setInt(2, userId);
            try (ResultSet rs = p.executeQuery()) {
                while (rs.next()) {
                    int friendId = rs.getInt(1);
                    if (friendId != userId) {
                        list.add(friendId);
                    }
                }
            }
        }
        return list;
    }

    public List<Integer> getPendingRequests(int userId) throws SQLException {
        List<Integer> list = new ArrayList<>();
        String sql = "SELECT user_id FROM friends WHERE friend_id = ? AND status = 'pending'";
        try (Connection conn = getConnection(); PreparedStatement p = conn.prepareStatement(sql)) {
            p.setInt(1, userId);
            try (ResultSet rs = p.executeQuery()) {
                while (rs.next()) list.add(rs.getInt(1));
            }
        }
        return list;
    }

    public List<Integer> getOutgoingRequest(int userId) throws SQLException {
        List<Integer> list = new ArrayList<>();
        String sql = "SELECT friend_id FROM friends WHERE user_id = ? AND status = 'pending'";
        try (Connection conn = getConnection(); PreparedStatement p = conn.prepareStatement(sql)) {
            p.setInt(1, userId);
            try (ResultSet rs = p.executeQuery()) {
                while (rs.next()) list.add(rs.getInt(1));
            }
        }
        return list;
    }

    public List<User> getPendingRequestsWithUserInfo(int userId, UserDAO userDAO) throws SQLException, DatabaseException, DatabaseConnectionException {
        List<User> list = new ArrayList<>();
        for (int senderId : getPendingRequests(userId)) {
            try {
                User u = userDAO.getUserById(senderId);
                if (u != null) list.add(u);
            } catch (UserNotFoundException e) {
                // User might have been deleted, skip silently
                System.out.println("User with ID " + senderId + " not found, skipping...");
            }
        }
        return list;
    }

    public List<User> getOutgoingRequestsWithUserInfo(int userId, UserDAO userDAO) throws SQLException, DatabaseException, DatabaseConnectionException {
        List<User> list = new ArrayList<>();
        for (int receiverId : getOutgoingRequest(userId)) {
            try {
                User u = userDAO.getUserById(receiverId);
                if (u != null) list.add(u);
            } catch (UserNotFoundException e) {
                // User might have been deleted, skip silently
                System.out.println("User with ID " + receiverId + " not found, skipping...");
            }
        }
        return list;
    }

    public boolean sendFriendRequest(int userId, int friendId) throws SQLException {
        if (userId == friendId) return false;
        if (!userExists(userId) || !userExists(friendId)) return false;
        if (isExistingFriendOrRequest(userId, friendId)) return false;

        String sql = "INSERT INTO friends (user_id, friend_id, status, created_at) VALUES (?, ?, 'pending', NOW())";
        try (Connection conn = getConnection();
             PreparedStatement p = conn.prepareStatement(sql)) {
            p.setInt(1, userId);     // người gửi
            p.setInt(2, friendId);   // người nhận
            boolean success = p.executeUpdate() > 0;
            System.out.println(success ?
                    "Gửi lời mời thành công từ " + userId + " đến " + friendId :
                    "Gửi thất bại");
            return success;
        }
    }
    public boolean acceptFriendRequest(int userId, int senderId) throws SQLException {
        String sql = "UPDATE friends SET status = 'accepted' WHERE user_id = ? AND friend_id = ? AND status = 'pending'";
        try (Connection conn = getConnection();
             PreparedStatement p = conn.prepareStatement(sql)) {
            p.setInt(1, senderId);
            p.setInt(2, userId);
            int rows = p.executeUpdate();
            System.out.println("Accept friend: updated " + rows + " rows");
            return rows > 0;
        }
    }

    public boolean rejectFriendRequest(int userId, int senderId) throws SQLException {
        String sql = "DELETE FROM friends WHERE user_id = ? AND friend_id = ? AND status = 'pending'";
        try (Connection conn = getConnection();
             PreparedStatement p = conn.prepareStatement(sql)) {

            p.setInt(1, senderId);
            p.setInt(2, userId);

            int rows = p.executeUpdate();
            System.out.println("Reject friend: deleted " + rows + " rows (sender=" + senderId + ", receiver=" + userId + ")");
            return rows > 0;
        }
    }

    public boolean updateFriendStatus(int userId, int friendId, String status) throws SQLException {
        String sql = "UPDATE friends SET status = ? WHERE user_id = ? AND friend_id = ?";
        try (Connection conn = getConnection(); PreparedStatement p = conn.prepareStatement(sql)) {
            p.setString(1, status);
            p.setInt(2, userId);
            p.setInt(3, friendId);
            return p.executeUpdate() > 0;
        }
    }

    private boolean userExists(int userId) throws SQLException {
        String sql = "SELECT 1 FROM users WHERE id = ? LIMIT 1";
        try (Connection conn = getConnection(); PreparedStatement p = conn.prepareStatement(sql)) {
            p.setInt(1, userId);
            try (ResultSet rs = p.executeQuery()) {
                return rs.next();
            }
        }
    }

    public List<Integer> getPendingFriendRequests(int userId) throws SQLException {
        // Chính là hàm cũ của bạn, chỉ đổi tên cho đúng chuẩn
        return getPendingRequests(userId);
    }
}