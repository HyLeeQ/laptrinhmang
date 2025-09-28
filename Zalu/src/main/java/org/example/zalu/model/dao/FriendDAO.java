package org.example.zalu.model.dao;

import org.example.zalu.model.Friend;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class FriendDAO {
    private Connection connection;

    public  FriendDAO(Connection connection){
        this.connection = connection;
    }

    public boolean saveFriend(Friend friend) throws SQLException{
        String sql = "INSERT INTO friends (user_id, friend_id, status) VALUES (?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)){
            pstmt.setInt(1, friend.getUserId());
            pstmt.setInt(2, friend.getFriendId());
            pstmt.setString(3, friend.getStatus());
            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;
        }
    }
    public Friend findFriendById(int userId, int friendId) throws SQLException {
        String sql = "SELECT * FROM friends WHERE user_id = ? AND friend_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)){
            pstmt.setInt(1, userId);
            pstmt.setInt(2, friendId);
            try(ResultSet rs = pstmt.executeQuery()){
                if (rs.next()){
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
}
