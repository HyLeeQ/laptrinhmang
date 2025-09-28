package org.example.zalu.model;

import java.time.LocalDateTime;

public class VoiceMessage {
    private int id;
    private int sender_id;
    private int receiver_id;
    private String file_path;
    private LocalDateTime created_at;

    public VoiceMessage(int id, int sender_id, int receiver_id, String file_path, LocalDateTime created_at) {
        this.id = id;
        this.sender_id = sender_id;
        this.receiver_id = receiver_id;
        if (file_path == null || file_path.trim().isEmpty()) {
            throw new IllegalArgumentException("File path cannot be null or empty");
        }
        this.file_path = file_path;
        if (created_at == null) {
            this.created_at = LocalDateTime.now(); // Mặc định thời gian hiện tại nếu null
        } else {
            this.created_at = created_at;
        }
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getSenderId() {
        return sender_id;
    }

    public void setSenderId(int sender_id) {
        this.sender_id = sender_id;
    }

    public int getReceiverId() {
        return receiver_id;
    }

    public void setReceiverId(int receiver_id) {
        this.receiver_id = receiver_id;
    }

    public String getFilePath() {
        return file_path;
    }

    public void setFilePath(String file_path) {
        if (file_path == null || file_path.trim().isEmpty()) {
            throw new IllegalArgumentException("File path cannot be null or empty");
        }
        this.file_path = file_path;
    }

    public LocalDateTime getCreatedAt() {
        return created_at;
    }

    public void setCreatedAt(LocalDateTime created_at) {
        if (created_at == null) {
            this.created_at = LocalDateTime.now();
        } else {
            this.created_at = created_at;
        }
    }
}