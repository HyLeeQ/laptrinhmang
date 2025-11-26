package org.example.zalu.exception.database;

/**
 * Exception được ném khi không thể kết nối đến database
 */
public class DatabaseConnectionException extends Exception {
    public DatabaseConnectionException(String message) {
        super(message);
    }

    public DatabaseConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}

