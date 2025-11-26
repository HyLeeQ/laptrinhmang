package org.example.zalu.exception.auth;

/**
 * Exception được ném khi đăng ký tài khoản thất bại
 */
public class RegistrationFailedException extends Exception {
    public RegistrationFailedException(String message) {
        super(message);
    }

    public RegistrationFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}

