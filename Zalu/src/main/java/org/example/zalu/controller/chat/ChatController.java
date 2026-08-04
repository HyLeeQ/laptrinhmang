package org.example.zalu.controller.chat;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import org.example.zalu.client.ChatClient;
import org.example.zalu.controller.common.EmojiPickerController;
import org.example.zalu.controller.common.ImagePreviewController;
import org.example.zalu.model.Message;
import org.example.zalu.model.User;
import org.example.zalu.util.audio.AudioRecorder;
import org.example.zalu.util.ui.ChatRenderer;
import org.example.zalu.util.IconUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.UUID;

public class ChatController {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ChatController.class);

    @FXML
    private TextField messageField;
    @FXML
    private Button emojiButton;
    @FXML
    private Button voiceButton;
    @FXML
    private Button sendButton;
    @FXML
    private HBox recordingPanel;
    @FXML
    private Label recordingTimeLabel;
    @FXML
    private Button stopRecordingButton;
    @FXML
    private HBox filePreviewPanel;
    @FXML
    private Label filePreviewIcon;
    @FXML
    private Label filePreviewLabel;
    @FXML
    private Label filePreviewSize;
    @FXML
    private ProgressBar fileProgressBar;
    @FXML
    private Button filePreviewCancelBtn;

    @FXML
    private HBox voicePreviewPanel;
    @FXML
    private Label voicePreviewLabel;
    @FXML
    private Label voicePreviewSize;
    @FXML
    private Button voicePreviewCancelBtn;

    @FXML
    private HBox replyPreviewPanel;
    @FXML
    private Label replyPreviewContent;
    @FXML
    private Button replyPreviewCancelBtn;

    private Popup emojiPopup;
    private AudioRecorder audioRecorder;
    private boolean isRecordingVoice = false;
    private Timeline recordingTimer;
    private int recordingSeconds = 0;
    private byte[] pendingVoiceData = null;
    private String pendingVoiceFileName = null;

    private byte[] pendingFileData = null;
    private String pendingFileName = null;
    private boolean pendingFileIsImage = false;

    private Stage stage;
    private int currentUserId = -1;
    private int currentFriendId = -1;
    private int currentGroupId = -1; // -1 = chat 1-1, >0 = group chat
    private MessageListController messageListController;

    // Typing indicator debouncing
    private Timeline typingDebounceTimer = null;
    private long lastTypingSignalTime = 0;
    private static final long TYPING_SIGNAL_INTERVAL = 2000; // Gửi signal mỗi 2 giây khi đang gõ

    @FXML
    public void initialize() {
        // Enter = gửi tin nhắn, Shift+Enter = xuống dòng (nếu muốn sau này)
        messageField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER && !event.isShiftDown()) {
                event.consume(); // tránh xuống dòng
                sendMessage();
            }
        });

        // Gửi typing signal khi user nhập text
        messageField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && !newValue.trim().isEmpty()) {
                sendTypingSignal();
            } else {
                // Nếu xóa hết text, dừng typing signal
                stopTypingSignal();
            }
        });

        // Drag & Drop support cho messageField
        setupDragAndDrop();
    }

    /**
     * Gửi typing signal với debouncing để tránh gửi quá nhiều
     */
    private void sendTypingSignal() {
        if (currentUserId == -1)
            return;

        // Chỉ gửi cho chat 1-1, không gửi cho group
        if (currentGroupId > 0)
            return;
        if (currentFriendId <= 0)
            return;

        long currentTime = System.currentTimeMillis();

        // Nếu đã gửi signal gần đây (< 2 giây), không gửi lại
        if (currentTime - lastTypingSignalTime < TYPING_SIGNAL_INTERVAL) {
            // Reset timer để gửi lại sau 2 giây nếu vẫn đang gõ
            if (typingDebounceTimer != null) {
                typingDebounceTimer.stop();
            }
            typingDebounceTimer = new Timeline(new KeyFrame(Duration.millis(TYPING_SIGNAL_INTERVAL), e -> {
                sendTypingSignal();
            }));
            typingDebounceTimer.setCycleCount(1);
            typingDebounceTimer.play();
            return;
        }

        // Gửi typing signal
        ChatClient.sendRequest("TYPING|" + currentUserId + "|" + currentFriendId);
        lastTypingSignalTime = currentTime;

        // Reset timer để gửi lại sau 2 giây nếu vẫn đang gõ
        if (typingDebounceTimer != null) {
            typingDebounceTimer.stop();
        }
        typingDebounceTimer = new Timeline(new KeyFrame(Duration.millis(TYPING_SIGNAL_INTERVAL), e -> {
            sendTypingSignal();
        }));
        typingDebounceTimer.setCycleCount(1);
        typingDebounceTimer.play();
    }

    /**
     * Dừng typing signal (khi xóa hết text hoặc gửi tin nhắn)
     */
    private void stopTypingSignal() {
        if (typingDebounceTimer != null) {
            typingDebounceTimer.stop();
            typingDebounceTimer = null;
        }

        if (currentUserId == -1 || currentFriendId <= 0)
            return;

        // Gửi signal dừng typing
        ChatClient.sendRequest("TYPING_STOP|" + currentUserId + "|" + currentFriendId);
        lastTypingSignalTime = 0;
    }

    private void setupDragAndDrop() {
        if (messageField == null)
            return;

        messageField.setOnDragOver(event -> {
            if (event.getDragboard().hasFiles()) {
                event.acceptTransferModes(javafx.scene.input.TransferMode.COPY);
            }
            event.consume();
        });

        messageField.setOnDragDropped(event -> {
            javafx.scene.input.Dragboard db = event.getDragboard();
            boolean success = false;

            if (db.hasFiles()) {
                File file = db.getFiles().get(0); // Lấy file đầu tiên
                if (file != null && file.exists() && file.isFile()) {
                    handleDroppedFile(file);
                    success = true;
                }
            }

            event.setDropCompleted(success);
            event.consume();
        });
    }

    private void handleDroppedFile(File file) {
        if (currentUserId == -1) {
            showAlert("Vui lòng đăng nhập để gửi file!");
            return;
        }

        if (currentFriendId == -1 && currentGroupId == -1) {
            showAlert("Vui lòng chọn một người bạn hoặc nhóm để gửi file!");
            return;
        }

        try {
            byte[] data = Files.readAllBytes(file.toPath());
            if (data.length > 25 * 1024 * 1024) { // Giới hạn 25MB
                showAlert("File quá lớn! Chỉ hỗ trợ file dưới 25MB.");
                return;
            }

            String fileName = file.getName();
            boolean isImage = ChatRenderer.isAudioFile(fileName) ? false : ChatRenderer.isImageFile(fileName);

            // Lưu file vào pending và hiển thị preview
            pendingFileData = data;
            pendingFileName = fileName;
            pendingFileIsImage = isImage;

            // Nếu là ảnh, hiển thị preview dialog trước
            if (isImage) {
                showImagePreviewDialog(data, fileName);
            } else {
                // File khác thì hiển thị preview panel
                showFilePreview(data, fileName);
            }
        } catch (IOException e) {
            logger.error("Error reading file", e);
            showAlert("Lỗi đọc file: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Error processing file", e);
            showAlert("Lỗi xử lý file: " + e.getMessage());
        }
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public void setCurrentUserId(int userId) {
        this.currentUserId = userId;
    }

    public void setCurrentFriend(User friend) {
        if (friend != null) {
            this.currentFriendId = friend.getId();
            this.currentGroupId = -1; // Reset group khi chọn bạn
        }
    }

    public void setCurrentGroup(int groupId) {
        this.currentGroupId = groupId;
        this.currentFriendId = -1; // Reset friend khi chọn nhóm
    }

    public void setMessageListController(MessageListController controller) {
        this.messageListController = controller;
    }

    @FXML
    private void sendMessage() {
        // Dừng typing signal khi gửi tin nhắn
        stopTypingSignal();

        // Kiểm tra nếu có file pending, gửi file trước
        if (pendingFileData != null && pendingFileName != null) {
            sendFilePreview();
            return;
        }

        // Kiểm tra nếu có voice pending, gửi voice trước
        if (pendingVoiceData != null && pendingVoiceFileName != null) {
            sendVoicePreview();
            return;
        }

        // Nếu không có file/voice pending, gửi tin nhắn text
        String content = messageField.getText().trim();
        if (content.isEmpty())
            return;

        if (sendContentToActiveChat(content)) {
            messageField.clear();
            messageField.requestFocus();
        }
    }

    @FXML
    private void handleSendLike() {
        sendContentToActiveChat("👍");
    }

    @FXML
    private void handleSendFile() {
        if (currentUserId == -1) {
            showAlert("Vui lòng đăng nhập để gửi file!");
            return;
        }

        if (currentFriendId == -1 && currentGroupId == -1) {
            showAlert("Vui lòng chọn một người bạn hoặc nhóm để gửi file!");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Chọn file gửi qua Zalu");
        File initialDir = new File(System.getProperty("user.home") + "/Downloads");
        if (initialDir.exists() && initialDir.isDirectory()) {
            fileChooser.setInitialDirectory(initialDir);
        }
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Tất cả file", "*.*"),
                new FileChooser.ExtensionFilter("Hình ảnh", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp", "*.webp"),
                new FileChooser.ExtensionFilter("Video", "*.mp4", "*.mkv", "*.avi", "*.mov", "*.wmv"),
                new FileChooser.ExtensionFilter("Tài liệu", "*.pdf", "*.docx", "*.doc", "*.txt", "*.zip", "*.rar"));

        File file = fileChooser.showOpenDialog(stage);
        if (file == null || !file.exists())
            return;

        try {
            byte[] data = Files.readAllBytes(file.toPath());
            if (data.length > 25 * 1024 * 1024) { // Giới hạn 25MB
                showAlert("File quá lớn! Chỉ hỗ trợ file dưới 25MB.");
                return;
            }

            String fileName = file.getName();
            boolean isImage = ChatRenderer.isAudioFile(fileName) ? false : ChatRenderer.isImageFile(fileName);

            // Lưu file vào pending và hiển thị preview
            pendingFileData = data;
            pendingFileName = fileName;
            pendingFileIsImage = isImage;

            // Nếu là ảnh, hiển thị preview dialog trước
            if (isImage) {
                showImagePreviewDialog(data, fileName);
            } else {
                // File khác thì hiển thị preview panel
                showFilePreview(data, fileName);
            }

        } catch (IOException e) {
            logger.error("Error reading file for send", e);
            showAlert("Lỗi đọc file: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Error sending file", e);
            showAlert("Lỗi gửi file: " + e.getMessage());
        }
    }

    private void showImagePreviewDialog(byte[] imageData, String fileName) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/org/example/zalu/views/media/image-preview-view.fxml"));
            VBox root = loader.load();
            ImagePreviewController controller = loader.getController();

            controller.setImageData(imageData, fileName);
            controller.setOnSendCallback(() -> {
                // Gửi ảnh khi người dùng xác nhận
                sendFileData(imageData, fileName, true);
                // Clear pending file sau khi gửi
                pendingFileData = null;
                pendingFileName = null;
                pendingFileIsImage = false;
            });

            Stage dialogStage = new Stage();
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.initOwner(stage);
            dialogStage.initStyle(StageStyle.UTILITY);
            dialogStage.setTitle("Xem trước ảnh");
            dialogStage.setScene(new Scene(root));
            dialogStage.setResizable(false);

            controller.setDialogStage(dialogStage);
            dialogStage.showAndWait();

        } catch (IOException e) {
            logger.error("Error showing image preview", e);
            showAlert("Lỗi hiển thị preview: " + e.getMessage());
            // Nếu lỗi preview, vẫn gửi ảnh trực tiếp
            sendFileData(imageData, fileName, true);
        }
    }

    private void sendFileData(byte[] data, String fileName, boolean isImage) {
        try {
            // Hiển thị progress bar cho file lớn (>1MB)
            boolean showProgress = data.length > 1024 * 1024;
            if (showProgress && fileProgressBar != null) {
                fileProgressBar.setVisible(true);
                fileProgressBar.setManaged(true);
                fileProgressBar.setProgress(0.5); // Indeterminate progress
            }

            // Gửi file cho nhóm hoặc bạn bè
            if (currentGroupId > 0) {
                // Gửi file cho nhóm
                ChatClient.sendRequest(
                        "SEND_GROUP_FILE|" + currentGroupId + "|" + currentUserId + "|" + fileName + "|" + data.length);
                ChatClient.sendObject(data);


                logger.info("Sent {} to group: {} ({})", (isImage ? "image" : "file"), fileName,
                        formatSize(data.length));
            } else if (currentFriendId > 0) {
                // Gửi file cho bạn bè
                ChatClient.sendRequest(
                        "SEND_FILE|" + currentUserId + "|" + currentFriendId + "|" + fileName + "|" + data.length);
                ChatClient.sendObject(data);


                logger.info("Sent {}: {} ({})", (isImage ? "image" : "file"), fileName, formatSize(data.length));
            }

            // Ẩn progress bar và preview panel sau khi gửi
            Platform.runLater(() -> {
                if (fileProgressBar != null) {
                    fileProgressBar.setVisible(false);
                    fileProgressBar.setManaged(false);
                    fileProgressBar.setProgress(0);
                }
                hideFilePreview();
            });
        } catch (Exception e) {
            logger.error("Error sending file", e);
            showAlert("Lỗi gửi file: " + e.getMessage());
            Platform.runLater(() -> {
                if (fileProgressBar != null) {
                    fileProgressBar.setVisible(false);
                    fileProgressBar.setManaged(false);
                }
            });
        }
    }

    private void showFilePreview(byte[] data, String fileName) {
        Platform.runLater(() -> {
            if (filePreviewPanel != null) {
                filePreviewPanel.setVisible(true);
                filePreviewPanel.setManaged(true);

                // Set icon dựa trên loại file
                if (filePreviewIcon != null) {
                    filePreviewIcon.setGraphic(
                            IconUtil.getFileIcon(fileName, 24, javafx.scene.paint.Color.web("#65676b")).getGraphic());
                    filePreviewIcon.setText(null);
                }

                if (filePreviewLabel != null) {
                    filePreviewLabel.setText(fileName);
                }

                if (filePreviewSize != null) {
                    filePreviewSize.setText(formatSize(data.length));
                }

                // Cập nhật text nút Gửi
                if (sendButton != null) {
                    sendButton.setText("Gửi file");
                }
            }
        });
    }

    private void hideFilePreview() {
        Platform.runLater(() -> {
            if (filePreviewPanel != null) {
                filePreviewPanel.setVisible(false);
                filePreviewPanel.setManaged(false);
            }
            pendingFileData = null;
            pendingFileName = null;
            pendingFileIsImage = false;

            // Reset text nút Gửi
            if (sendButton != null && pendingVoiceData == null) {
                sendButton.setText("Gửi");
            }
        });
    }

    @FXML
    private void cancelFilePreview() {
        hideFilePreview();
    }

    private void sendFilePreview() {
        if (pendingFileData != null && pendingFileName != null) {
            sendFileData(pendingFileData, pendingFileName, pendingFileIsImage);
        }
    }

    private String formatSize(long bytes) {
        if (bytes < 1024)
            return bytes + " B";
        if (bytes < 1024 * 1024)
            return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }

    private void showAlert(String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.show();
    }

    // Gọi từ MainController khi chọn bạn
    public void focusInput() {
        messageField.requestFocus();
    }

    @FXML
    private void handleVoiceMessage() {
        if (currentFriendId <= 0 && currentGroupId <= 0) {
            showAlert("Vui lòng chọn bạn hoặc nhóm để gửi tin nhắn thoại");
            return;
        }

        // Chỉ ghi âm trực tiếp, không có dialog chọn file
        startVoiceRecording();
    }

    private void startVoiceRecording() {
        if (audioRecorder == null) {
            audioRecorder = new AudioRecorder();
        }

        // Bắt đầu ghi âm
        if (audioRecorder.startRecording()) {
            isRecordingVoice = true;
            recordingSeconds = 0;

            // Ẩn preview nếu có
            hideVoicePreview();

            // Hiển thị recording panel
            if (recordingPanel != null) {
                recordingPanel.setVisible(true);
                recordingPanel.setManaged(true);
            }

            // Cập nhật nút voice
            if (voiceButton != null) {
                voiceButton.setText("⏹️");
                voiceButton.setStyle("-fx-background-color: #ff4444;");
            }

            // Bắt đầu timer
            startRecordingTimer();
        } else {
            showAlert("Không thể bắt đầu ghi âm. Kiểm tra microphone và quyền truy cập.");
        }
    }

    private void startRecordingTimer() {
        if (recordingTimer != null) {
            recordingTimer.stop();
        }

        recordingTimer = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            recordingSeconds++;
            updateRecordingTime();
        }));
        recordingTimer.setCycleCount(Timeline.INDEFINITE);
        recordingTimer.play();
    }

    private void updateRecordingTime() {
        Platform.runLater(() -> {
            int minutes = recordingSeconds / 60;
            int seconds = recordingSeconds % 60;
            String timeStr = String.format("%02d:%02d", minutes, seconds);
            if (recordingTimeLabel != null) {
                recordingTimeLabel.setText(timeStr);
            }
        });
    }

    @FXML
    private void stopVoiceRecording() {
        if (audioRecorder != null && isRecordingVoice) {
            // Dừng timer
            if (recordingTimer != null) {
                recordingTimer.stop();
                recordingTimer = null;
            }

            // Dừng ghi âm
            byte[] audioData = audioRecorder.stopRecording();
            isRecordingVoice = false;

            // Ẩn recording panel
            if (recordingPanel != null) {
                recordingPanel.setVisible(false);
                recordingPanel.setManaged(false);
            }

            // Cập nhật nút voice
            if (voiceButton != null) {
                voiceButton.setText("🎤");
                voiceButton.setStyle("");
            }

            if (audioData != null && audioData.length > 0) {
                // Lưu file tạm
                try {
                    Path voiceDir = Paths.get("voice_messages");
                    if (!Files.exists(voiceDir)) {
                        Files.createDirectories(voiceDir);
                    }

                    String fileName = "voice_" + System.currentTimeMillis() + ".wav";
                    File voiceFile = voiceDir.resolve(fileName).toFile();

                    if (audioRecorder.saveToFile(audioData, voiceFile)) {
                        // Lưu vào pending và hiển thị preview
                        pendingVoiceData = audioData;
                        pendingVoiceFileName = fileName;
                        showVoicePreview(audioData, fileName);
                    } else {
                        showAlert("Lỗi khi lưu file ghi âm");
                    }
                } catch (IOException e) {
                    showAlert("Lỗi khi lưu file ghi âm: " + e.getMessage());
                }
            } else {
                showAlert("Không có dữ liệu ghi âm");
            }
        }
    }

    private void showVoicePreview(byte[] audioData, String fileName) {
        Platform.runLater(() -> {
            if (voicePreviewPanel != null) {
                voicePreviewPanel.setVisible(true);
                voicePreviewPanel.setManaged(true);

                if (voicePreviewLabel != null) {
                    voicePreviewLabel.setText("Tin nhắn thoại (" + formatTime(recordingSeconds) + ")");
                }

                if (voicePreviewSize != null) {
                    voicePreviewSize.setText(formatSize(audioData.length));
                }

                // Cập nhật text nút Gửi
                if (sendButton != null) {
                    sendButton.setText("Gửi voice");
                }
            }
        });
    }

    private void hideVoicePreview() {
        Platform.runLater(() -> {
            if (voicePreviewPanel != null) {
                voicePreviewPanel.setVisible(false);
                voicePreviewPanel.setManaged(false);
            }
            pendingVoiceData = null;
            pendingVoiceFileName = null;

            // Reset text nút Gửi
            if (sendButton != null && pendingFileData == null) {
                sendButton.setText("Gửi");
            }
        });
    }

    @FXML
    private void cancelVoicePreview() {
        hideVoicePreview();
    }

    private void sendVoicePreview() {
        if (pendingVoiceData != null && pendingVoiceFileName != null) {
            sendVoiceMessage(pendingVoiceData, pendingVoiceFileName);
            hideVoicePreview();
        }
    }

    private String formatTime(int seconds) {
        int minutes = seconds / 60;
        int secs = seconds % 60;
        return String.format("%d:%02d", minutes, secs);
    }

    private void sendVoiceMessage(byte[] audioData, String fileName) {
        if (audioData == null || audioData.length == 0) {
            showAlert("File audio rỗng");
            return;
        }

        if (audioData.length > 10 * 1024 * 1024) { // 10MB limit
            showAlert("File audio quá lớn! Chỉ hỗ trợ file dưới 10MB.");
            return;
        }

        try {
            // Gửi như file thông thường, nhưng đánh dấu là voice message
            if (currentGroupId > 0) {
                ChatClient.sendRequest("SEND_GROUP_FILE|" + currentGroupId + "|" + currentUserId + "|" + fileName + "|"
                        + audioData.length);
                ChatClient.sendObject(audioData);
            } else if (currentFriendId > 0) {
                ChatClient.sendRequest(
                        "SEND_FILE|" + currentUserId + "|" + currentFriendId + "|" + fileName + "|" + audioData.length);
                ChatClient.sendObject(audioData);
            }



            logger.info("Sent voice message: {} ({})", fileName, formatSize(audioData.length));
        } catch (Exception e) {
            logger.error("Error sending voice message", e);
            showAlert("Lỗi gửi tin nhắn thoại: " + e.getMessage());
        }
    }

    @FXML
    private void handleEmojiPicker() {
        try {
            if (emojiPopup == null) {
                emojiPopup = new Popup();
                emojiPopup.setAutoHide(true);

                FXMLLoader loader = new FXMLLoader(
                        getClass().getResource("/org/example/zalu/views/common/emoji-picker-view.fxml"));
                VBox emojiPicker = loader.load();
                EmojiPickerController controller = loader.getController();
                controller.setChatController(this);

                emojiPopup.getContent().add(emojiPicker);
            }

            // Hiển thị popup gần nút emoji
            if (emojiButton != null && emojiButton.getScene() != null) {
                Bounds bounds = emojiButton.localToScreen(emojiButton.getBoundsInLocal());
                emojiPopup.show(emojiButton.getScene().getWindow(),
                        bounds.getMinX() - 280,
                        bounds.getMaxY() - 300);
            }
        } catch (IOException e) {
            logger.error("Error loading emoji picker", e);
            showAlert("Lỗi tải emoji picker: " + e.getMessage());
        }
    }

    public void insertEmoji(String emoji) {
        if (messageField != null) {
            int caretPosition = messageField.getCaretPosition();
            String text = messageField.getText();
            String newText = text.substring(0, caretPosition) + emoji + text.substring(caretPosition);
            messageField.setText(newText);
            messageField.positionCaret(caretPosition + emoji.length());
            messageField.requestFocus();
        }

        // Đóng popup sau khi chọn
        if (emojiPopup != null && emojiPopup.isShowing()) {
            emojiPopup.hide();
        }
    }

    /**
     * Xóa tin nhắn (chỉ xóa cho mình)
     */
    public void deleteMessage(int messageId) {
        if (currentUserId <= 0) {
            showAlert("Vui lòng đăng nhập!");
            return;
        }
        ChatClient.sendRequest("DELETE_MESSAGE|" + messageId + "|" + currentUserId);
    }

    /**
     * Thu hồi tin nhắn (xóa cho cả hai)
     */
    public void recallMessage(int messageId) {
        if (currentUserId <= 0) {
            showAlert("Vui lòng đăng nhập!");
            return;
        }
        ChatClient.sendRequest("RECALL_MESSAGE|" + messageId + "|" + currentUserId);
    }

    /**
     * Chỉnh sửa tin nhắn
     */
    public void editMessage(int messageId, String newContent) {
        if (currentUserId <= 0) {
            showAlert("Vui lòng đăng nhập!");
            return;
        }
        if (newContent == null || newContent.trim().isEmpty()) {
            showAlert("Nội dung không được để trống!");
            return;
        }
        ChatClient.sendRequest("EDIT_MESSAGE|" + messageId + "|" + currentUserId + "|" + newContent);
    }

    /**
     * Trả lời tin nhắn (reply)
     */
    private Integer replyingToMessageId = null;
    private String replyingToContent = null;

    public void startReply(int messageId, String messageContent) {
        replyingToMessageId = messageId;
        replyingToContent = messageContent != null && !messageContent.trim().isEmpty()
                ? messageContent.trim()
                : "Tin nhắn";

        // Hiển thị reply preview panel
        if (replyPreviewPanel != null && replyPreviewContent != null) {
            String previewText = replyingToContent.length() > 50
                    ? replyingToContent.substring(0, 47) + "..."
                    : replyingToContent;
            replyPreviewContent.setText(previewText);
            replyPreviewPanel.setVisible(true);
            replyPreviewPanel.setManaged(true);
        }

        messageField.setPromptText("Nhập tin nhắn...");
        messageField.requestFocus();
    }

    public void cancelReply() {
        replyingToMessageId = null;
        replyingToContent = null;
        messageField.setPromptText("Nhập tin nhắn...");

        // Ẩn reply preview panel
        if (replyPreviewPanel != null) {
            replyPreviewPanel.setVisible(false);
            replyPreviewPanel.setManaged(false);
        }
    }

    private boolean sendContentToActiveChat(String rawContent) {
        if (rawContent == null)
            return false;
        String content = rawContent.trim();
        if (content.isEmpty())
            return false;

        String tempId = UUID.randomUUID().toString();
        String requestStr;
        if (currentGroupId > 0) {
            String baseRequest = "SEND_GROUP_MESSAGE|" + currentGroupId + "|" + content;
            if (replyingToMessageId != null && replyingToMessageId > 0) {
                requestStr = baseRequest + "|REPLY_TO|" + replyingToMessageId + "|" + replyingToContent + "|TEMP_ID|"
                        + tempId;
            } else {
                requestStr = baseRequest + "|TEMP_ID|" + tempId;
            }
            ChatClient.sendRequest(requestStr);
            logger.debug("Sending group message: groupId={}, senderId={}, content={}, tempId={}", currentGroupId,
                    currentUserId, content, tempId);

            Message localMsg = new Message();
            localMsg.setSenderId(currentUserId);
            localMsg.setGroupId(currentGroupId);
            localMsg.setContent(content);
            localMsg.setCreatedAt(LocalDateTime.now());
            localMsg.setFile(false);
            localMsg.setStatus(Message.MessageStatus.SENDING);
            localMsg.setTempId(tempId);
            if (replyingToMessageId != null && replyingToMessageId > 0) {
                localMsg.setRepliedToMessageId(replyingToMessageId);
                localMsg.setRepliedToContent(replyingToContent);
            }

            if (messageListController != null) {
                messageListController.addLocalTextMessage(localMsg);
            }
            cancelReply(); // Clear reply after sending
            return true;
        } else if (currentFriendId > 0) {
            String baseRequest = "SEND_MESSAGE|" + currentFriendId + "|" + currentUserId + "|" + content;
            if (replyingToMessageId != null && replyingToMessageId > 0) {
                requestStr = baseRequest + "|REPLY_TO|" + replyingToMessageId + "|" + replyingToContent + "|TEMP_ID|"
                        + tempId;
            } else {
                requestStr = baseRequest + "|TEMP_ID|" + tempId;
            }
            ChatClient.sendRequest(requestStr);
            logger.debug("Sending message: receiverId={}, senderId={}, content={}, tempId={}", currentFriendId,
                    currentUserId, content, tempId);

            Message localMsg = new Message();
            localMsg.setSenderId(currentUserId);
            localMsg.setReceiverId(currentFriendId);
            localMsg.setContent(content);
            localMsg.setCreatedAt(LocalDateTime.now());
            localMsg.setFile(false);
            localMsg.setStatus(Message.MessageStatus.SENDING);
            localMsg.setTempId(tempId);
            if (replyingToMessageId != null && replyingToMessageId > 0) {
                localMsg.setRepliedToMessageId(replyingToMessageId);
                localMsg.setRepliedToContent(replyingToContent);
            }

            if (messageListController != null) {
                messageListController.addLocalTextMessage(localMsg);
            }
            cancelReply(); // Clear reply after sending
            return true;
        } else {
            showAlert("Vui lòng chọn một người bạn hoặc nhóm để nhắn tin!");
            return false;
        }
    }
}