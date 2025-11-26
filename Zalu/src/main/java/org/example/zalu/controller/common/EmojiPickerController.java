package org.example.zalu.controller.common;

import javafx.animation.ScaleTransition;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.layout.FlowPane;
import javafx.util.Duration;
import org.example.zalu.controller.chat.ChatController;

public class EmojiPickerController {
    
    @FXML private FlowPane emojiGrid;
    
    private ChatController chatController;
    
    // Danh sách emoji mặt người phổ biến
    private static final String[] EMOJIS = {
        "😊", "😃", "😄", "😁", "😂",
        "😍", "🥰", "😘", "😉", "😎",
        "🤔", "😏", "😒", "😞", "😢",
        "😭", "😤", "😠", "😡", "😱",
        "😳", "🥺", "😴", "🤗", "🙄"
    };
    
    @FXML
    public void initialize() {
        createEmojiButtons();
    }
    
    private void createEmojiButtons() {
        for (String emoji : EMOJIS) {
            Button btn = new Button(emoji);
            btn.getStyleClass().add("emoji-btn");
            btn.setOnAction(e -> onEmojiSelected(emoji));
            
            // Thêm animation khi hover
            btn.setOnMouseEntered(e -> {
                ScaleTransition st = new ScaleTransition(Duration.millis(150), btn);
                st.setToX(1.15);
                st.setToY(1.15);
                st.play();
            });
            
            btn.setOnMouseExited(e -> {
                ScaleTransition st = new ScaleTransition(Duration.millis(150), btn);
                st.setToX(1.0);
                st.setToY(1.0);
                st.play();
            });
            
            btn.setOnMousePressed(e -> {
                ScaleTransition st = new ScaleTransition(Duration.millis(100), btn);
                st.setToX(0.9);
                st.setToY(0.9);
                st.play();
            });
            
            btn.setOnMouseReleased(e -> {
                ScaleTransition st = new ScaleTransition(Duration.millis(100), btn);
                st.setToX(1.0);
                st.setToY(1.0);
                st.play();
            });
            
            emojiGrid.getChildren().add(btn);
        }
    }
    
    private void onEmojiSelected(String emoji) {
        if (chatController != null) {
            chatController.insertEmoji(emoji);
        }
    }
    
    public void setChatController(ChatController controller) {
        this.chatController = controller;
    }
}

