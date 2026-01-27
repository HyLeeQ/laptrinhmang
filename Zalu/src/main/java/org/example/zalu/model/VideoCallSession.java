package org.example.zalu.model;

import java.time.LocalDateTime;

/**
 * Model cho phiên video call
 */
public class VideoCallSession {
    private int callId;
    private int callerId;
    private String callerName;
    private int receiverId;
    private String receiverName;
    private CallStatus status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    public enum CallStatus {
        CALLING, // Đang gọi
        RINGING, // Đang đổ chuông
        CONNECTED, // Đã kết nối
        ENDED, // Đã kết thúc
        REJECTED, // Bị từ chối
        MISSED, // Nhỡ cuộc gọi
        BUSY // Bận
    }

    public VideoCallSession() {
    }

    public VideoCallSession(int callerId, String callerName, int receiverId, String receiverName) {
        this.callerId = callerId;
        this.callerName = callerName;
        this.receiverId = receiverId;
        this.receiverName = receiverName;
        this.status = CallStatus.CALLING;
        this.startTime = LocalDateTime.now();
    }

    // Getters and Setters
    public int getCallId() {
        return callId;
    }

    public void setCallId(int callId) {
        this.callId = callId;
    }

    public int getCallerId() {
        return callerId;
    }

    public void setCallerId(int callerId) {
        this.callerId = callerId;
    }

    public String getCallerName() {
        return callerName;
    }

    public void setCallerName(String callerName) {
        this.callerName = callerName;
    }

    public int getReceiverId() {
        return receiverId;
    }

    public void setReceiverId(int receiverId) {
        this.receiverId = receiverId;
    }

    public String getReceiverName() {
        return receiverName;
    }

    public void setReceiverName(String receiverName) {
        this.receiverName = receiverName;
    }

    public CallStatus getStatus() {
        return status;
    }

    public void setStatus(CallStatus status) {
        this.status = status;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }
}
