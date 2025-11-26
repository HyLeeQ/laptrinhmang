package org.example.zalu.exception.validation;

/**
 * Exception được ném khi dữ liệu đầu vào không hợp lệ
 */
public class ValidationException extends Exception {
    public ValidationException(String message) {
        super(message);
    }

    public ValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}

