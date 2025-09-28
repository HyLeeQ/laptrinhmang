package org.example.zalu.model;

import java.io.Serializable;
import java.time.LocalDateTime;

public class Message implements Serializable {
    private static final long serialVersionUID = 1L;
    private int id;
    private int sender_id;
    private int receiver_id;
    private String content;
    private LocalDateTime created_at;

    //constructor default
    public Message(){
    }

    //constructor full
    public Message(int id, int sender_id, int receiver_id, String content, LocalDateTime created_at) {
        this.id = id;
        this.sender_id = sender_id;
        this.receiver_id = receiver_id;
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("Content cannot be null or empty");
        }
        this.content = content;
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

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("Content cannot be null or empty");
        }
        this.content = content;
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