package org.example.zalu.exception.message;

/**
 * Exception tổng quát cho các lỗi liên quan đến message
 */
public class MessageException extends Exception {
    public MessageException(String message) {
        super(message);
    }

    public MessageException(String message, Throwable cause) {
        super(message, cause);
    }
}

