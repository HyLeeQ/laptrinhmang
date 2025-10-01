package org.example.zalu.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.scene.control.PasswordField;
import javafx.stage.Stage;
import org.example.zalu.client.ChatClient;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class LoginController {
    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;

    private Stage stage;

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    @FXML
    public void initialize() {
        // Không cần gì
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
            ObjectOutputStream serverOut = ChatClient.getOut();
            if (serverOut == null) {
                System.out.println("Server not connected");
                return;
            }
            String request = "LOGIN_REQUEST|" + username + "|" + password;
            serverOut.writeObject(request);
            serverOut.flush();
            System.out.println("Sent login request: " + request);

            ObjectInputStream serverIn = ChatClient.getIn();
            if (serverIn == null) {
                System.out.println("Cannot read from server");
                return;
            }
            // Đọc sync - an toàn vì chưa có listener
            Object obj = serverIn.readObject();
            if (obj instanceof String) {
                String response = (String) obj;
                System.out.println("Received response: " + response);

                if (response.startsWith("SUCCESS|")) {
                    String[] parts = response.split("\\|");
                    if (parts.length >= 4) {
                        int userId = Integer.parseInt(parts[1]);
                        System.out.println("Login successful for userId: " + userId);

                        // Chuyển scene - MainController sẽ start listener
                        FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/example/zalu/views/main-view.fxml"));
                        Parent root = loader.load();
                        MainController mainController = loader.getController();
                        mainController.setStage(stage);
                        mainController.setCurrentUserId(userId);
                        stage.setScene(new Scene(root, 800, 400));
                        stage.setTitle("Chat Application - Main");
                        stage.show();
                    } else {
                        System.out.println("Invalid response format");
                    }
                } else {
                    System.out.println("Login failed: " + response);
                }
            } else {
                System.out.println("Unexpected response type: " + obj.getClass().getName());
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error during login: " + e.getMessage());
        }
    }

    @FXML
    private void switchToRegister() {
        // Giữ nguyên
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/example/zalu/views/register-view.fxml"));
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