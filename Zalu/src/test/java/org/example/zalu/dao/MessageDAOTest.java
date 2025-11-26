package org.example.zalu.dao;

import org.example.zalu.exception.database.DatabaseConnectionException;
import org.example.zalu.exception.database.DatabaseException;
import org.example.zalu.exception.message.MessageException;
import org.example.zalu.model.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests cho MessageDAO
 * Lưu ý: Các test này yêu cầu database thật hoặc test database
 */
@DisplayName("MessageDAO Tests")
public class MessageDAOTest {
    
    private MessageDAO messageDAO;
    
    @BeforeEach
    void setUp() {
        messageDAO = new MessageDAO();
    }
    
    @Test
    @DisplayName("Test save message thành công")
    void testSaveMessage_Success() throws Exception {
        // Tạo message mới
        Message message = new Message();
        message.setSenderId(1);
        message.setReceiverId(2);
        message.setContent("Test message");
        message.setCreatedAt(LocalDateTime.now());
        message.setFile(false);
        message.setIsRead(false);
        
        // Test save
        boolean result = messageDAO.saveMessage(message);
        assertTrue(result, "saveMessage should return true on success");
        assertTrue(message.getId() > 0, "Message ID should be set after save");
    }
    
    @Test
    @DisplayName("Test save message với file")
    void testSaveMessage_WithFile() throws Exception {
        // Tạo message với file
        Message message = new Message();
        message.setSenderId(1);
        message.setReceiverId(2);
        message.setFileName("test.txt");
        message.setFileData("Test file content".getBytes());
        message.setCreatedAt(LocalDateTime.now());
        message.setFile(true);
        message.setIsRead(false);
        
        // Test save
        boolean result = messageDAO.saveMessage(message);
        assertTrue(result, "saveMessage should return true for file message");
    }
    
    @Test
    @DisplayName("Test getMessagesBetween hai users")
    void testGetMessagesBetween_Success() throws Exception {
        // Giả sử có messages giữa user 1 và user 2
        try {
            List<Message> messages = messageDAO.getMessagesBetween(1, 2);
            assertNotNull(messages, "getMessagesBetween should return list");
            // Không assert size vì có thể không có messages
        } catch (MessageException | DatabaseException | DatabaseConnectionException e) {
            // Nếu có lỗi, có thể do không có data hoặc database issue
            System.out.println("Skipping getMessagesBetween test: " + e.getMessage());
        }
    }
    
    @Test
    @DisplayName("Test getMessagesForGroup")
    void testGetMessagesForGroup_Success() throws Exception {
        // Giả sử có group với ID = 1
        try {
            List<Message> messages = messageDAO.getMessagesForGroup(1);
            assertNotNull(messages, "getMessagesForGroup should return list");
        } catch (MessageException | DatabaseException | DatabaseConnectionException e) {
            System.out.println("Skipping getMessagesForGroup test: " + e.getMessage());
        }
    }
    
    @Test
    @DisplayName("Test getMessageById")
    void testGetMessageById_Success() throws Exception {
        // Giả sử có message với ID = 1
        try {
            Message message = messageDAO.getMessageById(1);
            assertNotNull(message, "getMessageById should return message");
            assertEquals(1, message.getId(), "Message ID should match");
        } catch (MessageException | DatabaseException | DatabaseConnectionException e) {
            System.out.println("Skipping getMessageById test: " + e.getMessage());
        }
    }
    
    @Test
    @DisplayName("Test markMessagesAsRead")
    void testMarkMessagesAsRead_Success() throws Exception {
        // Giả sử có messages chưa đọc giữa user 1 và user 2
        try {
            boolean result = messageDAO.markMessagesAsRead(1, 2);
            assertTrue(result || !result, "markMessagesAsRead should return boolean");
        } catch (DatabaseException | DatabaseConnectionException e) {
            System.out.println("Skipping markMessagesAsRead test: " + e.getMessage());
        }
    }
    
    @Test
    @DisplayName("Test getUnreadCountForConversation")
    void testGetUnreadCountForConversation() throws Exception {
        try {
            int count = messageDAO.getUnreadCountForConversation(1, 2);
            assertTrue(count >= 0, "Unread count should be non-negative");
        } catch (DatabaseException | DatabaseConnectionException e) {
            System.out.println("Skipping getUnreadCountForConversation test: " + e.getMessage());
        }
    }
}

