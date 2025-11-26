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
import org.example.zalu.util.license.LicenseValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class ZaluApplication extends Application {
    private static final Logger logger = LoggerFactory.getLogger(ZaluApplication.class);

    @Override
    public void start(Stage primaryStage) {
        // ============================================
        // BẢO VỆ LICENSE - CHỈ CHẠY KHI CÓ LICENSE SERVER
        // ============================================
        logger.info("\n" + "=".repeat(50));
        logger.info("KIỂM TRA LICENSE - BẢO VỆ CODE");
        logger.info("=".repeat(50));
        
        // Kiểm tra license trước khi chạy ứng dụng
        if (!LicenseValidator.validateLicense()) {
            logger.error("\n" + "=".repeat(50));
            logger.error("❌ CODE KHÔNG ĐƯỢC PHÉP CHẠY!");
            logger.error("   Không thể xác thực license với License Server");
            logger.error("   Code này chỉ hoạt động khi có License Server của bạn");
            logger.error("   Vui lòng đảm bảo License Server đang chạy trên máy của bạn");
            logger.error("=".repeat(50) + "\n");
            System.exit(1);
        }
        
        // 1. Kết nối server trước (bắt buộc)
        if (!ChatClient.connectToServer()) {
            logger.error("✗ Không thể kết nối tới server! Kiểm tra cấu hình trong server.properties hoặc đảm bảo server đang chạy.");
            System.exit(1);
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/example/zalu/views/auth/login-view.fxml"));
            Parent root = loader.load();

            LoginController loginController = loader.getController();
            loginController.setStage(primaryStage);

            Scene scene = new Scene(root, AppConstants.LOGIN_WIDTH, AppConstants.LOGIN_HEIGHT);

            primaryStage.setTitle("Zalu - Đăng nhập");
            primaryStage.setScene(scene);

            // CỐ ĐỊNH SIZE HOÀN TOÀN CHO MÀN LOGIN (KHÔNG BAO GIỜ ĐỔI!)
            primaryStage.setResizable(false);
            primaryStage.setMinWidth(AppConstants.LOGIN_WIDTH);
            primaryStage.setMaxWidth(AppConstants.LOGIN_WIDTH);
            primaryStage.setMinHeight(AppConstants.LOGIN_HEIGHT);
            primaryStage.setMaxHeight(AppConstants.LOGIN_HEIGHT);
            primaryStage.centerOnScreen();  // Hiện giữa màn hình luôn đẹp

            primaryStage.show();

            logger.info("✓ Zalu Client đã khởi động thành công!");
            logger.info("   Kích thước cố định: {} x {}", AppConstants.LOGIN_WIDTH, AppConstants.LOGIN_HEIGHT);
            logger.info("   Kết nối server: OK");

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
        launch(args);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("App đang tắt → disconnect sạch");
            ChatClient.disconnect();
        }));
    }
}
