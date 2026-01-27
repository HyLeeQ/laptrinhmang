/*
 * EXAMPLE: TÍCH HỢP VIDEO CALL VÀO MAIN CONTROLLER
 * 
 * File này là ví dụ về cách tích hợp VideoCallManager vào MainController.java hoặc ChatController.java
 * Sao chép các đoạn code cần thiết và điều chỉnh theo cấu trúc của bạn
 */

package org.example.zalu.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import org.example.zalu.client.VideoCallManager;
import org.example.zalu.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class MainControllerExample {
    private static final Logger logger = LoggerFactory.getLogger(MainControllerExample.class);

    // ============ THÊM FIELDS ============
    private VideoCallManager videoCallManager;
    private User currentUser;
    private User selectedFriend; // User hiện đang được chọn trong chat

    @FXML
    private Button videoCallButton;

    // ============ INITIALIZATION ============

    /**
     * Khởi tạo VideoCallManager sau khi đăng nhập thành công
     * Gọi method này trong handleLoginSuccess() hoặc tương tự
     */
    private void initializeVideoCall(ObjectOutputStream output, ObjectInputStream input, User user) {
        this.currentUser = user;
        this.videoCallManager = new VideoCallManager(output, input, user);
        logger.info("VideoCallManager đã được khởi tạo cho user: {}", user.getUsername());

        // Enable video call button
        if (videoCallButton != null) {
            videoCallButton.setDisable(false);
        }
    }

    // ============ UI HANDLERS ============

    /**
     * Xử lý khi user click nút Video Call
     * Thêm button này vào chat header hoặc friend info panel
     */
    @FXML
    private void handleVideoCallButton() {
        if (selectedFriend == null) {
            showAlert("Chưa chọn bạn bè", "Vui lòng chọn một người bạn để thực hiện video call.");
            return;
        }

        if (videoCallManager == null) {
            showAlert("Lỗi", "Video call manager chưa được khởi tạo.");
            return;
        }

        logger.info("Initiating video call to: {}", selectedFriend.getFullName());
        videoCallManager.initiateVideoCall(
                selectedFriend.getId(),
                selectedFriend.getFullName());
    }

    // ============ SERVER MESSAGE HANDLERS ============

    /**
     * Xử lý các message từ server liên quan đến video call
     * Thêm các case này vào method xử lý server response hiện tại
     * (thường trong một listener thread đọc từ ObjectInputStream)
     */
    private void handleServerResponse(String response) {
        try {
            // ... existing handlers for other messages ...

            if (response.startsWith("VIDEO_CALL_INCOMING|")) {
                handleIncomingCall(response);
            } else if (response.startsWith("VIDEO_CALL_ACCEPTED|")) {
                handleCallAccepted(response);
            } else if (response.startsWith("VIDEO_CALL_REJECTED|")) {
                handleCallRejected(response);
            } else if (response.startsWith("VIDEO_CALL_ENDED|")) {
                handleCallEnded(response);
            } else if (response.startsWith("VIDEO_CALL_REQUEST|")) {
                handleCallRequestResponse(response);
            }

            // ... rest of existing handlers ...

        } catch (Exception e) {
            logger.error("Error handling server response: {}", e.getMessage(), e);
        }
    }

    /**
     * Xử lý khi có cuộc gọi đến
     * Format: VIDEO_CALL_INCOMING|callerId|callerName
     */
    private void handleIncomingCall(String message) {
        String[] parts = message.split("\\|");
        if (parts.length >= 3) {
            int callerId = Integer.parseInt(parts[1]);
            String callerName = parts[2];

            logger.info("Incoming call from: {} (ID: {})", callerName, callerId);

            if (videoCallManager != null) {
                videoCallManager.receiveIncomingCall(callerId, callerName);
            }
        }
    }

    /**
     * Xử lý khi cuộc gọi được chấp nhận
     * Format: VIDEO_CALL_ACCEPTED|receiverId|receiverName
     */
    private void handleCallAccepted(String message) {
        String[] parts = message.split("\\|");
        if (parts.length >= 3) {
            int receiverId = Integer.parseInt(parts[1]);
            String receiverName = parts[2];

            logger.info("Call accepted by: {} (ID: {})", receiverName, receiverId);

            if (videoCallManager != null) {
                videoCallManager.onCallAccepted(receiverId, receiverName);
            }
        }
    }

    /**
     * Xử lý khi cuộc gọi bị từ chối
     * Format: VIDEO_CALL_REJECTED|receiverId
     */
    private void handleCallRejected(String message) {
        String[] parts = message.split("\\|");
        if (parts.length >= 2) {
            int receiverId = Integer.parseInt(parts[1]);

            logger.info("Call rejected by user ID: {}", receiverId);

            if (videoCallManager != null) {
                videoCallManager.onCallRejected(receiverId);
            }
        }
    }

    /**
     * Xử lý khi cuộc gọi kết thúc
     * Format: VIDEO_CALL_ENDED|userId
     */
    private void handleCallEnded(String message) {
        String[] parts = message.split("\\|");
        if (parts.length >= 2) {
            int userId = Integer.parseInt(parts[1]);

            logger.info("Call ended by user ID: {}", userId);

            if (videoCallManager != null) {
                videoCallManager.onCallEnded(userId);
            }
        }
    }

    /**
     * Xử lý response cho video call request
     * Format: VIDEO_CALL_REQUEST|SENT hoặc VIDEO_CALL_REQUEST|FAIL|reason
     */
    private void handleCallRequestResponse(String message) {
        String[] parts = message.split("\\|");
        if (parts.length >= 2) {
            String status = parts[1];

            if ("SENT".equals(status)) {
                logger.info("Video call request sent successfully");
                // Show calling UI
            } else if ("FAIL".equals(status) && parts.length >= 3) {
                String reason = parts[2];
                logger.warn("Video call request failed: {}", reason);

                if ("USER_OFFLINE".equals(reason)) {
                    showAlert("Không thể gọi", "Người dùng hiện không online.");
                } else {
                    showAlert("Lỗi", "Không thể thực hiện cuộc gọi: " + reason);
                }
            }
        }
    }

    // ============ HELPER METHOD ============

    private void showAlert(String title, String message) {
        javafx.application.Platform.runLater(() -> {
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
}

/*
 * ============================================================================
 * THÊM VÀO FXML (ví dụ: main.fxml hoặc chat.fxml)
 * ============================================================================
 * 
 * Thêm button video call vào chat header:
 * 
 * <HBox alignment="CENTER_RIGHT" spacing="10">
 * <!-- Existing buttons -->
 * 
 * <Button fx:id="videoCallButton"
 * text="📹 Video Call"
 * onAction="#handleVideoCallButton"
 * styleClass="video-call-button"
 * disable="true"/>
 * </HBox>
 * 
 * ============================================================================
 * THÊM VÀO CSS (ví dụ: styles.css)
 * ============================================================================
 * 
 * .video-call-button {
 * -fx-background-color: #4CAF50;
 * -fx-text-fill: white;
 * -fx-font-size: 14px;
 * -fx-padding: 8 15;
 * -fx-background-radius: 5;
 * -fx-cursor: hand;
 * }
 * 
 * .video-call-button:hover {
 * -fx-background-color: #45a049;
 * }
 * 
 * .video-call-button:disabled {
 * -fx-opacity: 0.5;
 * -fx-cursor: default;
 * }
 * 
 * ============================================================================
 */
