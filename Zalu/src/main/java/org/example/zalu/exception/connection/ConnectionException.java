package org.example.zalu.exception.connection;

/**
 * Exception được ném khi có lỗi kết nối network
 */
public class ConnectionException extends Exception {
    public ConnectionException(String message) {
        super(message);
    }

    public ConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}

