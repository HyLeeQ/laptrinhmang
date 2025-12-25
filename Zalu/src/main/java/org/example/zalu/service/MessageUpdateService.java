package org.example.zalu.service;

import org.example.zalu.model.ChatItem;
import org.example.zalu.model.Message;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service để quản lý last messages và updates
 */
public class MessageUpdateService {
    private final Map<Integer, String> lastMessages = new HashMap<>();
    private final Map<Integer, LocalDateTime> lastMessageTimes = new HashMap<>();

    public MessageUpdateService() {
        // No longer needs MessageDAO
    }

    /**
     * Update last messages cho tất cả chat items từ cache
     */
    public void updateLastMessages(List<ChatItem> chatItems, int currentUserId) {
        try {
            for (ChatItem item : chatItems) {
                int chatId = item.isGroup() ? -item.getGroup().getId() : item.getUser().getId();
                List<Message> messages = org.example.zalu.client.ClientCache.getInstance().getMessages(chatId);

                if (!messages.isEmpty()) {
                    // Sort descending by time if not already
                    List<Message> sorted = new ArrayList<>(messages);
                    sorted.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
                    Message msg = sorted.get(0);

                    String preview = formatMessagePreview(msg);
                    int id = item.isGroup() ? item.getGroup().getId() : item.getUser().getId();
                    lastMessages.put(id, preview);
                    lastMessageTimes.put(id, msg.getCreatedAt());
                }
            }
        } catch (Exception e) {
            System.err.println("Unexpected error in updateLastMessages: " + e.getMessage());
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
