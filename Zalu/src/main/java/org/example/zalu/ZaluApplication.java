package org.example.zalu;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.example.zalu.client.ChatClient;
import org.example.zalu.controller.auth.LoginController;
import org.example.zalu.util.AppConstants;
import org.example.zalu.util.database.DBConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class ZaluApplication extends Application {
    private static final Logger logger = LoggerFactory.getLogger(ZaluApplication.class);

    @Override
    public void start(Stage primaryStage) {
        logger.info("=== Khởi động Zalu Client ===");
        logger.info("⚠ License check đã bị vô hiệu hóa – dùng cho môi trường phát triển.");

        // KHÔNG kết nối server ngay lập tức
        // UDP Auto-Discovery sẽ tìm server tự động trong LoginController
        // Client sẽ kết nối khi cần (khi login)
        logger.info("ℹ Sẽ tìm kiếm server tự động qua UDP Discovery...");

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/example/zalu/views/auth/login-view.fxml"));
            Parent root = loader.load();

            LoginController loginController = loader.getController();
            loginController.setStage(primaryStage);

            Scene scene = new Scene(root, AppConstants.LOGIN_WIDTH, AppConstants.LOGIN_HEIGHT);

            primaryStage.setTitle("Zalu - Đăng nhập");
            primaryStage.setScene(scene);

            // Cho phép resize nhưng giới hạn kích thước tối thiểu
            primaryStage.setResizable(false); // Giữ login cố định
            primaryStage.setMinWidth(AppConstants.LOGIN_WIDTH);
            primaryStage.setMaxWidth(AppConstants.LOGIN_WIDTH);
            primaryStage.setMinHeight(AppConstants.LOGIN_HEIGHT);
            primaryStage.setMaxHeight(AppConstants.LOGIN_HEIGHT);
            primaryStage.centerOnScreen(); // Hiện giữa màn hình luôn đẹp

            primaryStage.show();

            logger.info("✓ Zalu Client đã khởi động thành công!");
            logger.info("   Kích thước cố định: {} x {}", AppConstants.LOGIN_WIDTH, AppConstants.LOGIN_HEIGHT);
            logger.info("   UDP Auto-Discovery: ENABLED");

        } catch (IOException e) {
            logger.error("✗ Lỗi tải giao diện Login: {}", e.getMessage(), e);
        }
    }

    @Override
    public void stop() throws Exception {
        ChatClient.disconnect();
        DBConnection.closePool();
        logger.info("Ứng dụng Zalu đã tắt hoàn toàn – Hẹn gặp lại!");
        super.stop();
    }

    public static void main(String[] args) {
        // Đăng ký Global Exception Handler
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            logger.error("⚠ LỖI KHÔNG MONG MUỐN (UNCAUGHT EXCEPTION): {}", throwable.getMessage(), throwable);
            // Gửi báo cáo lỗi về server
            ChatClient.sendErrorReport(throwable);
        });

        launch(args);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("App đang tắt → disconnect sạch");
            ChatClient.disconnect();
        }));
    }
}
