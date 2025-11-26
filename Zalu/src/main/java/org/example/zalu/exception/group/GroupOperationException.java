package org.example.zalu.exception.group;

/**
 * Exception tổng quát cho các lỗi trong thao tác với group
 */
public class GroupOperationException extends Exception {
    public GroupOperationException(String message) {
        super(message);
    }

    public GroupOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}

