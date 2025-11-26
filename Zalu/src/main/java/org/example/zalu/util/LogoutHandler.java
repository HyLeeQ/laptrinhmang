package org.example.zalu.util;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.example.zalu.client.ChatClient;
import org.example.zalu.client.ChatEventManager;
import org.example.zalu.controller.MainController;
import org.example.zalu.controller.chat.MessageListController;
import org.example.zalu.controller.auth.LoginController;

import java.io.IOException;
import java.net.URL;

public class LogoutHandler {
    private final Stage stage;
    private final MainController mainController;
    private final MessageListController messageListController;

    public LogoutHandler(Stage stage, MainController mainController, MessageListController messageListController) {
        this.stage = stage;
        this.mainController = mainController;
        this.messageListController = messageListController;
    }

    // SỬA: @FXML để FXML có thể gọi trực tiếp nếu cần (backup nếu MainController miss)
    @FXML
    public void performLogout() {
        System.out.println("Logout initiated - cleaning resources...");
        try {
            // Clean data
            if (mainController != null) {
                mainController.resetDataFlags();
            }
            ChatEventManager.getInstance().unregisterAllCallbacks();
            ChatEventManager.getInstance().stopListening();
            ChatClient.disconnect();

            // Add system message nếu controller OK (tránh NPE)
            if (messageListController != null) {
                messageListController.addSystemMessage("Đăng xuất thành công. Tạm biệt!");
            } else {
                System.out.println("MessageListController null - skipping system message");
            }

            // Switch to login scene
            switchScene("/org/example/zalu/views/auth/login-view.fxml");
            System.out.println("Logout completed - switched to login view");

        } catch (Exception e) {
            System.err.println("Error during logout: " + e.getMessage());
            e.printStackTrace();
            // Fallback: Force exit nếu fail
            if (messageListController != null) {
                messageListController.addSystemMessage("Lỗi đăng xuất: " + e.getMessage() + ". App sẽ đóng.");
            }
            Platform.exit();
        }
    }

    // SỬA: switchScene - thêm debug log, check fxmlUrl null chi tiết
    private void switchScene(String fxmlPath) throws IOException {
        if (stage == null) {
            throw new IllegalStateException("Stage is not initialized - cannot switch scene");
        }

        URL fxmlUrl = getClass().getResource(fxmlPath);
        if (fxmlUrl == null) {
            throw new IOException("FXML file not found: " + fxmlPath + ". Check path in resources/org/example/zalu/views/. Current classpath root: " + getClass().getResource("/"));
        }
        System.out.println("Switching to FXML: " + fxmlPath + " at URL: " + fxmlUrl);

        FXMLLoader loader = new FXMLLoader(fxmlUrl);
        Parent root = loader.load();

        Object controller = loader.getController();
        if (controller instanceof LoginController) {
            ((LoginController) controller).setStage(stage);
            System.out.println("LoginController initialized for new scene");
        } else {
            System.out.println("Controller is " + (controller != null ? controller.getClass().getName() : "null"));
        }

        Scene scene = new Scene(root, 800, 400);
        stage.setScene(scene);
        stage.setTitle("Zalu - Đăng Nhập");
        stage.show();
    }
}