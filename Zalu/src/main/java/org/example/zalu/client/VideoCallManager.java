package org.example.zalu.client;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.ButtonType;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.example.zalu.controller.media.VideoCallController;
import org.example.zalu.model.User;
import org.example.zalu.model.VideoCallSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Optional;

/**
 * Manager quản lý video call từ phía client
 */
public class VideoCallManager {
    private static final Logger logger = LoggerFactory.getLogger(VideoCallManager.class);

    private ObjectOutputStream outputStream;
    private ObjectInputStream inputStream;
    private User currentUser;
    private VideoCallController activeCallController;
    private Stage activeCallStage;

    public VideoCallManager(ObjectOutputStream output, ObjectInputStream input, User currentUser) {
        this.outputStream = output;
        this.inputStream = input;
        this.currentUser = currentUser;
    }

    /**
     * Gửi yêu cầu video call đến user khác
     */
    public void initiateVideoCall(int receiverId, String receiverName) {
        try {
            // Gửi request đến server
            outputStream.writeObject("VIDEO_CALL_REQUEST|" + currentUser.getId() + "|" +
                    currentUser.getFullName() + "|" + receiverId);
            outputStream.flush();

            logger.info("Đã gửi video call request đến user {} ({})", receiverId, receiverName);

            // Show calling dialog
            showCallingDialog(receiverId, receiverName);

        } catch (IOException e) {
            logger.error("Lỗi khi gửi video call request: {}", e.getMessage(), e);
            showErrorDialog("Không thể thực hiện cuộc gọi video");
        }
    }

    /**
     * Hiển thị dialog khi đang gọi (caller side)
     */
    private void showCallingDialog(int receiverId, String receiverName) {
        Platform.runLater(() -> {
            logger.info("Đang gọi đến {}...", receiverName);

            // Tạo custom dialog
            javafx.scene.control.Dialog<ButtonType> dialog = new javafx.scene.control.Dialog<>();
            dialog.setTitle("Video Call");
            dialog.setHeaderText("Đang gọi...");

            // Content
            javafx.scene.layout.VBox content = new javafx.scene.layout.VBox(15);
            content.setPadding(new javafx.geometry.Insets(20));
            content.setAlignment(javafx.geometry.Pos.CENTER);

            // Avatar placeholder (có thể thêm ảnh sau)
            javafx.scene.shape.Circle avatar = new javafx.scene.shape.Circle(50);
            avatar.setFill(javafx.scene.paint.Color.LIGHTBLUE);

            javafx.scene.control.Label nameLabel = new javafx.scene.control.Label(receiverName);
            nameLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

            javafx.scene.control.Label statusLabel = new javafx.scene.control.Label("Đang gọi...");
            statusLabel.setStyle("-fx-text-fill: gray;");

            // Ringing animation
            javafx.scene.control.ProgressIndicator progress = new javafx.scene.control.ProgressIndicator();
            progress.setMaxSize(50, 50);

            content.getChildren().addAll(avatar, nameLabel, statusLabel, progress);
            dialog.getDialogPane().setContent(content);

            // Nút Kết thúc cuộc gọi
            ButtonType endCallButton = new ButtonType("Kết thúc cuộc gọi",
                    javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE);
            dialog.getDialogPane().getButtonTypes().add(endCallButton);

            // Style
            dialog.getDialogPane().setMinWidth(350);

            // Handle end call
            dialog.setOnCloseRequest(e -> {
                try {
                    outputStream.writeObject("VIDEO_CALL_END|" + currentUser.getId() + "|" + receiverId);
                    outputStream.flush();
                    logger.info("Đã hủy cuộc gọi đến {}", receiverName);
                } catch (IOException ex) {
                    logger.error("Lỗi khi hủy cuộc gọi: {}", ex.getMessage());
                }
            });

            // Show non-blocking
            dialog.show();

            // TODO: Auto close dialog khi nhận được ACCEPTED hoặc REJECTED
        });
    }

    /**
     * Nhận cuộc gọi video đến (receiver side)
     */
    public void receiveIncomingCall(int callerId, String callerName) {
        Platform.runLater(() -> {
            logger.info("Nhận cuộc gọi video từ {} (ID: {})", callerName, callerId);

            // Show incoming call dialog
            boolean accepted = showIncomingCallDialog(callerName);

            if (accepted) {
                acceptVideoCall(callerId, callerName);
            } else {
                rejectVideoCall(callerId);
            }
        });
    }

    /**
     * Hiển thị dialog cho cuộc gọi đến
     */
    private boolean showIncomingCallDialog(String callerName) {
        logger.info("Cuộc gọi video đến từ {}", callerName);

        // Tạo custom dialog
        javafx.scene.control.Dialog<ButtonType> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle("Cuộc gọi video đến");
        dialog.setHeaderText(null);

        // Content
        javafx.scene.layout.VBox content = new javafx.scene.layout.VBox(20);
        content.setPadding(new javafx.geometry.Insets(30));
        content.setAlignment(javafx.geometry.Pos.CENTER);
        content.setStyle("-fx-background-color: white;");

        // Phone icon
        javafx.scene.control.Label iconLabel = new javafx.scene.control.Label("📞");
        iconLabel.setStyle("-fx-font-size: 48px;");

        // Caller info
        javafx.scene.control.Label titleLabel = new javafx.scene.control.Label("Cuộc gọi video đến");
        titleLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: gray;");

        javafx.scene.control.Label callerLabel = new javafx.scene.control.Label(callerName);
        callerLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");

        // Animation - ringing effect
        javafx.animation.Timeline timeline = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(javafx.util.Duration.seconds(0),
                        new javafx.animation.KeyValue(iconLabel.scaleXProperty(), 1),
                        new javafx.animation.KeyValue(iconLabel.scaleYProperty(), 1)),
                new javafx.animation.KeyFrame(javafx.util.Duration.seconds(0.5),
                        new javafx.animation.KeyValue(iconLabel.scaleXProperty(), 1.2),
                        new javafx.animation.KeyValue(iconLabel.scaleYProperty(), 1.2)));
        timeline.setCycleCount(javafx.animation.Animation.INDEFINITE);
        timeline.setAutoReverse(true);
        timeline.play();

        content.getChildren().addAll(iconLabel, titleLabel, callerLabel);
        dialog.getDialogPane().setContent(content);

        // Buttons
        ButtonType acceptButton = new ButtonType("✓ Chấp nhận", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        ButtonType rejectButton = new ButtonType("✗ Từ chối", javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(acceptButton, rejectButton);

        // Style buttons
        dialog.getDialogPane().setMinWidth(400);
        dialog.getDialogPane().setMinHeight(250);

        // Get button nodes and style them
        Platform.runLater(() -> {
            javafx.scene.Node acceptBtn = dialog.getDialogPane().lookupButton(acceptButton);
            acceptBtn.setStyle(
                    "-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-size: 14px; -fx-padding: 10 30;");

            javafx.scene.Node rejectBtn = dialog.getDialogPane().lookupButton(rejectButton);
            rejectBtn.setStyle(
                    "-fx-background-color: #f44336; -fx-text-fill: white; -fx-font-size: 14px; -fx-padding: 10 30;");
        });

        // Show and wait
        Optional<ButtonType> result = dialog.showAndWait();
        timeline.stop();

        return result.isPresent() && result.get() == acceptButton;
    }

    /**
     * Chấp nhận cuộc gọi video
     */
    private void acceptVideoCall(int callerId, String callerName) {
        try {
            // Gửi accept đến server
            outputStream.writeObject("VIDEO_CALL_ACCEPT|" + currentUser.getId() + "|" +
                    currentUser.getFullName() + "|" + callerId);
            outputStream.flush();

            logger.info("Đã chấp nhận cuộc gọi từ {}", callerName);

            // Open video call window
            openVideoCallWindow(callerId, callerName, false);

        } catch (IOException e) {
            logger.error("Lỗi khi chấp nhận video call: {}", e.getMessage(), e);
        }
    }

    /**
     * Từ chối cuộc gọi video
     */
    private void rejectVideoCall(int callerId) {
        try {
            outputStream.writeObject("VIDEO_CALL_REJECT|" + currentUser.getId() + "|" + callerId);
            outputStream.flush();

            logger.info("Đã từ chối cuộc gọi từ user {}", callerId);

        } catch (IOException e) {
            logger.error("Lỗi khi từ chối video call: {}", e.getMessage(), e);
        }
    }

    /**
     * Xử lý khi cuộc gọi được chấp nhận (caller side)
     */
    public void onCallAccepted(int receiverId, String receiverName) {
        Platform.runLater(() -> {
            logger.info("{} đã chấp nhận cuộc gọi", receiverName);

            // Open video call window
            openVideoCallWindow(receiverId, receiverName, true);
        });
    }

    /**
     * Xử lý khi cuộc gọi bị từ chối
     */
    public void onCallRejected(int receiverId) {
        Platform.runLater(() -> {
            logger.info("Cuộc gọi bị từ chối bởi user {}", receiverId);
            showInfoDialog("Cuộc gọi bị từ chối", "Người dùng đã từ chối cuộc gọi video của bạn.");
        });
    }

    /**
     * Xử lý khi cuộc gọi kết thúc
     */
    public void onCallEnded(int otherUserId) {
        Platform.runLater(() -> {
            logger.info("Cuộc gọi với user {} đã kết thúc", otherUserId);

            // Close video call window if open
            if (activeCallStage != null && activeCallStage.isShowing()) {
                activeCallStage.close();
            }
        });
    }

    /**
     * Mở cửa sổ video call
     */
    private void openVideoCallWindow(int otherUserId, String otherUserName, boolean isCaller) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/org/example/zalu/view/videocall.fxml"));

            Scene scene = new Scene(loader.load());

            Stage stage = new Stage();
            stage.setTitle("Video Call - " + otherUserName);
            stage.initModality(Modality.NONE);
            stage.setScene(scene);

            // Get controller and initialize
            VideoCallController controller = loader.getController();
            controller.setStage(stage);

            // Create session
            VideoCallSession session;
            if (isCaller) {
                session = new VideoCallSession(
                        currentUser.getId(), currentUser.getFullName(),
                        otherUserId, otherUserName);
            } else {
                session = new VideoCallSession(
                        otherUserId, otherUserName,
                        currentUser.getId(), currentUser.getFullName());
            }
            session.setStatus(VideoCallSession.CallStatus.CONNECTED);

            // Start video call
            controller.startVideoCall(session, outputStream, inputStream);

            // Save references
            activeCallController = controller;
            activeCallStage = stage;

            stage.show();

            logger.info("Đã mở cửa sổ video call với {}", otherUserName);

        } catch (IOException e) {
            logger.error("Lỗi khi mở cửa sổ video call: {}", e.getMessage(), e);
            showErrorDialog("Không thể mở cửa sổ video call");
        }
    }

    /**
     * Kết thúc cuộc gọi
     */
    public void endCall(int otherUserId) {
        try {
            outputStream.writeObject("VIDEO_CALL_END|" + currentUser.getId() + "|" + otherUserId);
            outputStream.flush();

            logger.info("Đã gửi tín hiệu kết thúc cuộc gọi");

        } catch (IOException e) {
            logger.error("Lỗi khi kết thúc cuộc gọi: {}", e.getMessage(), e);
        }
    }

    // Helper methods for dialogs
    private Optional<javafx.scene.control.ButtonType> showConfirmDialog(String title, String message) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        return alert.showAndWait();
    }

    private void showInfoDialog(String title, String message) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showErrorDialog(String message) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.ERROR);
        alert.setTitle("Lỗi");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
