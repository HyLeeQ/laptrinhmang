package org.example.zalu.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.scene.control.PasswordField;
import javafx.stage.Stage;
import org.example.zalu.dao.UserDAO;
import org.example.zalu.model.DBConnection;
import org.example.zalu.model.User;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

public class LoginController {
    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;

    private Stage stage;
    private UserDAO userDAO;

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    @FXML
    private void login() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            System.out.println("Username or password cannot be empty");
            return;
        }

        try {
            Connection connection = DBConnection.getConnection();
            if (connection == null) {
                throw new SQLException("Database connection failed");
            }
            userDAO = new UserDAO(connection);
            System.out.println("Attempting login for username: " + username);

            User user = userDAO.login(username, password);
            if (user != null) {
                int userId = user.getId();
                System.out.println("Login successful for userId: " + userId);

                FXMLLoader loader = new FXMLLoader(getClass().getResource(
                        "/org/example/zalu/views/main-view.fxml"));
                Parent root = loader.load();
                MainController mainController = loader.getController();
                if (mainController == null) {
                    throw new IOException("MainController not loaded from FXML");
                }
                mainController.setStage(stage);
                mainController.setCurrentUserId(userId);
                stage.setScene(new Scene(root, 800, 400));
                stage.setTitle("Chat Application - Main");
                stage.show();
            } else {
                System.out.println("Invalid username or password");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            System.out.println("Database error during login: " + e.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Error loading main view: " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Unexpected error: " + e.getMessage());
        }
    }

    @FXML
    private void switchToRegister() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(
                    "/org/example/zalu/views/register-view.fxml"));
            Parent root = loader.load();
            RegisterController registerController = loader.getController();
            registerController.setStage(stage);
            stage.setScene(new Scene(root, 600, 400));
            stage.setTitle("Chat Application - Register");
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Error loading register view: " + e.getMessage());
        }
    }
}