package org.example.zalu.controller.chat;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import org.example.zalu.controller.MainController;

import java.io.IOException;


public class WelcomeController {
    @FXML
    private Label welcomeLabel;
    @FXML
    private Label userInfoLabel;
    @FXML
    private Button goToMainButton;

    private Stage stage;
    private String userName;
    private int userId;
    private boolean isEmbedded = false; // Flag để biết có đang được embed trong main view không

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public void setUserInfo(String userName, int userId) {
        this.userName = userName;
        this.userId = userId;
        if (userInfoLabel != null) {
            userInfoLabel.setText("Tài khoản: " + userName);
        }
    }

    /**
     * Đặt flag để biết view này đang được embed trong main view
     * Nếu là embedded, sẽ ẩn button "Vào ứng dụng chính"
     */
    public void setEmbedded(boolean embedded) {
        this.isEmbedded = embedded;
        if (goToMainButton != null) {
            goToMainButton.setVisible(!embedded);
            goToMainButton.setManaged(!embedded);
        }
    }

    @FXML
    private void switchToMain(){
        // Nếu đã được embed trong main view, không làm gì
        if (isEmbedded) {
            return;
        }
        
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/example/zalu/views/main/main-view.fxml"));
            Parent root = loader.load();
            MainController mainController = loader.getController();
            mainController.setStage(stage);
            mainController.setCurrentUserId(userId);

            stage.setScene(new Scene(root, 800, 600));
            stage.setTitle("Chat Application - Main");
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
