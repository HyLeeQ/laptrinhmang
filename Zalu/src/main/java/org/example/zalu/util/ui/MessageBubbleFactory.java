package org.example.zalu.util.ui;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;
import org.example.zalu.service.AvatarService;
import org.example.zalu.util.audio.VoicePlayer;
import org.example.zalu.util.IconUtil;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Factory để tạo các loại message bubble
 */
public class MessageBubbleFactory {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    
    /**
     * Tạo read status box với icon và text "Đã xem" hoặc avatar người đã đọc
     */
    public static HBox createReadStatusBox(boolean isRead, byte[] readerAvatarData, String readerAvatarUrl) {
        HBox readStatusBox = new HBox(4);
        readStatusBox.setAlignment(Pos.CENTER_RIGHT);
        
        if (isRead && readerAvatarData != null) {
            // Hiển thị avatar người đã đọc (giống Zalo/Messenger) - avatar nhỏ
            Image readerAvatarImage = AvatarService.resolveAvatar(readerAvatarData, readerAvatarUrl, 20, 20);
            if (readerAvatarImage != null && !readerAvatarImage.isError()) {
                ImageView readerAvatarView = new ImageView(readerAvatarImage);
                readerAvatarView.setFitWidth(20);
                readerAvatarView.setFitHeight(20);
                readerAvatarView.setPreserveRatio(true);
                readerAvatarView.setSmooth(true);
                
                // Clip thành hình tròn
                Circle clip = new Circle(10, 10, 10);
                readerAvatarView.setClip(clip);
                
                readStatusBox.getChildren().add(readerAvatarView);
            } else {
                // Fallback: hiển thị "Đã nhận" nếu không có avatar
                Label readStatus = new Label("Đã nhận");
                readStatus.getStyleClass().add("message-read-status");
                readStatus.getStyleClass().add("read");
                readStatus.setStyle("-fx-font-size: 11px; -fx-text-fill: #8e8e93;");
                readStatusBox.getChildren().add(readStatus);
            }
        } else if (isRead) {
            // Đã đọc nhưng không có avatar -> hiển thị "Đã nhận" 
            Label readStatus = new Label("Đã nhận");
            readStatus.getStyleClass().add("message-read-status");
            readStatus.getStyleClass().add("read");
            readStatus.setStyle("-fx-font-size: 11px; -fx-text-fill: #8e8e93;");
            readStatusBox.getChildren().add(readStatus);
        } else {
            // Chưa đọc -> hiển thị "Đã gửi" (không dùng checkmark)
            Label readStatus = new Label("Đã gửi");
            readStatus.getStyleClass().add("message-read-status");
            readStatus.setStyle("-fx-font-size: 11px; -fx-text-fill: #8e8e93;");
            readStatusBox.getChildren().add(readStatus);
        }
        
        return readStatusBox;
    }
    
    /**
     * Overload không có reader avatar (giữ tương thích)
     */
    public static HBox createReadStatusBox(boolean isRead) {
        return createReadStatusBox(isRead, null, null);
    }
    
    /**
     * Tạo text message bubble
     */
    public static VBox createTextBubble(String content, boolean isOwn, LocalDateTime timestamp, 
                                        String senderName, boolean isRead) {
        return createTextBubble(content, isOwn, timestamp, senderName, isRead, null, null);
    }
    
    /**
     * Tạo text message bubble với reader avatar (khi đã đọc)
     */
    public static VBox createTextBubble(String content, boolean isOwn, LocalDateTime timestamp, 
                                        String senderName, boolean isRead, byte[] readerAvatarData, String readerAvatarUrl) {
        return createTextBubble(content, isOwn, timestamp, senderName, isRead, readerAvatarData, readerAvatarUrl, 
            false, false, false, null, null);
    }
    
    /**
     * Tạo text message bubble với hỗ trợ edit/delete/recall/reply
     */
    public static VBox createTextBubble(String content, boolean isOwn, LocalDateTime timestamp, 
                                        String senderName, boolean isRead, byte[] readerAvatarData, String readerAvatarUrl,
                                        boolean isDeleted, boolean isRecalled, boolean isEdited, 
                                        String repliedToContent, Integer repliedToMessageId) {
        VBox contentBox = new VBox(4);
        
        // Reply preview (nếu có)
        if (repliedToContent != null && !repliedToContent.trim().isEmpty() && repliedToMessageId != null && repliedToMessageId > 0) {
            HBox replyPreview = new HBox(8);
            replyPreview.setPadding(new Insets(6, 8, 6, 8));
            replyPreview.setStyle("-fx-background-color: " + (isOwn ? "rgba(255,255,255,0.2)" : "rgba(0,0,0,0.05)") + "; -fx-background-radius: 8;");
            replyPreview.setMaxWidth(350);
            
            // Divider line
            Region divider = new Region();
            divider.setPrefWidth(3);
            divider.setStyle("-fx-background-color: " + (isOwn ? "#ffffff" : "#0088ff") + "; -fx-background-radius: 2;");
            
            // Reply content
            Label replyLabel = new Label(repliedToContent);
            replyLabel.setWrapText(true);
            replyLabel.setMaxWidth(320);
            replyLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: " + (isOwn ? "rgba(255,255,255,0.8)" : "#666666") + "; -fx-font-style: italic;");
            
            replyPreview.getChildren().addAll(divider, replyLabel);
            contentBox.getChildren().add(replyPreview);
        }
        
        // Main message content
        Label bubble = new Label(content.trim());
        bubble.setWrapText(true);
        bubble.setMaxWidth(400);
        bubble.setMinWidth(Region.USE_PREF_SIZE);
        bubble.setPadding(new Insets(8, 12, 8, 12));
        
        // Apply style based on state
        if (isRecalled) {
            bubble.getStyleClass().add(isOwn ? "message-bubble-sent-recalled" : "message-bubble-received-recalled");
            bubble.setStyle("-fx-text-overrun: ellipsis; -fx-line-spacing: 2; -fx-font-style: italic; -fx-opacity: 0.7;");
        } else if (isDeleted) {
            bubble.getStyleClass().add(isOwn ? "message-bubble-sent-deleted" : "message-bubble-received-deleted");
            bubble.setStyle("-fx-text-overrun: ellipsis; -fx-line-spacing: 2; -fx-font-style: italic; -fx-opacity: 0.5;");
        } else {
            bubble.getStyleClass().add(isOwn ? "message-bubble-sent" : "message-bubble-received");
            bubble.setStyle("-fx-text-overrun: ellipsis; -fx-line-spacing: 2;");
        }
        
        contentBox.getChildren().add(bubble);
        
        // Edited/Recalled indicator
        HBox indicatorBox = new HBox(4);
        indicatorBox.setAlignment(Pos.CENTER_RIGHT);
        
        if (isEdited && !isRecalled && !isDeleted) {
            Label editedLabel = new Label("Đã chỉnh sửa");
            editedLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: " + (isOwn ? "rgba(255,255,255,0.7)" : "#888888") + "; -fx-font-style: italic;");
            indicatorBox.getChildren().add(editedLabel);
        }
        
        if (isRecalled) {
            Label recalledLabel = new Label("Tin nhắn đã được thu hồi");
            recalledLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: " + (isOwn ? "rgba(255,255,255,0.7)" : "#888888") + "; -fx-font-style: italic;");
            indicatorBox.getChildren().add(recalledLabel);
        }
        
        if (!indicatorBox.getChildren().isEmpty()) {
            contentBox.getChildren().add(indicatorBox);
        }
        
        // Time label
        String timeStr = timestamp.format(TIME_FORMATTER);
        Label timeLabel = new Label(timeStr);
        timeLabel.getStyleClass().add("message-time");
        
        // Read status
        HBox timeAndStatusBox = new HBox(4);
        timeAndStatusBox.setAlignment(Pos.CENTER_RIGHT);
        timeAndStatusBox.getChildren().add(timeLabel);
        
        if (isOwn) {
            HBox readStatusBox = createReadStatusBox(isRead, readerAvatarData, readerAvatarUrl);
            timeAndStatusBox.getChildren().add(readStatusBox);
        }
        
        contentBox.getChildren().add(timeAndStatusBox);
        
        // Sender name label
        VBox bubbleBox;
        if (!isOwn && senderName != null && !senderName.trim().isEmpty()) {
            Label senderLabel = new Label(senderName);
            senderLabel.getStyleClass().add("message-sender-name");
            bubbleBox = new VBox(2, senderLabel, contentBox);
        } else {
            bubbleBox = new VBox(2, contentBox);
        }
        bubbleBox.setAlignment(isOwn ? Pos.BOTTOM_RIGHT : Pos.BOTTOM_LEFT);
        
        return bubbleBox;
    }
    
    /**
     * Tạo file message bubble
     */
    public static VBox createFileBubble(String fileName, int fileSize, boolean isOwn, LocalDateTime timestamp,
                                       String senderName, boolean isRead) {
        Label iconLabel;
        if (isOwn) {
            iconLabel = IconUtil.getAttachmentIcon(20, Color.WHITE);
        } else {
            iconLabel = IconUtil.getAttachmentIcon(20, Color.web("#0088ff"));
        }
        
        VBox fileBox = new VBox(2); // Tăng spacing từ 1 lên 2 để giống text bubble
        Label nameLabel = new Label(fileName);
        nameLabel.setWrapText(true);
        nameLabel.setMaxWidth(350);
        if (isOwn) {
            nameLabel.setStyle("-fx-font-weight: 400; -fx-text-fill: white; -fx-font-size: 15px; -fx-text-overrun: ellipsis; -fx-line-spacing: 2;"); // Giống text: font-weight 400, font-size 15px, line-spacing 2
        } else {
            nameLabel.setStyle("-fx-font-weight: 400; -fx-text-fill: #1c1e21; -fx-font-size: 15px; -fx-text-overrun: ellipsis; -fx-line-spacing: 2;");
        }
        Label sizeLabel = new Label(formatFileSize(fileSize));
        if (isOwn) {
            sizeLabel.setStyle("-fx-font-size: 11.5px; -fx-text-fill: rgba(255,255,255,0.85);"); // Giống message-time: 11.5px, opacity 0.85
        } else {
            sizeLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #8e8e93;"); // Giống message-time received: 11px
        }
        fileBox.getChildren().addAll(nameLabel, sizeLabel);
        
        HBox previewBox = new HBox(8, iconLabel, fileBox);
        // Không set padding ở đây, để CSS xử lý (giống text bubble)
        previewBox.setMaxWidth(400);
        previewBox.setAlignment(Pos.CENTER_LEFT);
        previewBox.setMinWidth(Region.USE_PREF_SIZE); // Giống text bubble
        
        // Áp dụng style tương tự như text bubble - chỉ dùng style class, không set inline
        if (isOwn) {
            previewBox.getStyleClass().add("message-bubble-sent");
        } else {
            previewBox.getStyleClass().add("message-bubble-received");
        }
        
        // Time label
        String timeStr = timestamp.format(TIME_FORMATTER);
        Label timeLabel = new Label(timeStr);
        timeLabel.getStyleClass().add("message-time");
        
        // Read status
        HBox timeAndStatusBox = new HBox(4);
        timeAndStatusBox.setAlignment(isOwn ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        timeAndStatusBox.getChildren().add(timeLabel);
        
        if (isOwn) {
            HBox readStatusBox = createReadStatusBox(isRead, null, null);
            timeAndStatusBox.getChildren().add(readStatusBox);
        }
        
        // Sender name label
        VBox contentBox;
        if (!isOwn && senderName != null && !senderName.trim().isEmpty()) {
            Label senderLabel = new Label(senderName);
            senderLabel.getStyleClass().add("message-sender-name");
            contentBox = new VBox(2, senderLabel, previewBox, timeAndStatusBox);
        } else {
            contentBox = new VBox(2, previewBox, timeAndStatusBox);
        }
        contentBox.setAlignment(isOwn ? Pos.BOTTOM_RIGHT : Pos.BOTTOM_LEFT);
        
        return contentBox;
    }
    
    /**
     * Tạo avatar node cho message
     */
    public static Node createMessageAvatar(byte[] avatarData, String avatarUrl) {
        Image avatarImage = AvatarService.resolveAvatar(avatarData, avatarUrl, 40, 40);
        
        if (avatarImage != null && !avatarImage.isError()) {
            ImageView avatarView = new ImageView(avatarImage);
            avatarView.setFitHeight(40);
            avatarView.setFitWidth(40);
            avatarView.setPreserveRatio(true);
            avatarView.setSmooth(true);
            
            Circle clip = new Circle(20, 20, 20);
            avatarView.setClip(clip);
            
            StackPane avatarContainer = new StackPane();
            avatarContainer.getChildren().add(avatarView);
            avatarContainer.getStyleClass().add("message-avatar");
            avatarContainer.setPrefSize(40, 40);
            avatarContainer.setMinSize(40, 40);
            avatarContainer.setMaxSize(40, 40);
            
            return avatarContainer;
        } else {
            Circle fallback = new Circle(20, Color.LIGHTGRAY);
            fallback.setStroke(Color.WHITE);
            fallback.setStrokeWidth(2);
            StackPane container = new StackPane(fallback);
            container.getStyleClass().add("message-avatar");
            container.setPrefSize(40, 40);
            return container;
        }
    }
    
    /**
     * Tạo voice message bubble với player tích hợp
     */
    public static VBox createVoiceBubble(byte[] audioData, String fileName, boolean isOwn, LocalDateTime timestamp, 
                                         String senderName, boolean isRead) {
        // Tạo VoicePlayer
        VoicePlayer voicePlayer = new VoicePlayer();
        
        // Icon và label
        Label iconLabel = new Label("🎤");
        iconLabel.setStyle("-fx-font-size: 18px;");
        
        Label nameLabel = new Label("Tin nhắn thoại");
        nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        
        // Tính tổng thời lượng từ audioData (ước tính từ kích thước)
        // Format: 16kHz, 16bit, mono = 32000 bytes/giây
        double estimatedDuration = audioData.length / 32000.0;
        int totalSeconds = (int) Math.ceil(estimatedDuration);
        String initialTimeText = String.format("%d:%02d", totalSeconds / 60, totalSeconds % 60);
        
        Label timeLabel = new Label(initialTimeText);
        timeLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #6c757d;");
        timeLabel.setMinWidth(60);
        
        // Lưu tổng thời lượng để dùng cho countdown
        final int[] totalDurationSeconds = {totalSeconds};
        
        // Nút play/pause
        Button playPauseButton = new Button();
        playPauseButton.setGraphic(IconUtil.getPlayIcon(20, isOwn ? Color.WHITE : Color.web("#0084ff")).getGraphic());
        playPauseButton.setStyle("-fx-background-color: transparent; -fx-padding: 4 8; -fx-background-radius: 12; -fx-cursor: hand;");
        playPauseButton.setMinWidth(36);
        playPauseButton.setMinHeight(36);
        
        // Progress bar
        ProgressBar progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(200);
        progressBar.setPrefHeight(4);
        progressBar.setStyle("-fx-accent: " + (isOwn ? "#ffffff" : "#0084ff") + ";");
        progressBar.setVisible(false);
        
        VBox contentBox = new VBox(4);
        contentBox.getChildren().addAll(nameLabel, timeLabel);
        
        HBox previewBox = new HBox(8);
        previewBox.setAlignment(Pos.CENTER_LEFT);
        previewBox.getChildren().addAll(playPauseButton, iconLabel, contentBox);
        previewBox.setPadding(new Insets(10, 14, 10, 14));
        previewBox.setMaxWidth(400);
        previewBox.getStyleClass().add("voice-message-bubble");
        previewBox.setStyle("-fx-background-color: " + (isOwn ? "#0084ff" : "#e4e6eb") + "; -fx-background-radius: 18;");
        
        // Set text color cho labels
        if (isOwn) {
            nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: white;");
            timeLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: rgba(255,255,255,0.8);");
        }
        
        // Timeline để cập nhật progress và time
        Timeline progressTimeline = new Timeline();
        progressTimeline.setCycleCount(Timeline.INDEFINITE);
        
        // Xử lý play/pause
        playPauseButton.setOnAction(e -> {
            if (voicePlayer.isPlaying()) {
                voicePlayer.pause();
                playPauseButton.setGraphic(IconUtil.getPlayIcon(20, isOwn ? Color.WHITE : Color.web("#0084ff")).getGraphic());
                progressTimeline.pause();
            } else if (voicePlayer.isPaused()) {
                voicePlayer.resume();
                playPauseButton.setGraphic(IconUtil.getPauseIcon(20, isOwn ? Color.WHITE : Color.web("#0084ff")).getGraphic());
                progressTimeline.play();
            } else {
                // Bắt đầu phát
                voicePlayer.playAudio(audioData, fileName);
                playPauseButton.setGraphic(IconUtil.getPauseIcon(20, isOwn ? Color.WHITE : Color.web("#0084ff")).getGraphic());
                progressBar.setVisible(true);
                progressBar.setProgress(0);
                
                // Cập nhật tổng thời lượng khi media ready
                voicePlayer.setOnReady(() -> {
                    double total = voicePlayer.getTotalDuration();
                    if (total > 0) {
                        totalDurationSeconds[0] = (int) Math.ceil(total);
                        Platform.runLater(() -> {
                            if (!voicePlayer.isPlaying() && !voicePlayer.isPaused()) {
                                int totalSec = totalDurationSeconds[0];
                                timeLabel.setText(String.format("%d:%02d", totalSec / 60, totalSec % 60));
                            }
                        });
                    }
                });
                
                // Cập nhật progress và time (countdown)
                progressTimeline.getKeyFrames().clear();
                progressTimeline.getKeyFrames().add(new KeyFrame(Duration.millis(100), ev -> {
                    if (voicePlayer.isPlaying() || voicePlayer.isPaused()) {
                        double current = voicePlayer.getCurrentTime();
                        double total = voicePlayer.getTotalDuration();
                        if (total > 0) {
                            progressBar.setProgress(current / total);
                            // Countdown: hiển thị thời gian còn lại
                            int remainingSec = Math.max(0, totalDurationSeconds[0] - (int) current);
                            timeLabel.setText(String.format("%d:%02d", remainingSec / 60, remainingSec % 60));
                        } else {
                            // Nếu chưa có duration, ước tính từ current time
                            int currentSec = (int) current;
                            int estimatedTotal = Math.max(totalDurationSeconds[0], currentSec + 1);
                            int remainingSec = Math.max(0, estimatedTotal - currentSec);
                            timeLabel.setText(String.format("%d:%02d", remainingSec / 60, remainingSec % 60));
                        }
                    }
                }));
                progressTimeline.play();
            }
        });
        
        // Callback khi phát xong
        voicePlayer.setOnFinished(() -> {
            Platform.runLater(() -> {
                playPauseButton.setGraphic(IconUtil.getPlayIcon(20, isOwn ? Color.WHITE : Color.web("#0084ff")).getGraphic());
                progressBar.setVisible(false);
                progressBar.setProgress(0);
                // Reset về tổng thời lượng
                int totalSec = totalDurationSeconds[0];
                timeLabel.setText(String.format("%d:%02d", totalSec / 60, totalSec % 60));
                progressTimeline.stop();
            });
        });
        
        // Callback khi trạng thái thay đổi
        voicePlayer.setOnPlayStateChanged(() -> {
            Platform.runLater(() -> {
                if (!voicePlayer.isPlaying() && !voicePlayer.isPaused()) {
                    playPauseButton.setGraphic(IconUtil.getPlayIcon(20, isOwn ? Color.WHITE : Color.web("#0084ff")).getGraphic());
                    progressBar.setVisible(false);
                    // Reset về tổng thời lượng khi dừng
                    int totalSec = totalDurationSeconds[0];
                    timeLabel.setText(String.format("%d:%02d", totalSec / 60, totalSec % 60));
                }
            });
        });
        
        // Time label (timestamp)
        String timeStr = timestamp.format(TIME_FORMATTER);
        Label timestampLabel = new Label(timeStr);
        timestampLabel.getStyleClass().add("message-time");
        
        // Read status
        HBox timeAndStatusBox = new HBox(4);
        timeAndStatusBox.setAlignment(isOwn ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        timeAndStatusBox.getChildren().add(timestampLabel);
        
        if (isOwn) {
            HBox readStatusBox = createReadStatusBox(isRead, null, null);
            timeAndStatusBox.getChildren().add(readStatusBox);
        }
        
        // Sender name label
        VBox finalBox;
        if (!isOwn && senderName != null && !senderName.trim().isEmpty()) {
            Label senderLabel = new Label(senderName);
            senderLabel.getStyleClass().add("message-sender-name");
            finalBox = new VBox(2, senderLabel, previewBox, timeAndStatusBox);
        } else {
            finalBox = new VBox(2, previewBox, timeAndStatusBox);
        }
        finalBox.setAlignment(isOwn ? Pos.BOTTOM_RIGHT : Pos.BOTTOM_LEFT);
        
        return finalBox;
    }
    
    /**
     * Format file size
     */
    private static String formatFileSize(int bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }
}

