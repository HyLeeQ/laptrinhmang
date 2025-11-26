package org.example.zalu.exception.friend;

/**
 * Exception được ném khi không tìm thấy friend relationship
 */
public class FriendNotFoundException extends Exception {
    public FriendNotFoundException(String message) {
        super(message);
    }

    public FriendNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}

