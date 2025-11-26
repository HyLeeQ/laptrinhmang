package org.example.zalu.dao;

import org.example.zalu.exception.auth.RegistrationFailedException;
import org.example.zalu.exception.auth.UserNotFoundException;
import org.example.zalu.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests cho UserDAO
 * Lưu ý: Các test này yêu cầu database thật hoặc test database
 * Để chạy test, cần có database laptrinhmang_db với bảng users
 */
@DisplayName("UserDAO Tests")
public class UserDAOTest {
    
    private UserDAO userDAO;
    
    @BeforeEach
    void setUp() {
        userDAO = new UserDAO();
    }
    
    @Test
    @DisplayName("Test register user thành công")
    void testRegisterUser_Success() throws Exception {
        // Tạo user mới với username unique
        String uniqueUsername = "testuser_" + System.currentTimeMillis();
        User newUser = new User(-1, uniqueUsername, "Test User", "password123", 
                                "test@example.com", "0123456789", 
                                "/images/default-avatar.jpg", "", 
                                LocalDate.of(1990, 1, 1), "offline", "male");
        
        // Test register
        boolean result = userDAO.register(newUser);
        assertTrue(result, "Register should return true on success");
    }
    
    @Test
    @DisplayName("Test register user với username đã tồn tại")
    void testRegisterUser_DuplicateUsername() throws Exception {
        // Tạo user với username đã tồn tại (giả sử "admin" đã tồn tại)
        User duplicateUser = new User(-1, "admin", "Duplicate User", "password123",
                                     "duplicate@example.com", "0987654321",
                                     "/images/default-avatar.jpg", "",
                                     LocalDate.of(1990, 1, 1), "offline", "male");
        
        // Test register với username trùng
        assertThrows(RegistrationFailedException.class, () -> {
            userDAO.register(duplicateUser);
        }, "Should throw RegistrationFailedException for duplicate username");
    }
    
    @Test
    @DisplayName("Test login với username và password đúng")
    void testLogin_Success() throws Exception {
        // Giả sử có user "admin" với password "admin" trong database
        // Lưu ý: Cần có user thật trong database để test này pass
        try {
            User user = userDAO.login("admin", "admin");
            assertNotNull(user, "Login should return user object");
            assertEquals("admin", user.getUsername(), "Username should match");
        } catch (UserNotFoundException e) {
            // Nếu không có user "admin", skip test này
            System.out.println("Skipping login test - user 'admin' not found in database");
        }
    }
    
    @Test
    @DisplayName("Test login với username không tồn tại")
    void testLogin_UserNotFound() {
        assertThrows(UserNotFoundException.class, () -> {
            userDAO.login("nonexistent_user_12345", "password");
        }, "Should throw UserNotFoundException for non-existent user");
    }
    
    @Test
    @DisplayName("Test login với password sai")
    void testLogin_InvalidPassword() {
        // Giả sử có user "admin" trong database
        // Nếu user không tồn tại, sẽ throw UserNotFoundException
        // Nếu user tồn tại nhưng password sai, sẽ throw InvalidCredentialsException
        assertThrows(Exception.class, () -> {
            userDAO.login("admin", "wrong_password");
        }, "Should throw InvalidCredentialsException or UserNotFoundException");
    }
    
    @Test
    @DisplayName("Test getUserById với ID hợp lệ")
    void testGetUserById_Success() throws Exception {
        // Giả sử có user với ID = 1 trong database
        try {
            User user = userDAO.getUserById(1);
            assertNotNull(user, "getUserById should return user object");
            assertEquals(1, user.getId(), "User ID should match");
        } catch (UserNotFoundException e) {
            // Nếu không có user với ID = 1, skip test này
            System.out.println("Skipping getUserById test - user with ID 1 not found in database");
        }
    }
    
    @Test
    @DisplayName("Test getUserById với ID không tồn tại")
    void testGetUserById_NotFound() {
        assertThrows(UserNotFoundException.class, () -> {
            userDAO.getUserById(999999);
        }, "Should throw UserNotFoundException for non-existent user ID");
    }
    
    @Test
    @DisplayName("Test register với dữ liệu null")
    void testRegisterUser_NullData() {
        // Test với username null
        User nullUser = new User(-1, null, "Test", "password", 
                                 "test@example.com", "123", 
                                 "/images/default-avatar.jpg", "",
                                 null, "offline", "male");
        
        assertThrows(Exception.class, () -> {
            userDAO.register(nullUser);
        }, "Should throw exception for null username");
    }
}

