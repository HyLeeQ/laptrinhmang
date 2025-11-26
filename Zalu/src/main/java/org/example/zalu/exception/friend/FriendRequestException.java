package org.example.zalu.exception.friend;

/**
 * Exception được ném khi có lỗi trong quá trình gửi/xử lý friend request
 */
public class FriendRequestException extends Exception {
    public FriendRequestException(String message) {
        super(message);
    }

    public FriendRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}

