package org.example.zalu.controller.media;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.example.zalu.model.VideoCallSession;
import org.example.zalu.service.VideoStreamService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * Controller cho màn hình video call
 */
public class VideoCallController {
    private static final Logger logger = LoggerFactory.getLogger(VideoCallController.class);

    @FXML
    private StackPane videoContainer;
    @FXML
    private ImageView localVideoView;
    @FXML
    private ImageView remoteVideoView;
    @FXML
    private Label statusLabel;
    @FXML
    private Label callDurationLabel;
    @FXML
    private Button muteButton;
    @FXML
    private Button videoToggleButton;
    @FXML
    private Button endCallButton;

    private VideoStreamService videoService;
    private VideoCallSession callSession;
    private ObjectOutputStream outputStream;
    private ObjectInputStream inputStream;
    private Stage stage;
    private long callStartTime;
    private boolean isMuted = false;
    private boolean isVideoEnabled = true;

    public void initialize() {
        logger.info("Khởi tạo VideoCallController");
        setupUI();
    }

    private void setupUI() {
        // Setup local video view (preview của bản thân)
        localVideoView.setFitWidth(200);
        localVideoView.setFitHeight(150);
        localVideoView.setPreserveRatio(true);

        // Setup remote video view (video của người đối diện)
        remoteVideoView.setFitWidth(640);
        remoteVideoView.setFitHeight(480);
        remoteVideoView.setPreserveRatio(true);

        // Setup buttons
        setupButtons();
    }

    private void setupButtons() {
        endCallButton.setOnAction(e -> endCall());

        muteButton.setOnAction(e -> {
            isMuted = !isMuted;
            muteButton.setText(isMuted ? "Unmute" : "Mute");
            // TODO: Implement audio mute logic
        });

        videoToggleButton.setOnAction(e -> {
            isVideoEnabled = !isVideoEnabled;
            videoToggleButton.setText(isVideoEnabled ? "Tắt Video" : "Bật Video");
            toggleVideo();
        });
    }

    /**
     * Bắt đầu cuộc gọi video
     */
    public void startVideoCall(VideoCallSession session, ObjectOutputStream output, ObjectInputStream input) {
        this.callSession = session;
        this.outputStream = output;
        this.inputStream = input;
        this.callStartTime = System.currentTimeMillis();

        logger.info("Bắt đầu video call với user: {}",
                session.getCallerId() == getCurrentUserId() ? session.getReceiverName() : session.getCallerName());

        // Khởi tạo video service
        videoService = new VideoStreamService();

        if (!videoService.initializeWebcam()) {
            statusLabel.setText("Lỗi: Không tìm thấy webcam!");
            logger.error("Không thể khởi tạo webcam");
            return;
        }

        // Hiển thị preview local video
        startLocalPreview();

        // Bắt đầu streaming video
        videoService.startStreaming(outputStream);

        // Bắt đầu nhận video từ người đối diện
        videoService.startReceiving(inputStream, this::updateRemoteVideo);

        // Cập nhật UI
        updateCallStatus("Đang kết nối...");

        // Bắt đầu timer cho thời lượng cuộc gọi
        startCallDurationTimer();
    }

    /**
     * Hiển thị preview video local
     */
    private void startLocalPreview() {
        Thread previewThread = new Thread(() -> {
            while (isVideoEnabled && videoService != null) {
                Image preview = videoService.getPreviewImage();
                if (preview != null) {
                    Platform.runLater(() -> localVideoView.setImage(preview));
                }

                try {
                    Thread.sleep(100); // Update preview 10 FPS
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        previewThread.setDaemon(true);
        previewThread.start();
    }

    /**
     * Cập nhật remote video
     */
    private void updateRemoteVideo(Image frame) {
        remoteVideoView.setImage(frame);
    }

    /**
     * Toggle video on/off
     */
    private void toggleVideo() {
        if (isVideoEnabled) {
            // Video đang bật, tiếp tục streaming
            if (videoService != null && !videoService.isStreaming()) {
                videoService.startStreaming(outputStream);
            }
        } else {
            // Video bị tắt
            if (videoService != null) {
                videoService.stopStreaming();
            }
            localVideoView.setImage(null);
        }
    }

    /**
     * Kết thúc cuộc gọi
     */
    @FXML
    private void endCall() {
        logger.info("Kết thúc video call");

        // Dừng video service
        if (videoService != null) {
            videoService.stopStreaming();
        }

        // Gửi tín hiệu kết thúc cuộc gọi
        try {
            if (outputStream != null) {
                outputStream.writeObject("VIDEO_CALL_END");
                outputStream.flush();
            }
        } catch (Exception e) {
            logger.error("Lỗi khi gửi tín hiệu kết thúc: {}", e.getMessage());
        }

        // Đóng cửa sổ
        if (stage != null) {
            stage.close();
        }
    }

    /**
     * Timer hiển thị thời lượng cuộc gọi
     */
    private void startCallDurationTimer() {
        Thread timerThread = new Thread(() -> {
            while (stage != null && stage.isShowing()) {
                long duration = System.currentTimeMillis() - callStartTime;
                long seconds = (duration / 1000) % 60;
                long minutes = (duration / (1000 * 60)) % 60;
                long hours = (duration / (1000 * 60 * 60));

                String timeStr = String.format("%02d:%02d:%02d", hours, minutes, seconds);
                Platform.runLater(() -> callDurationLabel.setText(timeStr));

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        timerThread.setDaemon(true);
        timerThread.start();
    }

    /**
     * Cập nhật trạng thái cuộc gọi
     */
    private void updateCallStatus(String status) {
        Platform.runLater(() -> statusLabel.setText(status));
    }

    /**
     * Set stage để có thể đóng window
     */
    public void setStage(Stage stage) {
        this.stage = stage;

        // Xử lý khi đóng cửa sổ
        stage.setOnCloseRequest(e -> {
            e.consume();
            endCall();
        });
    }

    // TODO: Implement proper user ID retrieval
    private int getCurrentUserId() {
        return 0; // Placeholder
    }
}
