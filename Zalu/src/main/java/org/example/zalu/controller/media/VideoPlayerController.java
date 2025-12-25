package org.example.zalu.controller.media;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.StackPane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class VideoPlayerController {
    private static final Logger logger = LoggerFactory.getLogger(VideoPlayerController.class);

    @FXML
    private StackPane videoContainer;
    @FXML
    private MediaView mediaView;
    @FXML
    private Button playPauseButton;
    @FXML
    private Button stopButton;
    @FXML
    private Slider timeSlider;
    @FXML
    private Slider volumeSlider;
    @FXML
    private Label currentTimeLabel;
    @FXML
    private Label totalTimeLabel;
    @FXML
    private Label fileNameLabel;

    private MediaPlayer mediaPlayer;
    private Stage dialogStage;
    private boolean isPlaying = false;
    private File tempVideoFile;

    @FXML
    public void initialize() {
        // Volume slider setup
        if (volumeSlider != null) {
            volumeSlider.setValue(50);
            volumeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
                if (mediaPlayer != null) {
                    mediaPlayer.setVolume(newVal.doubleValue() / 100.0);
                }
            });
        }

        // Time slider setup
        if (timeSlider != null) {
            timeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
                if (timeSlider.isValueChanging() && mediaPlayer != null) {
                    mediaPlayer.seek(Duration.seconds(newVal.doubleValue()));
                }
            });
        }
    }

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;

        // Cleanup khi đóng dialog
        dialogStage.setOnCloseRequest(event -> cleanup());
    }

    /**
     * Load video từ byte array
     */
    public void loadVideo(byte[] videoData, String fileName) {
        try {
            // Tạo file tạm để MediaPlayer có thể đọc
            Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"), "zalu_videos");
            if (!Files.exists(tempDir)) {
                Files.createDirectories(tempDir);
            }

            // Tạo file tạm với tên unique
            String extension = getFileExtension(fileName);
            tempVideoFile = tempDir.resolve("video_" + System.currentTimeMillis() + extension).toFile();

            // Ghi data vào file tạm
            try (FileOutputStream fos = new FileOutputStream(tempVideoFile)) {
                fos.write(videoData);
            }

            // Load video vào MediaPlayer
            Media media = new Media(tempVideoFile.toURI().toString());
            mediaPlayer = new MediaPlayer(media);

            if (mediaView != null) {
                mediaView.setMediaPlayer(mediaPlayer);
            }

            // Set file name
            if (fileNameLabel != null) {
                fileNameLabel.setText(fileName);
            }

            // Setup MediaPlayer listeners
            setupMediaPlayerListeners();

            // Set volume
            if (volumeSlider != null) {
                mediaPlayer.setVolume(volumeSlider.getValue() / 100.0);
            }

            logger.info("Video loaded successfully: {}", fileName);

        } catch (IOException e) {
            logger.error("Error loading video", e);
            showError("Không thể tải video: " + e.getMessage());
        }
    }

    private void setupMediaPlayerListeners() {
        if (mediaPlayer == null)
            return;

        // Khi video ready
        mediaPlayer.setOnReady(() -> {
            Duration totalDuration = mediaPlayer.getTotalDuration();
            if (timeSlider != null) {
                timeSlider.setMax(totalDuration.toSeconds());
            }
            if (totalTimeLabel != null) {
                totalTimeLabel.setText(formatTime(totalDuration));
            }
            logger.info("Video ready, duration: {}", formatTime(totalDuration));
        });

        // Cập nhật time slider khi video đang chạy
        mediaPlayer.currentTimeProperty().addListener((obs, oldTime, newTime) -> {
            if (!timeSlider.isValueChanging()) {
                timeSlider.setValue(newTime.toSeconds());
            }
            if (currentTimeLabel != null) {
                currentTimeLabel.setText(formatTime(newTime));
            }
        });

        // Khi video kết thúc
        mediaPlayer.setOnEndOfMedia(() -> {
            isPlaying = false;
            if (playPauseButton != null) {
                playPauseButton.setText("▶");
            }
            mediaPlayer.seek(Duration.ZERO);
        });

        // Error handling
        mediaPlayer.setOnError(() -> {
            logger.error("MediaPlayer error: {}", mediaPlayer.getError());
            showError("Lỗi phát video: " + mediaPlayer.getError().getMessage());
        });
    }

    @FXML
    private void handlePlayPause() {
        if (mediaPlayer == null)
            return;

        if (isPlaying) {
            mediaPlayer.pause();
            isPlaying = false;
            if (playPauseButton != null) {
                playPauseButton.setText("▶");
            }
        } else {
            mediaPlayer.play();
            isPlaying = true;
            if (playPauseButton != null) {
                playPauseButton.setText("⏸");
            }
        }
    }

    @FXML
    private void handleStop() {
        if (mediaPlayer == null)
            return;

        mediaPlayer.stop();
        isPlaying = false;
        if (playPauseButton != null) {
            playPauseButton.setText("▶");
        }
    }

    @FXML
    private void handleClose() {
        cleanup();
        if (dialogStage != null) {
            dialogStage.close();
        }
    }

    private void cleanup() {
        // Stop và dispose MediaPlayer
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
            mediaPlayer = null;
        }

        // Xóa file tạm
        if (tempVideoFile != null && tempVideoFile.exists()) {
            try {
                Files.delete(tempVideoFile.toPath());
                logger.info("Deleted temp video file: {}", tempVideoFile.getName());
            } catch (IOException e) {
                logger.warn("Could not delete temp video file: {}", e.getMessage());
            }
        }
    }

    private String formatTime(Duration duration) {
        if (duration == null || duration.isUnknown()) {
            return "00:00";
        }

        int totalSeconds = (int) duration.toSeconds();
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;

        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%02d:%02d", minutes, seconds);
        }
    }

    private String getFileExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0 && lastDot < fileName.length() - 1) {
            return fileName.substring(lastDot);
        }
        return ".mp4"; // Default
    }

    private void showError(String message) {
        javafx.application.Platform.runLater(() -> {
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.ERROR);
            alert.setTitle("Lỗi Video Player");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
}
