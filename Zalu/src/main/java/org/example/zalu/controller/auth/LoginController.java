package org.example.zalu.controller.auth;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.example.zalu.client.ChatClient;
import org.example.zalu.client.LoginSession;
import org.example.zalu.controller.MainController;
import org.example.zalu.util.AppConstants;

import java.io.IOException;

public class LoginController {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(LoginController.class);

    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private Label statusLabel;
    @FXML
    private StackPane loadingOverlay;
    @FXML
    private ProgressIndicator loadingIndicator;
    @FXML
    private Label loadingLabel;

    private Stage stage;

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    @FXML
    public void initialize() {
        // Tự động tìm kiếm server khi khởi động màn hình login
        findServerInBackground();
    }

    private void findServerInBackground() {
        logger.info("=== Khởi động Auto-Discovery ===");

        new Thread(() -> {
            Platform.runLater(() -> {
                if (statusLabel != null)
                    statusLabel.setText("Đang tìm server trong mạng LAN...");
            });

            logger.info("Bắt đầu quá trình tìm kiếm server tự động...");
            long startTime = System.currentTimeMillis();

            org.example.zalu.client.ServerDiscoveryClient.ServerInfo info = org.example.zalu.client.ServerDiscoveryClient
                    .findServer();

            long elapsedTime = System.currentTimeMillis() - startTime;

            Platform.runLater(() -> {
                if (info != null) {
                    logger.info("=== ✓ Auto-Discovery Thành Công ===");
                    logger.info("Server được tìm thấy: {}:{}", info.address, info.port);
                    logger.info("Thời gian tìm kiếm: {}ms", elapsedTime);

                    ChatClient.setServerConfig(info.address, info.port);

                    if (statusLabel != null) {
                        statusLabel.setText("Đã tìm thấy Server tại: " + info.address);
                        statusLabel.setStyle("-fx-text-fill: green;");
                    }

                    logger.info("Đã cấu hình ChatClient với server: {}:{}", info.address, info.port);
                } else {
                    logger.warn("=== ✗ Auto-Discovery Thất Bại ===");
                    logger.warn("Không tìm thấy server sau {}ms", elapsedTime);
                    logger.warn("Sẽ sử dụng cấu hình mặc định (localhost)");

                    if (statusLabel != null) {
                        statusLabel.setText("Không tìm thấy server trong LAN (dùng localhost)");
                        // Reset style nếu cần
                        statusLabel.setStyle("");
                    }
                }

                logger.info("=== Kết thúc Auto-Discovery ===");
            });
        }, "ServerDiscovery-Thread").start();
    }

    @FXML
    private void login() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            statusLabel.setText("Vui lòng nhập đầy đủ thông tin!");
            return;
        }

        statusLabel.setText("Đang kết nối server...");

        // Hiển thị loading overlay
        showLoading("Đang đăng nhập...");

        LoginSession.setPendingStage(stage);
        LoginSession.setPendingUsername(username);

        // Dùng callback của ChatClient (đã sửa ổn định)
        ChatClient.login(username, password, new ChatClient.LoginCallback() {
            @Override
            public void onSuccess(int userId) {
                Platform.runLater(() -> {
                    hideLoading();
                    statusLabel.setText("Đăng nhập thành công!");
                    logger.info("Login successful for user: {}", username);
                    switchToMain(userId, username);
                });
            }

            @Override
            public void onFail(String message) {
                Platform.runLater(() -> {
                    hideLoading();
                    statusLabel.setText("Đăng nhập thất bại");
                    logger.warn("Login failed for user: {}. Reason: {}", username, message);

                    // Xóa mật khẩu và focus vào ô mật khẩu để người dùng nhập lại
                    passwordField.clear();
                    passwordField.requestFocus();

                    // Hiển thị thông báo lỗi (sau khi xóa password để UX tốt hơn)
                    showAlert(Alert.AlertType.ERROR, "Lỗi đăng nhập", message);
                });
            }
        });
    }

    private void switchToMain(int userId, String username) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/example/zalu/views/main/main-view.fxml"));
            Parent root = loader.load();

            MainController mainController = loader.getController();
            mainController.setStage(stage);
            mainController.setCurrentUserId(userId);
            mainController.setWelcomeUsername(username);

            Scene scene = new Scene(root, org.example.zalu.util.AppConstants.MAIN_WIDTH,
                    org.example.zalu.util.AppConstants.MAIN_HEIGHT);
            stage.setScene(scene);
            stage.setTitle("Zalu - " + username);
            stage.setWidth(org.example.zalu.util.AppConstants.MAIN_WIDTH);
            stage.setHeight(org.example.zalu.util.AppConstants.MAIN_HEIGHT);
            stage.setResizable(true);
            stage.setMinWidth(800);
            stage.setMinHeight(600);
            stage.centerOnScreen();
            stage.show();
        } catch (IOException e) {
            logger.error("Failed to load main view", e);
            showAlert(Alert.AlertType.ERROR, "Lỗi giao diện", "Không tải được màn hình chính: " + e.getMessage());
        }
    }

    @FXML
    private void switchToRegister() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/org/example/zalu/views/auth/register-view.fxml"));
            Parent root = loader.load();
            RegisterController controller = loader.getController();
            controller.setStage(stage);

            Scene scene = new Scene(root, AppConstants.REGISTER_WIDTH, AppConstants.REGISTER_HEIGHT);
            stage.setScene(scene);
            stage.setTitle("Zalu - Đăng ký");
            stage.setResizable(false);
            stage.setMinWidth(AppConstants.REGISTER_WIDTH);
            stage.setMaxWidth(AppConstants.REGISTER_WIDTH);
            stage.setMinHeight(AppConstants.REGISTER_HEIGHT);
            stage.setMaxHeight(AppConstants.REGISTER_HEIGHT);
            stage.setWidth(AppConstants.REGISTER_WIDTH);
            stage.setHeight(AppConstants.REGISTER_HEIGHT);
            stage.centerOnScreen();
            stage.show();
        } catch (IOException e) {
            logger.error("Failed to load register view", e);
            showAlert(Alert.AlertType.ERROR, "Lỗi", "Không mở được form đăng ký");
        }
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private void showLoading(String message) {
        if (loadingOverlay != null) {
            if (loadingLabel != null && message != null) {
                loadingLabel.setText(message);
            }
            loadingOverlay.setVisible(true);
            loadingOverlay.setManaged(true);
            loadingOverlay.toFront();
        }
    }

    private void hideLoading() {
        if (loadingOverlay != null) {
            loadingOverlay.setVisible(false);
            loadingOverlay.setManaged(false);
        }
    }
}