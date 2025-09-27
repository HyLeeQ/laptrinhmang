package org.example.zalu.model.dao;

import org.example.zalu.model.DBConnection;
import org.example.zalu.model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class UserDAO {
    public boolean register(User user){
        String sql = "INSERT INTO users(username, password, email) VALUES (?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, user.getUsername());
            stmt.setString(2, user.getPassword());
            stmt.setString(3, user.getEmail());
            return stmt.executeUpdate() > 0;
        }catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    public User login(String username, String password){
        String sql = "SELECT * FROM users WHERE username=? AND password=?";
        try(Connection conn = DBConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql)){
            stmt.setString(1, username);
            stmt.setString(2, password);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return new User(rs.getInt("id"), rs.getString("username"),
                        rs.getString("password"), rs.getString("email"));
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }
}
