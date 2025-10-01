package org.example.zalu.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.example.zalu.model.DBConnection;
import org.example.zalu.model.User;
import org.example.zalu.dao.UserDAO;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

public class RegisterController {
    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private TextField emailField;

    private UserDAO userDAO;
    private Stage stage;

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    @FXML
    public void initialize() {
        try {
            Connection connection = DBConnection.getConnection();
            if (connection == null) throw new Exception("Database connection failed");
            userDAO = new UserDAO(connection);
        } catch (Exception e) {
            System.out.println("Error initializing UserDAO: " + e.getMessage());
        }
    }

    @FXML
    private void register() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText().trim();
        String email = emailField.getText().trim();
        if (username.isEmpty() || password.isEmpty() || email.isEmpty()) {
            System.out.println("All fields are required");
            return;
        }
        try {
            User newUser = new User(0, username, password, email, "offline");
            if (userDAO.register(newUser)) {
                System.out.println("Đăng ký thành công!");
                switchToLogin(); // Chuyển sang đăng nhập
            } else {
                System.out.println("Đăng ký thất bại!");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            System.out.println("Database error during registration: " + e.getMessage());
        }
    }

    @FXML
    private void switchToLogin() {
        if (stage == null) {
            System.out.println("Stage is null, cannot switch to login view");
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/example/zalu/views/login-view.fxml"));
            Parent root = loader.load();
            Scene scene = new Scene(root, 600, 400);
            stage.setScene(scene);
            stage.setTitle("Chat Application - Login");
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Error loading login view: " + e.getMessage());
        }
    }
}