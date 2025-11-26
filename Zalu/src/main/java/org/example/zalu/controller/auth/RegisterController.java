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
import org.example.zalu.client.ChatEventManager;
import org.example.zalu.exception.validation.InvalidInputException;
import org.example.zalu.exception.validation.ValidationException;
import org.example.zalu.exception.connection.ServerConnectionException;
import org.example.zalu.util.AppConstants;

import java.io.IOException;
import java.util.regex.Pattern;

public class RegisterController {
    @FXML private TextField usernameField;
    @FXML private TextField fullNameField;
    @FXML private PasswordField passwordField;
    @FXML private TextField emailField;
    @FXML private TextField phoneField;
    @FXML private Label statusLabel;
    @FXML private StackPane loadingOverlay;
    @FXML private ProgressIndicator loadingIndicator;
    @FXML private Label loadingLabel;

    private Stage stage;

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    @FXML
    public void initialize() {
        if (statusLabel != null) {
            statusLabel.setText("Nhập thông tin để đăng ký");
        }
    }

    @FXML
    private void register() {
        String username = usernameField.getText().trim();
        String fullName = fullNameField.getText().trim();
        String password = passwordField.getText().trim();
        String email = emailField.getText().trim();
        String phone = phoneField.getText().trim();

        // Validation với exceptions
        try {
            validateInput(username, fullName, password, email, phone);
        } catch (ValidationException e) {
            showAlert(Alert.AlertType.WARNING, "Lỗi", e.getMessage());
            return;
        } catch (InvalidInputException e) {
            showAlert(Alert.AlertType.WARNING, "Lỗi", e.getMessage());
            return;
        }

        statusLabel.setText("Đang gửi yêu cầu đăng ký...");
        disableFields(true);
        
        // Hiển thị loading overlay
        showLoading("Đang đăng ký tài khoản...");

        // Đảm bảo đã kết nối trước khi gửi request
        try {
            if (!ChatClient.isConnected() && !ChatClient.connectToServer()) {
                throw new ServerConnectionException("Không thể kết nối tới server. Vui lòng kiểm tra lại kết nối mạng/server.");
            }
        } catch (ServerConnectionException e) {
            hideLoading();
            statusLabel.setText("Không thể kết nối tới server");
            showAlert(Alert.AlertType.ERROR, "Lỗi", e.getMessage());
            disableFields(false);
            return;
        }

        // Bắt đầu listener nếu chưa chạy (để nhận phản hồi đăng ký)
        ChatClient.startGlobalListener();

        // Gửi yêu cầu đăng ký bằng ChatClient
        try {
            String request = "REGISTER_REQUEST|" + username + "|" + fullName + "|" + password + "|" + email + "|" + phone;
            ChatClient.sendRequest(request);
        } catch (Exception e) {
            hideLoading();
            statusLabel.setText("Lỗi khi gửi yêu cầu");
            showAlert(Alert.AlertType.ERROR, "Lỗi", "Không thể gửi yêu cầu đăng ký: " + e.getMessage());
            disableFields(false);
            return;
        }

        // Đăng ký lắng nghe phản hồi từ server (xử lý ngay khi nhận được, không chờ timeout)
        ChatEventManager.getInstance().registerErrorCallback(msg -> {
            if (msg.startsWith("REGISTER_RESPONSE|")) {
                Platform.runLater(() -> {
                    hideLoading();
                    if (msg.contains("SUCCESS")) {
                        statusLabel.setText("Đăng ký thành công!");
                        showAlert(Alert.AlertType.INFORMATION, "Thành công", "Tài khoản đã được tạo!\nBây giờ bạn có thể đăng nhập.");
                        disableFields(false);
                        // Unregister callback trước khi chuyển màn hình
                        ChatEventManager.getInstance().unregisterAllCallbacks();
                        switchToLogin();
                    } else if (msg.contains("FAIL")) {
                        String error = msg.contains("|FAIL|") 
                            ? msg.substring(msg.indexOf("|FAIL|") + 6) 
                            : "Đăng ký thất bại";
                        statusLabel.setText("Đăng ký thất bại");
                        showAlert(Alert.AlertType.ERROR, "Thất bại", error);
                        disableFields(false);
                        // Xóa các field để người dùng nhập lại (trừ username nếu trùng)
                        if (error.contains("Tên đăng nhập") || error.contains("username")) {
                            usernameField.clear();
                            usernameField.requestFocus();
                        } else {
                            passwordField.clear();
                            emailField.clear();
                            phoneField.clear();
                            passwordField.requestFocus();
                        }
                        // Unregister callback sau khi xử lý
                        ChatEventManager.getInstance().unregisterAllCallbacks();
                    }
                });
            }
        });
        
        // Timeout fallback: Nếu sau 10 giây không nhận được response, hiển thị lỗi
        new Thread(() -> {
            try {
                Thread.sleep(10000);
                Platform.runLater(() -> {
                    // Kiểm tra xem đã nhận response chưa (bằng cách kiểm tra status label)
                    if (statusLabel.getText().equals("Đang gửi yêu cầu đăng ký...")) {
                        hideLoading();
                        statusLabel.setText("Timeout - Không nhận được phản hồi");
                        showAlert(Alert.AlertType.ERROR, "Lỗi", "Server không phản hồi. Vui lòng thử lại!");
                        disableFields(false);
                        ChatEventManager.getInstance().unregisterAllCallbacks();
                    }
                });
            } catch (InterruptedException ignored) {}
        }).start();
    }

    private void validateInput(String username, String fullName, String password, String email, String phone) 
            throws ValidationException, InvalidInputException {
        // Kiểm tra các trường bắt buộc
        if (username.isEmpty() || fullName.isEmpty() || password.isEmpty() || email.isEmpty() || phone.isEmpty()) {
            throw new ValidationException("Vui lòng nhập đầy đủ thông tin!");
        }
        
        // Kiểm tra định dạng email
        if (!isValidEmail(email)) {
            throw new InvalidInputException("Email không hợp lệ!");
        }
        
        // Kiểm tra định dạng số điện thoại
        if (!isValidPhone(phone)) {
            throw new InvalidInputException("Số điện thoại phải 10-11 chữ số!");
        }
        
        // Kiểm tra độ dài mật khẩu
        if (password.length() < 6) {
            throw new InvalidInputException("Mật khẩu phải ít nhất 6 ký tự!");
        }
    }

    private boolean isValidEmail(String email) {
        String regex = "^[A-Za-z0-9+_.-]+@(.+)$";
        return Pattern.compile(regex).matcher(email).matches();
    }

    private boolean isValidPhone(String phone) {
        String regex = "^[0-9]{10,11}$";
        return Pattern.compile(regex).matcher(phone).matches();
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private void disableFields(boolean disable) {
        usernameField.setDisable(disable);
        fullNameField.setDisable(disable);
        passwordField.setDisable(disable);
        emailField.setDisable(disable);
        phoneField.setDisable(disable);
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

    @FXML
    private void switchToLogin() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/example/zalu/views/auth/login-view.fxml"));
            Parent root = loader.load();
            org.example.zalu.controller.auth.LoginController controller = loader.getController();
            controller.setStage(stage);
            Scene scene = new Scene(root, AppConstants.LOGIN_WIDTH, AppConstants.LOGIN_HEIGHT);
            stage.setScene(scene);
            stage.setTitle("Zalu - Đăng nhập");
            stage.setResizable(false);
            stage.setMinWidth(AppConstants.LOGIN_WIDTH);
            stage.setMaxWidth(AppConstants.LOGIN_WIDTH);
            stage.setMinHeight(AppConstants.LOGIN_HEIGHT);
            stage.setMaxHeight(AppConstants.LOGIN_HEIGHT);
            stage.setWidth(AppConstants.LOGIN_WIDTH);
            stage.setHeight(AppConstants.LOGIN_HEIGHT);
            stage.centerOnScreen();
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Lỗi", "Không mở được trang đăng nhập");
        }
    }
}