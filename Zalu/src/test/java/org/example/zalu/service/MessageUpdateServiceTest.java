package org.example.zalu.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests cho MessageUpdateService
 * Test các method không cần database connection
 */
@DisplayName("MessageUpdateService Tests")
public class MessageUpdateServiceTest {

    private MessageUpdateService messageUpdateService;

    @BeforeEach
    void setUp() {
        messageUpdateService = new MessageUpdateService();
    }

    @Test
    @DisplayName("Test updateLastMessage")
    void testUpdateLastMessage() {
        int userId = 1;
        String preview = "Test message preview";
        LocalDateTime now = LocalDateTime.now();

        messageUpdateService.updateLastMessage(userId, preview, now);

        String result = messageUpdateService.getLastMessage(userId);
        assertEquals(preview, result, "Last message should match");

        LocalDateTime resultTime = messageUpdateService.getLastMessageTime(userId);
        assertEquals(now, resultTime, "Last message time should match");
    }

    @Test
    @DisplayName("Test updateLastMessage without time")
    void testUpdateLastMessage_WithoutTime() {
        int userId = 2;
        String preview = "Another message";

        messageUpdateService.updateLastMessage(userId, preview);

        String result = messageUpdateService.getLastMessage(userId);
        assertEquals(preview, result, "Last message should match");
    }

    @Test
    @DisplayName("Test getLastMessage với ID không tồn tại")
    void testGetLastMessage_NotFound() {
        String result = messageUpdateService.getLastMessage(999);
        assertEquals("Bắt đầu trò chuyện...", result,
                "Should return default message for non-existent ID");
    }

    @Test
    @DisplayName("Test getLastMessageTime với ID không tồn tại")
    void testGetLastMessageTime_NotFound() {
        LocalDateTime result = messageUpdateService.getLastMessageTime(999);
        assertEquals(LocalDateTime.MIN, result,
                "Should return LocalDateTime.MIN for non-existent ID");
    }

    @Test
    @DisplayName("Test clear")
    void testClear() {
        // Add some messages
        messageUpdateService.updateLastMessage(1, "Message 1");
        messageUpdateService.updateLastMessage(2, "Message 2");

        // Clear
        messageUpdateService.clear();

        // Verify cleared
        String result1 = messageUpdateService.getLastMessage(1);
        String result2 = messageUpdateService.getLastMessage(2);

        assertEquals("Bắt đầu trò chuyện...", result1, "Should be cleared");
        assertEquals("Bắt đầu trò chuyện...", result2, "Should be cleared");
    }

    @Test
    @DisplayName("Test getAllLastMessages")
    void testGetAllLastMessages() {
        // Add some messages
        messageUpdateService.updateLastMessage(1, "Message 1");
        messageUpdateService.updateLastMessage(2, "Message 2");

        Map<Integer, String> allMessages = messageUpdateService.getAllLastMessages();

        assertNotNull(allMessages, "getAllLastMessages should return map");
        assertEquals(2, allMessages.size(), "Should have 2 messages");
        assertEquals("Message 1", allMessages.get(1), "First message should match");
        assertEquals("Message 2", allMessages.get(2), "Second message should match");
    }
}
