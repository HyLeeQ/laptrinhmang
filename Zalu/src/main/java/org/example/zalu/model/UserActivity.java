package org.example.zalu.model;

import java.time.LocalDateTime;

/**
 * Model để lưu thông tin hoạt động của người dùng trên server
 */
public class UserActivity {
    private int userId;
    private String username;
    private String activityType; // MESSAGE, FILE, GROUP_MESSAGE, LOGIN, LOGOUT, etc.
    private int targetUserId; // ID người nhận (nếu là tin nhắn)
    private int groupId; // ID nhóm (nếu là tin nhắn nhóm)
    private String encryptedContent; // Nội dung đã mã hóa/ẩn
    private LocalDateTime timestamp;
    private String status; // ONLINE, OFFLINE

    public UserActivity(int userId, String username, String activityType, LocalDateTime timestamp) {
        this.userId = userId;
        this.username = username;
        this.activityType = activityType;
        this.timestamp = timestamp;
        this.status = "ONLINE";
    }

    public UserActivity(int userId, String username, String activityType, 
                       int targetUserId, String encryptedContent, LocalDateTime timestamp) {
        this.userId = userId;
        this.username = username;
        this.activityType = activityType;
        this.targetUserId = targetUserId;
        this.encryptedContent = encryptedContent;
        this.timestamp = timestamp;
        this.status = "ONLINE";
    }

    public UserActivity(int userId, String username, String activityType, 
                       int groupId, String encryptedContent, LocalDateTime timestamp, boolean isGroup) {
        this.userId = userId;
        this.username = username;
        this.activityType = activityType;
        this.groupId = groupId;
        this.encryptedContent = encryptedContent;
        this.timestamp = timestamp;
        this.status = "ONLINE";
    }

    // Getters and Setters
    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getActivityType() {
        return activityType;
    }

    public void setActivityType(String activityType) {
        this.activityType = activityType;
    }

    public int getTargetUserId() {
        return targetUserId;
    }

    public void setTargetUserId(int targetUserId) {
        this.targetUserId = targetUserId;
    }

    public int getGroupId() {
        return groupId;
    }

    public void setGroupId(int groupId) {
        this.groupId = groupId;
    }

    public String getEncryptedContent() {
        return encryptedContent;
    }

    public void setEncryptedContent(String encryptedContent) {
        this.encryptedContent = encryptedContent;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * Mã hóa/ẩn nội dung tin nhắn để hiển thị trên server
     */
    public static String encryptContent(String content) {
        if (content == null || content.isEmpty()) {
            return "[Nội dung trống]";
        }
        // Mã hóa đơn giản: thay thế bằng dấu *
        int length = content.length();
        if (length <= 10) {
            return "***";
        } else if (length <= 50) {
            return "*****";
        } else {
            return "**********";
        }
    }

    /**
     * Format hiển thị hoạt động
     */
    public String toDisplayString() {
        String timeStr = timestamp.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
        switch (activityType) {
            case "MESSAGE":
                return String.format("[%s] %s (ID: %d) → %d | %s", 
                    timeStr, username, userId, targetUserId, encryptedContent);
            case "GROUP_MESSAGE":
                return String.format("[%s] %s (ID: %d) → Nhóm %d | %s", 
                    timeStr, username, userId, groupId, encryptedContent);
            case "FILE":
                return String.format("[%s] %s (ID: %d) → %d | [File: %s]", 
                    timeStr, username, userId, targetUserId, encryptedContent);
            case "GROUP_FILE":
                return String.format("[%s] %s (ID: %d) → Nhóm %d | [File: %s]", 
                    timeStr, username, userId, groupId, encryptedContent);
            case "LOGIN":
                return String.format("[%s] %s (ID: %d) đã đăng nhập", 
                    timeStr, username, userId);
            case "LOGOUT":
                return String.format("[%s] %s (ID: %d) đã đăng xuất", 
                    timeStr, username, userId);
            default:
                return String.format("[%s] %s (ID: %d) - %s", 
                    timeStr, username, userId, activityType);
        }
    }
}

