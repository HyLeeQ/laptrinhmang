package org.example.zalu.exception.validation;

/**
 * Exception được ném khi input không hợp lệ (email, phone, password, etc.)
 */
public class InvalidInputException extends Exception {
    public InvalidInputException(String message) {
        super(message);
    }

    public InvalidInputException(String message, Throwable cause) {
        super(message, cause);
    }
}

