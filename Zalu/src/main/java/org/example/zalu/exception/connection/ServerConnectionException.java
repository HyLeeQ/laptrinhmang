package org.example.zalu.exception.connection;

/**
 * Exception được ném khi không thể kết nối đến server
 */
public class ServerConnectionException extends Exception {
    public ServerConnectionException(String message) {
        super(message);
    }

    public ServerConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}

