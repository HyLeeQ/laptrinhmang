package org.example.zalu.controller.common;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;

import java.io.ByteArrayInputStream;

public class ImagePreviewController {
    
    @FXML private ImageView previewImageView;
    @FXML private Label fileNameLabel;
    @FXML private Label fileSizeLabel;
    @FXML private Button sendButton;
    @FXML private Button cancelButton;
    
    private byte[] imageData;
    private String fileName;
    private Runnable onSendCallback;
    private Runnable onCancelCallback;
    private Stage dialogStage;
    
    public void setImageData(byte[] imageData, String fileName) {
        this.imageData = imageData;
        this.fileName = fileName;
        
        // Load và hiển thị ảnh
        if (imageData != null && imageData.length > 0) {
            Image image = new Image(new ByteArrayInputStream(imageData), 500, 400, true, true);
            previewImageView.setImage(image);
        }
        
        // Hiển thị tên file và kích thước
        if (fileName != null) {
            fileNameLabel.setText("Tên file: " + fileName);
        }
        
        if (imageData != null) {
            fileSizeLabel.setText("Kích thước: " + formatFileSize(imageData.length));
        }
    }
    
    public void setOnSendCallback(Runnable callback) {
        this.onSendCallback = callback;
    }
    
    public void setOnCancelCallback(Runnable callback) {
        this.onCancelCallback = callback;
    }
    
    public void setDialogStage(Stage stage) {
        this.dialogStage = stage;
    }
    
    @FXML
    private void handleSend() {
        if (onSendCallback != null) {
            onSendCallback.run();
        }
        if (dialogStage != null) {
            dialogStage.close();
        }
    }
    
    @FXML
    private void handleCancel() {
        if (onCancelCallback != null) {
            onCancelCallback.run();
        }
        if (dialogStage != null) {
            dialogStage.close();
        }
    }
    
    public byte[] getImageData() {
        return imageData;
    }
    
    public String getFileName() {
        return fileName;
    }
    
    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }
}

