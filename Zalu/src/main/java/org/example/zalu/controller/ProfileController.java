package org.example.zalu.controller;

import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import org.example.zalu.model.DBConnection;
import javafx.scene.text.Text;
import org.example.zalu.model.User;
import org.example.zalu.dao.UserDAO;


import java.sql.Connection;
import java.sql.SQLException;

public class ProfileController {
    @FXML
    private Text usernameLabel;
    @FXML
    private Text emailLabel;
    @FXML
    private Text statusLabel;
    @FXML
    private TextField newPasswordField;

    private UserDAO userDAO;
    private User currentUser;

    @FXML
    public void initialize() {
        try {
            Connection connection = DBConnection.getConnection();
            if (connection == null) throw new SQLException("Database connection failed");
            userDAO = new UserDAO(connection);
            // Load thông tin user hiện tại (giả định ID 1, có thể thay bằng ID từ session)
            currentUser = userDAO.getUserById(1); // Sửa lỗi: giờ có phương thức này
            if (currentUser != null) {
                usernameLabel.setText("Username: " + currentUser.getUsername());
                emailLabel.setText("Email: " + currentUser.getEmail());
                statusLabel.setText("Status: " + currentUser.getStatus());
            } else {
                System.out.println("Không tìm thấy user với ID 1");
            }
        } catch (SQLException e) {
            System.out.println("Error loading profile: " + e.getMessage());
        }
    }

    @FXML
    private void updateProfile() {
        String newPassword = newPasswordField.getText().trim();
        if (!newPassword.isEmpty()) {
            if (currentUser != null) {
                currentUser.setPassword(newPassword);
                try {
                    if (userDAO.updateUser(currentUser)) {
                        System.out.println("Cập nhật thành công!");
                    } else {
                        System.out.println("Cập nhật thất bại!");
                    }
                } catch (SQLException e) {
                    System.out.println("Error updating: " + e.getMessage());
                }
            } else {
                System.out.println("Không có user để cập nhật!");
            }
        } else {
            System.out.println("Mật khẩu mới không được để trống!");
        }
    }
}
