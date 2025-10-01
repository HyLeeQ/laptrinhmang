package org.example.zalu;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.example.zalu.controller.LoginController;

import java.io.IOException;

public class HelloApplication extends Application {
    @Override
    public void start(Stage primaryStage) throws IOException {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/example/zalu/views/login-view.fxml"));
            Parent root = loader.load();
            LoginController loginController = loader.getController();
            loginController.setStage(primaryStage);

            primaryStage.setTitle("Chat Application - Login");
            primaryStage.setScene(new Scene(root, 600, 400));
            primaryStage.show();
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Failed to load FXML: " + e.getMessage());
        }
    }
}
