package org.example.zalu.exception.message;

/**
 * Exception được ném khi gửi message thất bại
 */
public class MessageSendFailedException extends Exception {
    public MessageSendFailedException(String message) {
        super(message);
    }

    public MessageSendFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}

