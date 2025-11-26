package org.example.zalu.exception.friend;

/**
 * Exception được ném khi user đã là bạn hoặc đã gửi lời mời kết bạn
 */
public class FriendAlreadyExistsException extends Exception {
    public FriendAlreadyExistsException(String message) {
        super(message);
    }

    public FriendAlreadyExistsException(String message, Throwable cause) {
        super(message, cause);
    }
}

