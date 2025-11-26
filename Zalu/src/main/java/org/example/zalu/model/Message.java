package org.example.zalu.model;

import java.io.Serializable;
import java.time.LocalDateTime;

public class Message implements Serializable {
    private static final long serialVersionUID = 1L;
    private int id;
    private int senderId;
    private int receiverId;
    private String content;
    private byte[] fileData;
    private String fileName;
    private boolean isRead;
    private LocalDateTime createdAt;
    private int groupId = 0;  // Mới: 0 cho 1-1, >0 cho group chat
    private boolean isDeleted = false;  // Xóa cho mình
    private boolean isRecalled = false;  // Thu hồi (xóa cho cả hai)
    private boolean isEdited = false;  // Đã chỉnh sửa
    private String editedContent = null;  // Nội dung sau khi sửa
    private int repliedToMessageId = 0;  // ID tin nhắn được trả lời (0 = không phải reply)
    private String repliedToContent = null;  // Nội dung preview tin nhắn được trả lời
    private boolean isPinned = false;  // Tin nhắn đã được ghim

    // Constructor mặc định
    public Message() {
    }

    // THÊM MỚI: Constructor 8-param đầy đủ (tương thích DAO cũ, default groupId=0)
    public Message(int id, int senderId, int receiverId, String content, byte[] fileData, String fileName, boolean isRead, LocalDateTime createdAt) {
        this(id, senderId, receiverId, content, fileData, fileName, isRead, createdAt, 0);  // Gọi 9-param với groupId=0
    }

    // Constructor 9-param đầy đủ (cho cả text và file)
    public Message(int id, int senderId, int receiverId, String content, byte[] fileData, String fileName, boolean isRead, LocalDateTime createdAt, int groupId) {
        this.id = id;
        this.senderId = senderId;
        this.receiverId = receiverId;
        // SỬA: Cho phép content null cho file message (chỉ check empty nếu content != null)
        if (content != null) {
            if (content.trim().isEmpty()) {
                throw new IllegalArgumentException("Content cannot be empty (trimmed)");
            }
            this.content = content.trim();
        } else {
            this.content = null;  // Giữ null cho file
        }
        this.fileData = fileData;
        this.fileName = fileName;
        this.isRead = isRead;
        if (createdAt == null) {
            this.createdAt = LocalDateTime.now();
        } else {
            this.createdAt = createdAt;
        }
        this.groupId = groupId;
    }

    // Constructor cho text message (fileData = null, groupId=0)
    public Message(int id, int senderId, int receiverId, String content, boolean isRead, LocalDateTime timestamp) {
        this(id, senderId, receiverId, content, null, null, isRead, timestamp, 0);
    }

    // Constructor cho text group message (groupId >0)
    public Message(int id, int senderId, int receiverId, String content, boolean isRead, LocalDateTime timestamp, int groupId) {
        this(id, senderId, receiverId, content, null, null, isRead, timestamp, groupId);
    }

    // Getters/Setters (giữ nguyên + groupId)
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getSenderId() {
        return senderId;
    }

    public void setSenderId(int senderId) {
        this.senderId = senderId;
    }

    public int getReceiverId() {
        return receiverId;
    }

    public void setReceiverId(int receiverId) {
        this.receiverId = receiverId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        if (content != null) {
            if (content.trim().isEmpty()) {
                throw new IllegalArgumentException("Content cannot be empty (trimmed)");
            }
            this.content = content.trim();
        } else {
            this.content = null;  // Cho phép null cho file
        }
    }

    public byte[] getFileData() {
        return fileData;
    }

    public void setFileData(byte[] fileData) {
        this.fileData = fileData;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public boolean getIsRead() {
        return isRead;
    }

    public void setIsRead(boolean isRead) {
        this.isRead = isRead;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        if (createdAt == null) {
            this.createdAt = LocalDateTime.now();
        } else {
            this.createdAt = createdAt;
        }
    }

    // Getter/Setter cho groupId
    public int getGroupId() {
        return groupId;
    }

    public void setGroupId(int groupId) {
        this.groupId = groupId;
    }

    private boolean isFile = false;

    public boolean isFile() {
        return isFile;
    }

    public void setFile(boolean isFile) {
        this.isFile = isFile;
    }
    
    // Getters/Setters cho delete/recall/edit/reply
    public boolean isDeleted() {
        return isDeleted;
    }
    
    public void setDeleted(boolean deleted) {
        isDeleted = deleted;
    }
    
    public boolean isRecalled() {
        return isRecalled;
    }
    
    public void setRecalled(boolean recalled) {
        isRecalled = recalled;
    }
    
    public boolean isEdited() {
        return isEdited;
    }
    
    public void setEdited(boolean edited) {
        isEdited = edited;
    }
    
    public String getEditedContent() {
        return editedContent;
    }
    
    public void setEditedContent(String editedContent) {
        this.editedContent = editedContent;
    }
    
    public int getRepliedToMessageId() {
        return repliedToMessageId;
    }
    
    public void setRepliedToMessageId(int repliedToMessageId) {
        this.repliedToMessageId = repliedToMessageId;
    }
    
    public String getRepliedToContent() {
        return repliedToContent;
    }
    
    public void setRepliedToContent(String repliedToContent) {
        this.repliedToContent = repliedToContent;
    }
    
    public boolean isPinned() {
        return isPinned;
    }
    
    public void setPinned(boolean pinned) {
        isPinned = pinned;
    }
}