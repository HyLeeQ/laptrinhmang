package org.example.zalu.exception.auth;

/**
 * Exception được ném khi không tìm thấy user
 */
public class UserNotFoundException extends Exception {
    public UserNotFoundException(String message) {
        super(message);
    }

    public UserNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}

