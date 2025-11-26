package org.example.zalu.exception.auth;

/**
 * Exception được ném khi đăng nhập thất bại
 */
public class LoginFailedException extends Exception {
    public LoginFailedException(String message) {
        super(message);
    }

    public LoginFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}

