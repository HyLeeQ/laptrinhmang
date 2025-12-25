package org.example.zalu.model;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Model để gửi báo cáo lỗi từ Client về Server
 */
public class ClientErrorLog implements Serializable {
    private static final long serialVersionUID = 1L;

    private int userId;
    private String username;
    private String errorMessage;
    private String stackTrace;
    private LocalDateTime timestamp;
    private String osInfo;

    public ClientErrorLog(int userId, String username, String errorMessage, String stackTrace) {
        this.userId = userId;
        this.username = username;
        this.errorMessage = errorMessage;
        this.stackTrace = stackTrace;
        this.timestamp = LocalDateTime.now();
        this.osInfo = System.getProperty("os.name") + " " + System.getProperty("os.version");
    }

    public int getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getStackTrace() {
        return stackTrace;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getOsInfo() {
        return osInfo;
    }

    @Override
    public String toString() {
        return "ERROR [" + timestamp + "] User: " + username + " (ID:" + userId + ") - " + errorMessage;
    }
}
