package org.example.zalu.controller;

import org.example.zalu.model.*;
import org.example.zalu.dao.UserDAO;
import org.example.zalu.dao.FriendDAO;
import org.example.zalu.dao.MessageDAO;
import org.example.zalu.dao.VoiceMessageDAO;

import java.sql.Connection;
import java.sql.SQLException;

public class Main {
    public static void main(String[] args) {
        try {
            // Lấy connection từ DBConnection
            Connection connection = DBConnection.getConnection();

            // Khởi tạo DAO với connection
            UserDAO userDAO = new UserDAO(connection);
            FriendDAO friendDAO = new FriendDAO(connection);
            MessageDAO messageDAO = new MessageDAO(connection);
            VoiceMessageDAO voiceMessageDAO = new VoiceMessageDAO(connection);

//            // Đăng ký user 1
//            User user1 = new User(0, "user1", "pass123", "user1@example.com", "offline");
//            if (userDAO.register(user1)) {
//                System.out.println("Đăng ký user1 thành công!");
//            } else {
//                System.out.println("Đăng ký user1 thất bại!");
//            }
//
//            // Đăng ký user 2
//            User user2 = new User(0, "user2", "pass123", "user2@example.com", "offline");
//            if (userDAO.register(user2)) {
//                System.out.println("Đăng ký user2 thành công!");
//            } else {
//                System.out.println("Đăng ký user2 thất bại!");
//            }

//            // Thêm mối quan hệ bạn bè
//            Friend newFriend = new Friend(5, 6, "pending"); // Sử dụng id của user1 và user2
//            if (friendDAO.saveFriend(newFriend)) {
//                System.out.println("Thêm bạn thành công!");
//            } else {
//                System.out.println("Thêm bạn thất bại!");
//            }

//            // Test Message
//            Message newMessage = new Message(0, 5, 6, "Hello!", LocalDateTime.now());
//            if (messageDAO.saveMessage(newMessage)) {
//                System.out.println("Gửi tin nhắn thành công!");
//            } else {
//                System.out.println("Gửi tin nhắn thất bại!");
//            }
//
//            // Test VoiceMessage
//            VoiceMessage newVoiceMessage = new VoiceMessage(0, 5, 6, "/path/to/audio.mp3", LocalDateTime.now());
//            if (voiceMessageDAO.saveVoiceMessage(newVoiceMessage)) {
//                System.out.println("Gửi tin nhắn thoại thành công!");
//            } else {
//                System.out.println("Gửi tin nhắn thoại thất bại!");
//            }

        } catch (SQLException e) {
            System.out.println("Lỗi CSDL: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Lỗi không xác định: " + e.getMessage());
        }
    }
}