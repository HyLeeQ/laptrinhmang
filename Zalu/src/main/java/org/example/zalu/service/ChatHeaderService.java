package org.example.zalu.service;

import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import org.example.zalu.model.User;
import org.example.zalu.service.AvatarService;

/**
 * Service để quản lý chat header (tên, status, avatar)
 */
public class ChatHeaderService {
    private final HBox chatHeader;
    private final Label friendNameLabel;
    private final Label friendStatusLabel;
    private final ImageView friendAvatar;
    
    public ChatHeaderService(HBox chatHeader, Label friendNameLabel, 
                            Label friendStatusLabel, ImageView friendAvatar) {
        this.chatHeader = chatHeader;
        this.friendNameLabel = friendNameLabel;
        this.friendStatusLabel = friendStatusLabel;
        this.friendAvatar = friendAvatar;
    }
    
    /**
     * Hiển thị header với thông tin friend
     */
    public void showHeaderForFriend(User friend) {
        showHeaderForFriend(friend, false);
    }
    
    /**
     * Hiển thị header với thông tin friend và online status
     */
    public void showHeaderForFriend(User friend, boolean isOnline) {
        if (chatHeader != null) {
            chatHeader.setVisible(true);
            chatHeader.setManaged(true);
        }
        
        String displayName = (friend.getFullName() != null && !friend.getFullName().trim().isEmpty()) 
                ? friend.getFullName() 
                : friend.getUsername();
        friendNameLabel.setText(displayName);
        updateStatus(isOnline);
        
        // Load avatar
        loadAvatar(friend);
    }
    
    /**
     * Cập nhật status của friend trong header
     */
    public void updateFriendStatus(boolean isOnline) {
        updateStatus(isOnline);
    }
    
    private void updateStatus(boolean isOnline) {
        if (friendStatusLabel != null) {
            if (isOnline) {
                friendStatusLabel.setText("Đang hoạt động");
                friendStatusLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #31d559; -fx-font-weight: 500;");
            } else {
                friendStatusLabel.setText("Offline");
                friendStatusLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #8e8e93; -fx-font-weight: 500;");
            }
        }
    }
    
    /**
     * Hiển thị header với thông tin group
     */
    public void showHeaderForGroup(String groupName) {
        if (chatHeader != null) {
            chatHeader.setVisible(true);
            chatHeader.setManaged(true);
        }
        
        friendNameLabel.setText("👥 " + groupName);
        friendStatusLabel.setText("Nhóm");
    }
    
    /**
     * Ẩn header (welcome mode)
     */
    public void hideHeader() {
        if (chatHeader != null) {
            chatHeader.setVisible(false);
            chatHeader.setManaged(false);
        }
    }
    
    /**
     * Load avatar cho friend
     */
    private void loadAvatar(User friend) {
        if (friendAvatar == null || friend == null) return;
        
        try {
            javafx.scene.image.Image avatarImage = AvatarService.resolveAvatar(friend);
            if (avatarImage != null && !avatarImage.isError()) {
                friendAvatar.setImage(avatarImage);
            } else {
                javafx.scene.image.Image defaultAvatar = AvatarService.getDefaultAvatar();
                if (defaultAvatar != null) {
                    friendAvatar.setImage(defaultAvatar);
                }
            }
        } catch (Exception e) {
            System.err.println("Error loading avatar: " + e.getMessage());
        }
    }
}

