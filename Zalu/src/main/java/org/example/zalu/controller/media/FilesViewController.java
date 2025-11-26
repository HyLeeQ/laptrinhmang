package org.example.zalu.controller.media;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.example.zalu.client.ChatClient;
import org.example.zalu.client.ChatEventManager;
import org.example.zalu.model.Message;
import org.example.zalu.util.IconUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class FilesViewController {
    @FXML private Label subtitleLabel;
    @FXML private ListView<FileItem> filesListView;
    
    private Stage dialogStage;
    private List<Message> messages;
    private boolean isGroup;
    
    public static class FileItem {
        private String fileName;
        private long fileSize;
        private Message message;
        
        public FileItem(String fileName, long fileSize, Message message) {
            this.fileName = fileName;
            this.fileSize = fileSize;
            this.message = message;
        }
        
        public String getFileName() { return fileName; }
        public long getFileSize() { return fileSize; }
        public Message getMessage() { return message; }
        
        public String getFormattedSize() {
            if (fileSize < 1024) return fileSize + " B";
            if (fileSize < 1024 * 1024) return new DecimalFormat("#.##").format(fileSize / 1024.0) + " KB";
            return new DecimalFormat("#.##").format(fileSize / (1024.0 * 1024.0)) + " MB";
        }
        
        public String getFormattedDate() {
            if (message != null && message.getCreatedAt() != null) {
                return message.getCreatedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            }
            return "";
        }
        
        public String getFileExtension() {
            if (fileName == null) return "";
            int lastDot = fileName.lastIndexOf('.');
            if (lastDot > 0 && lastDot < fileName.length() - 1) {
                return fileName.substring(lastDot + 1).toLowerCase();
            }
            return "";
        }
    }
    
    public void initialize() {
        filesListView.setCellFactory(param -> new ListCell<FileItem>() {
            private HBox itemBox;
            private StackPane iconContainer;
            private VBox infoBox;
            private Label nameLabel;
            private Label sizeLabel;
            private Label dateLabel;
            private Label checkIcon;
            
            {
                itemBox = new HBox(12);
                itemBox.setAlignment(Pos.CENTER_LEFT);
                itemBox.setPadding(new Insets(12, 16, 12, 16));
                
                // File icon với màu theo extension
                iconContainer = new StackPane();
                iconContainer.setPrefSize(48, 48);
                
                // Info box
                infoBox = new VBox(4);
                infoBox.setAlignment(Pos.CENTER_LEFT);
                
                nameLabel = new Label();
                nameLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: 500; -fx-text-fill: #1c1e21;");
                
                HBox metaBox = new HBox(8);
                sizeLabel = new Label();
                sizeLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #8e8e93;");
                
                dateLabel = new Label();
                dateLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #8e8e93;");
                
                metaBox.getChildren().addAll(sizeLabel, new Label("•"), dateLabel);
                
                infoBox.getChildren().addAll(nameLabel, metaBox);
                
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                
                // Check icon
                checkIcon = IconUtil.getCheckIcon(18);
                
                itemBox.getChildren().addAll(iconContainer, infoBox, spacer, checkIcon);
            }
            
            @Override
            protected void updateItem(FileItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(null);
                    
                    // Set file name
                    nameLabel.setText(item.getFileName());
                    
                    // Set size and date
                    sizeLabel.setText(item.getFormattedSize());
                    dateLabel.setText(item.getFormattedDate());
                    
                    // Create file icon với màu
                    iconContainer.getChildren().clear();
                    String ext = item.getFileExtension();
                    Color iconColor = getFileIconColor(ext);
                    String iconText = getFileIconText(ext);
                    
                    Rectangle iconBg = new Rectangle(48, 48);
                    iconBg.setFill(iconColor);
                    iconBg.setArcWidth(12);
                    iconBg.setArcHeight(12);
                    
                    Label iconLabel = new Label(iconText);
                    iconLabel.setStyle("-fx-font-size: 20px; -fx-text-fill: white; -fx-font-weight: bold;");
                    
                    iconContainer.getChildren().addAll(iconBg, iconLabel);
                    
                    setGraphic(itemBox);
                    setStyle("-fx-cursor: hand;");
                }
            }
        });
        
        filesListView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                FileItem selected = filesListView.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    downloadOrOpenFile(selected);
                }
            }
        });
    }
    
    private Color getFileIconColor(String extension) {
        switch (extension) {
            case "doc":
            case "docx":
                return Color.web("#2b579a"); // Blue for Word
            case "xls":
            case "xlsx":
                return Color.web("#1d6f42"); // Green for Excel
            case "ppt":
            case "pptx":
                return Color.web("#d04423"); // Orange for PowerPoint
            case "pdf":
                return Color.web("#e53e3e"); // Red for PDF
            case "zip":
            case "rar":
            case "7z":
                return Color.web("#9c27b0"); // Purple for ZIP
            case "txt":
                return Color.web("#607d8b"); // Blue-grey for text
            default:
                return Color.web("#757575"); // Grey for others
        }
    }
    
    private String getFileIconText(String extension) {
        switch (extension) {
            case "doc":
            case "docx":
                return "W";
            case "xls":
            case "xlsx":
                return "X";
            case "ppt":
            case "pptx":
                return "P";
            case "pdf":
                return "PDF";
            case "zip":
            case "rar":
            case "7z":
                return "ZIP";
            case "txt":
                return "TXT";
            default:
                return "FILE";
        }
    }
    
    public void setDialogStage(Stage stage) {
        this.dialogStage = stage;
    }
    
    public void setMessages(List<Message> messages, boolean isGroup) {
        this.messages = messages;
        this.isGroup = isGroup;
        loadFiles();
    }
    
    private void loadFiles() {
        if (messages == null || filesListView == null) return;
        
        List<FileItem> files = new ArrayList<>();
        for (Message msg : messages) {
            if (msg.getFileName() != null && !msg.getFileName().trim().isEmpty()) {
                // Skip images and videos (they're in media view)
                if (!org.example.zalu.util.ui.ChatRenderer.isImageFile(msg.getFileName()) &&
                    !isVideoFile(msg.getFileName())) {
                    long fileSize = msg.getFileData() != null ? msg.getFileData().length : 0;
                    files.add(new FileItem(msg.getFileName(), fileSize, msg));
                }
            }
        }
        
        // Sắp xếp theo thời gian: mới nhất trước (DESC)
        files.sort((f1, f2) -> {
            Message m1 = f1.getMessage();
            Message m2 = f2.getMessage();
            if (m1 == null || m1.getCreatedAt() == null) return 1;
            if (m2 == null || m2.getCreatedAt() == null) return -1;
            return m2.getCreatedAt().compareTo(m1.getCreatedAt()); // DESC: mới nhất trước
        });
        
        filesListView.getItems().setAll(files);
        
        if (subtitleLabel != null) {
            subtitleLabel.setText(files.size() + " file đã chia sẻ trong cuộc trò chuyện");
        }
    }
    
    private boolean isVideoFile(String fileName) {
        if (fileName == null) return false;
        String lower = fileName.toLowerCase();
        return lower.endsWith(".mp4") || lower.endsWith(".avi") || lower.endsWith(".mov") ||
               lower.endsWith(".wmv") || lower.endsWith(".flv") || lower.endsWith(".mkv");
    }
    
    @FXML
    private void handleClose() {
        if (dialogStage != null) {
            dialogStage.close();
        }
    }
    
    private void downloadOrOpenFile(FileItem fileItem) {
        Message message = fileItem.getMessage();
        if (message == null) {
            showAlert("Không tìm thấy thông tin tin nhắn");
            return;
        }
        
        int messageId = message.getId();
        String fileName = fileItem.getFileName();
        
        // Nếu file đã có data trong message, mở trực tiếp
        if (message.getFileData() != null && message.getFileData().length > 0) {
            openFileDirectly(fileName, message.getFileData());
            return;
        }
        
        // Nếu chưa có data, request từ server
        System.out.println("Requesting file download for messageId: " + messageId);
        
        // Register callback để xử lý file download response
        ChatEventManager.getInstance().registerFileDownloadCallback(downloadInfo -> {
            if (downloadInfo != null && downloadInfo.getMessageId() == messageId) {
                openFileDirectly(downloadInfo.getFileName(), downloadInfo.getFileData());
            } else if (downloadInfo == null) {
                Platform.runLater(() -> {
                    showAlert("Không thể tải file. Vui lòng thử lại.");
                });
            }
        });
        
        // Gửi request download
        ChatClient.sendRequest("GET_FILE|" + messageId);
    }
    
    private void openFileDirectly(String fileName, byte[] fileData) {
        Platform.runLater(() -> {
            try {
                // Tạo file tạm
                File tempFile = File.createTempFile("zalu_", "_" + fileName);
                tempFile.deleteOnExit();
                
                // Lưu data vào file tạm
                try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                    fos.write(fileData);
                    fos.flush();
                }
                
                // Mở file
                try {
                    java.awt.Desktop.getDesktop().open(tempFile);
                } catch (Exception e) {
                    // Nếu không mở được, cho phép user chọn nơi lưu
                    FileChooser fileChooser = new FileChooser();
                    fileChooser.setTitle("Lưu file");
                    fileChooser.setInitialFileName(fileName);
                    fileChooser.setInitialDirectory(new File(System.getProperty("user.home") + "/Downloads"));
                    
                    File selectedFile = fileChooser.showSaveDialog(dialogStage);
                    if (selectedFile != null) {
                        try (FileOutputStream fos = new FileOutputStream(selectedFile)) {
                            fos.write(fileData);
                            fos.flush();
                        }
                        showAlert("File đã được lưu tại: " + selectedFile.getAbsolutePath());
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                showAlert("Lỗi khi mở file: " + e.getMessage());
            }
        });
    }
    
    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Thông báo");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}

