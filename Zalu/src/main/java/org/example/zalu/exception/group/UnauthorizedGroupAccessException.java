package org.example.zalu.exception.group;

/**
 * Exception được ném khi user không có quyền thực hiện thao tác trên group
 */
public class UnauthorizedGroupAccessException extends Exception {
    public UnauthorizedGroupAccessException(String message) {
        super(message);
    }

    public UnauthorizedGroupAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}

