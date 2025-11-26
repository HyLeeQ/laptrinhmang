package org.example.zalu.server;

/**
 * Model classes cho Server UI
 */
public class ServerModels {
    // Model cho bảng Account Online
    public static class OnlineUser {
        private final int userId;
        private final String status;
        
        public OnlineUser(int userId, String status) {
            this.userId = userId;
            this.status = status;
        }
        
        public int getUserId() { return userId; }
        public String getStatus() { return status; }
    }
    
    // Model cho bảng Hoạt động
    public static class ActivityRecord {
        private final int activeUserId;
        private final Integer passiveUserId;
        private final String action;
        private final String content;
        
        public ActivityRecord(int activeUserId, Integer passiveUserId, String action, String content) {
            this.activeUserId = activeUserId;
            this.passiveUserId = passiveUserId;
            this.action = action;
            this.content = content;
        }
        
        public int getActiveUserId() { return activeUserId; }
        public Integer getPassiveUserId() { return passiveUserId; }
        public String getAction() { return action; }
        public String getContent() { return content; }
    }
}

