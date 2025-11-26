package org.example.zalu.controller.media;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.example.zalu.util.IconUtil;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.example.zalu.model.Message;
import org.example.zalu.util.ui.ChatRenderer;

import java.io.ByteArrayInputStream;
import java.util.List;

public class MediaViewController {
    @FXML private Label subtitleLabel;
    @FXML private FlowPane mediaGridPane;
    @FXML private FlowPane allMediaGridPane;
    @FXML private javafx.scene.control.ScrollPane allMediaScrollPane;
    @FXML private javafx.scene.control.Button viewAllButton;
    
    private Stage dialogStage;
    private List<Message> messages;
    private boolean isGroup;
    private List<Message> allMediaMessages;
    private boolean showingAll = false;
    
    public void initialize() {
        // Initialize
    }
    
    public void setDialogStage(Stage stage) {
        this.dialogStage = stage;
    }
    
    public void setMessages(List<Message> messages, boolean isGroup) {
        this.messages = messages;
        this.isGroup = isGroup;
        loadMedia();
    }
    
    private void loadMedia() {
        if (messages == null || mediaGridPane == null) return;
        
        mediaGridPane.getChildren().clear();
        if (allMediaGridPane != null) {
            allMediaGridPane.getChildren().clear();
        }
        
        // Lọc và sắp xếp media messages (ảnh và video) theo thời gian mới nhất trước
        allMediaMessages = new java.util.ArrayList<>();
        for (Message msg : messages) {
            if (msg.getFileName() != null && msg.getFileData() != null && msg.getFileData().length > 0) {
                if (ChatRenderer.isImageFile(msg.getFileName()) || isVideoFile(msg.getFileName())) {
                    allMediaMessages.add(msg);
                }
            }
        }
        
        // Sắp xếp theo thời gian: mới nhất trước (DESC)
        allMediaMessages.sort((m1, m2) -> {
            if (m1.getCreatedAt() == null && m2.getCreatedAt() == null) return 0;
            if (m1.getCreatedAt() == null) return 1;
            if (m2.getCreatedAt() == null) return -1;
            return m2.getCreatedAt().compareTo(m1.getCreatedAt()); // DESC: mới nhất trước
        });
        
        int mediaCount = allMediaMessages.size();
        int displayCount = 0;
        int maxDisplay = 6; // Hiển thị 6 ảnh/video gần nhất (giống Zalo preview)
        
        // Hiển thị nút "Xem tất cả" nếu có nhiều hơn 6
        if (viewAllButton != null) {
            viewAllButton.setVisible(mediaCount > maxDisplay);
        }
        
        for (Message msg : allMediaMessages) {
            if (displayCount >= maxDisplay) break;
            addMediaItemToPane(msg, mediaGridPane);
            displayCount++;
        }
        
        if (subtitleLabel != null) {
            subtitleLabel.setText(mediaCount + " ảnh và video đã chia sẻ trong cuộc trò chuyện");
        }
    }
    
    private boolean isVideoFile(String fileName) {
        if (fileName == null) return false;
        String lower = fileName.toLowerCase();
        return lower.endsWith(".mp4") || lower.endsWith(".avi") || lower.endsWith(".mov") ||
               lower.endsWith(".wmv") || lower.endsWith(".flv") || lower.endsWith(".mkv");
    }
    
    @FXML
    private void handleViewAll() {
        if (allMediaMessages == null || allMediaGridPane == null || mediaGridPane == null) return;
        
        showingAll = !showingAll;
        
        if (showingAll) {
            // Hiển thị tất cả
            mediaGridPane.setVisible(false);
            mediaGridPane.setManaged(false);
            allMediaScrollPane.setVisible(true);
            allMediaScrollPane.setManaged(true);
            
            // Load tất cả ảnh vào allMediaGridPane
            allMediaGridPane.getChildren().clear();
            for (Message msg : allMediaMessages) {
                addMediaItemToPane(msg, allMediaGridPane);
            }
            
            if (viewAllButton != null) {
                viewAllButton.setText("Thu gọn");
            }
        } else {
            // Hiển thị preview 6 ảnh
            allMediaScrollPane.setVisible(false);
            allMediaScrollPane.setManaged(false);
            mediaGridPane.setVisible(true);
            mediaGridPane.setManaged(true);
            
            if (viewAllButton != null) {
                viewAllButton.setText("Xem tất cả");
            }
        }
    }
    
    private void addMediaItemToPane(Message msg, FlowPane pane) {
        if (ChatRenderer.isImageFile(msg.getFileName())) {
            try {
                Image image = new Image(new ByteArrayInputStream(msg.getFileData()), 200, 200, true, true);
                ImageView imageView = new ImageView(image);
                imageView.setFitWidth(180);
                imageView.setFitHeight(180);
                imageView.setPreserveRatio(true);
                imageView.setSmooth(true);
                imageView.setCache(true);
                
                StackPane mediaContainer = new StackPane();
                mediaContainer.setStyle(
                    "-fx-background-color: #f0f0f0; " +
                    "-fx-background-radius: 12; " +
                    "-fx-padding: 4; " +
                    "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 4, 0, 0, 2); " +
                    "-fx-cursor: hand;"
                );
                mediaContainer.getChildren().add(imageView);
                mediaContainer.setPrefSize(180, 180);
                mediaContainer.setMaxSize(180, 180);
                
                mediaContainer.setOnMouseEntered(e -> {
                    mediaContainer.setStyle(
                        "-fx-background-color: #e0e0e0; " +
                        "-fx-background-radius: 12; " +
                        "-fx-padding: 4; " +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 6, 0, 0, 3); " +
                        "-fx-cursor: hand;"
                    );
                });
                mediaContainer.setOnMouseExited(e -> {
                    mediaContainer.setStyle(
                        "-fx-background-color: #f0f0f0; " +
                        "-fx-background-radius: 12; " +
                        "-fx-padding: 4; " +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 4, 0, 0, 2); " +
                        "-fx-cursor: hand;"
                    );
                });
                
                mediaContainer.setOnMouseClicked(e -> {
                    System.out.println("Click image: " + msg.getFileName());
                });
                
                pane.getChildren().add(mediaContainer);
            } catch (Exception e) {
                System.err.println("Error loading image: " + e.getMessage());
            }
        } else if (isVideoFile(msg.getFileName())) {
            StackPane videoContainer = new StackPane();
            videoContainer.setStyle(
                "-fx-background-color: #1c1e21; " +
                "-fx-background-radius: 12; " +
                "-fx-padding: 8; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 4, 0, 0, 2); " +
                "-fx-cursor: hand;"
            );
            videoContainer.setPrefSize(180, 180);
            videoContainer.setMaxSize(180, 180);
            
            Label playIcon = IconUtil.getPlayIcon(48);
            videoContainer.getChildren().add(playIcon);
            
            Label fileName = new Label(msg.getFileName());
            fileName.setStyle("-fx-font-size: 11px; -fx-text-fill: white; -fx-wrap-text: true;");
            fileName.setMaxWidth(160);
            StackPane.setMargin(fileName, new Insets(140, 0, 0, 0));
            videoContainer.getChildren().add(fileName);
            
            videoContainer.setOnMouseEntered(e -> {
                videoContainer.setStyle(
                    "-fx-background-color: #2a2d31; " +
                    "-fx-background-radius: 12; " +
                    "-fx-padding: 8; " +
                    "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 6, 0, 0, 3); " +
                    "-fx-cursor: hand;"
                );
            });
            videoContainer.setOnMouseExited(e -> {
                videoContainer.setStyle(
                    "-fx-background-color: #1c1e21; " +
                    "-fx-background-radius: 12; " +
                    "-fx-padding: 8; " +
                    "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 4, 0, 0, 2); " +
                    "-fx-cursor: hand;"
                );
            });
            
            pane.getChildren().add(videoContainer);
        }
    }
    
    @FXML
    private void handleClose() {
        if (dialogStage != null) {
            dialogStage.close();
        }
    }
}

