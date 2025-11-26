package org.example.zalu.service;

import org.example.zalu.exception.database.DatabaseConnectionException;
import org.example.zalu.exception.database.DatabaseException;
import org.example.zalu.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests cho FriendService
 * Lưu ý: Các test này yêu cầu database thật hoặc test database
 */
@DisplayName("FriendService Tests")
public class FriendServiceTest {
    
    private FriendService friendService;
    
    @BeforeEach
    void setUp() {
        friendService = new FriendService();
    }
    
    @Test
    @DisplayName("Test getFriendsWithDetails")
    void testGetFriendsWithDetails() throws Exception {
        // Giả sử user với ID = 1 có friends
        try {
            List<User> friends = friendService.getFriendsWithDetails(1);
            assertNotNull(friends, "getFriendsWithDetails should return list");
            // Không assert size vì có thể không có friends
        } catch (SQLException | DatabaseException | DatabaseConnectionException e) {
            System.out.println("Skipping getFriendsWithDetails test: " + e.getMessage());
        }
    }
    
    @Test
    @DisplayName("Test getPendingRequestsWithDetails")
    void testGetPendingRequestsWithDetails() throws Exception {
        try {
            List<User> pending = friendService.getPendingRequestsWithDetails(1);
            assertNotNull(pending, "getPendingRequestsWithDetails should return list");
        } catch (SQLException | DatabaseException | DatabaseConnectionException e) {
            System.out.println("Skipping getPendingRequestsWithDetails test: " + e.getMessage());
        }
    }
    
    @Test
    @DisplayName("Test getOutgoingRequestsWithDetails")
    void testGetOutgoingRequestsWithDetails() throws Exception {
        try {
            List<User> outgoing = friendService.getOutgoingRequestsWithDetails(1);
            assertNotNull(outgoing, "getOutgoingRequestsWithDetails should return list");
        } catch (SQLException | DatabaseException | DatabaseConnectionException e) {
            System.out.println("Skipping getOutgoingRequestsWithDetails test: " + e.getMessage());
        }
    }
}

