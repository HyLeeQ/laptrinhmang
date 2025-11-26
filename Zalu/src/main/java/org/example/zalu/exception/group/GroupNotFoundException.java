package org.example.zalu.exception.group;

/**
 * Exception được ném khi không tìm thấy group
 */
public class GroupNotFoundException extends Exception {
    public GroupNotFoundException(String message) {
        super(message);
    }

    public GroupNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}

