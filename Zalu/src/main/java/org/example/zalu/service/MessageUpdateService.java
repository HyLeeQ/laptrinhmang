package org.example.zalu.service;

import org.example.zalu.dao.MessageDAO;
import org.example.zalu.model.ChatItem;
import org.example.zalu.model.Message;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service để quản lý last messages và updates
 */
public class MessageUpdateService {
    private final MessageDAO messageDAO;
    private final Map<Integer, String> lastMessages = new HashMap<>();
    private final Map<Integer, LocalDateTime> lastMessageTimes = new HashMap<>();
    
    public MessageUpdateService(MessageDAO messageDAO) {
        this.messageDAO = messageDAO;
    }
    
    /**
     * Update last messages cho tất cả chat items
     */
    public void updateLastMessages(List<ChatItem> chatItems, int currentUserId) {
        try {
            for (ChatItem item : chatItems) {
                try {
                    if (item.isGroup()) {
                        // Lấy tin nhắn cuối của nhóm
                        List<Message> groupMessages = messageDAO.getMessagesForGroup(item.getGroup().getId());
                        if (!groupMessages.isEmpty()) {
                            Message msg = groupMessages.get(groupMessages.size() - 1);
                            String preview = formatMessagePreview(msg);
                            int groupId = item.getGroup().getId();
                            lastMessages.put(groupId, preview);
                            lastMessageTimes.put(groupId, msg.getCreatedAt());
                        }
                    } else {
                        // Lấy tin nhắn cuối của bạn bè
                        List<Message> lastMsg = messageDAO.getLastMessagesPerFriend(currentUserId, item.getUser().getId(), 1);
                        if (!lastMsg.isEmpty()) {
                            Message msg = lastMsg.get(0);
                            String preview = formatMessagePreview(msg);
                            int friendId = item.getUser().getId();
                            lastMessages.put(friendId, preview);
                            lastMessageTimes.put(friendId, msg.getCreatedAt());
                        }
                    }
                } catch (org.example.zalu.exception.message.MessageException | 
                         org.example.zalu.exception.database.DatabaseException | 
                         org.example.zalu.exception.database.DatabaseConnectionException e) {
                    System.err.println("Error updating last message for item: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            System.err.println("Unexpected error in updateLastMessages: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Format message preview text
     */
    private String formatMessagePreview(Message msg) {
        if (msg.getContent() != null && !msg.getContent().trim().isEmpty()) {
            return msg.getContent();
        } else if (msg.getFileName() != null) {
            return "[File: " + msg.getFileName() + "]";
        }
        return "Bắt đầu trò chuyện...";
    }
    
    /**
     * Get last message preview cho một user/group
     */
    public String getLastMessage(int id) {
        return lastMessages.getOrDefault(id, "Bắt đầu trò chuyện...");
    }
    
    /**
     * Update last message cho một user/group
     */
    public void updateLastMessage(int id, String preview, LocalDateTime createdAt) {
        lastMessages.put(id, preview);
        if (createdAt != null) {
            lastMessageTimes.put(id, createdAt);
        }
    }
    
    /**
     * Update last message cho một user/group (overload không cần thời gian)
     */
    public void updateLastMessage(int id, String preview) {
        lastMessages.put(id, preview);
    }
    
    /**
     * Get last message time cho một user/group
     */
    public LocalDateTime getLastMessageTime(int id) {
        return lastMessageTimes.getOrDefault(id, LocalDateTime.MIN);
    }
    
    /**
     * Clear all last messages
     */
    public void clear() {
        lastMessages.clear();
        lastMessageTimes.clear();
    }
    
    /**
     * Get all last messages (for refresh)
     */
    public Map<Integer, String> getAllLastMessages() {
        return new HashMap<>(lastMessages);
    }
}

