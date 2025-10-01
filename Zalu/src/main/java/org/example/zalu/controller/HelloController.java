package org.example.zalu.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class HelloController {
    private Stage stage;

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    @FXML
    private void goToLogin() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/example/zalu/views/login-view.fxml"));
            Parent root = loader.load();
            Scene scene = new Scene(root, 400, 300);
            stage.setScene(scene);
            stage.setTitle("Chat Application - Login");
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Error loading login view: " + e.getMessage());
            // Hiển thị thông báo lỗi cho người dùng (tùy chọn)
            System.out.println("Failed to load login page. Check FXML file path.");
        }
    }
}