package org.example.zalu.model;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class User implements Serializable {
    private static final long serialVersionUID = 1L;
    private int id;
    private String username;
    private String fullName; // Tên thật để hiển thị
    private String password;
    private String email;
    private String phone;
    private String avatarUrl; // Giữ nhưng không dùng binary
    private byte[] avatarData;
    private String bio;
    private LocalDate birthdate;
    private String status;
    private LocalDateTime createdAt;
    private static final String DEFAULT_AVATAR_PATH = "/images/default-avatar.jpg";
    private String gender = "other";

    public User() {
    }

    // Constructor đầy đủ (cho login/register - không avatarData)
    public User(int id, String username, String fullName, String password, String email, String phone,
            String avatarUrl, String bio, LocalDate birthdate, String status, String gender) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be null or empty");
        }
        // SỬA: Skip validation password nếu là dummy (id == -1)
        if (id != -1 && (password == null || password.trim().isEmpty())) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }
        if (id != -1 && (email == null || email.trim().isEmpty())) { // Tương tự cho email nếu cần
            throw new IllegalArgumentException("Email cannot be null or empty");
        }
        this.id = id;
        this.username = username.trim();
        this.fullName = (fullName != null) ? fullName.trim() : "";
        this.password = password != null ? password.trim() : null; // Cho phép null cho dummy
        this.email = email != null ? email.trim() : null;
        this.phone = (phone != null) ? phone.trim() : null;
        this.avatarUrl = (avatarUrl != null) ? avatarUrl.trim() : "/default-avatar.jpg";
        this.bio = (bio != null) ? bio.trim() : null;
        this.birthdate = birthdate;
        setStatus(status);
        setGender(gender);
        this.avatarData = null;
    }

    // Constructor cho display friends (không avatarData)
    public User(int id, String username, String fullName, String email, String phone,
            String avatarUrl, String bio, LocalDate birthdate, String status, String gender) {
        this.id = id;
        this.username = (username != null && !username.trim().isEmpty()) ? username.trim() : "";
        this.fullName = (fullName != null && !fullName.trim().isEmpty()) ? fullName.trim() : username;
        this.password = null;
        this.email = (email != null && !email.trim().isEmpty()) ? email.trim() : null;
        this.phone = (phone != null && !phone.trim().isEmpty()) ? phone.trim() : null;
        this.avatarUrl = (avatarUrl != null && !avatarUrl.trim().isEmpty()) ? avatarUrl.trim() : "/default-avatar.jpg";
        this.bio = (bio != null && !bio.trim().isEmpty()) ? bio.trim() : null;
        this.birthdate = birthdate;
        setStatus(status);
        setGender(gender);
        this.avatarData = null;
    }

    // Constructor cho dummy user
    public User(int id, String username, String status) {
        this(id, username, null, null, null, null, null, null, null, status, "other");
    }

    // Getter/Setter (xóa avatarData)
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be null or empty");
        }
        this.username = username.trim();
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        if (password != null && password.trim().isEmpty()) {
            throw new IllegalArgumentException("Password cannot be empty if provided");
        }
        this.password = (password != null) ? password.trim() : null;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = (email != null && !email.trim().isEmpty()) ? email.trim() : null;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        if (status != null && !status.equals("online") && !status.equals("offline")) {
            throw new IllegalArgumentException("Status must be 'online' or 'offline'");
        }
        this.status = status;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = (fullName != null && !fullName.trim().isEmpty()) ? fullName.trim() : "";
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = (phone != null && !phone.trim().isEmpty()) ? phone.trim() : null;
    }

    public String getAvatarUrl() {
        if (avatarUrl == null || avatarUrl.trim().isEmpty()) {
            return DEFAULT_AVATAR_PATH;
        }
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = (avatarUrl != null && !avatarUrl.trim().isEmpty()) ? avatarUrl.trim() : null;
    }

    public String getAvatarUrlRaw() {
        return avatarUrl;
    }

    public byte[] getAvatarData() {
        return avatarData;
    }

    public void setAvatarData(byte[] avatarData) {
        this.avatarData = avatarData;
    }

    public boolean hasAvatarData() {
        return avatarData != null && avatarData.length > 0;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = (bio != null && !bio.trim().isEmpty()) ? bio.trim() : null;
    }

    public LocalDate getBirthdate() {
        return birthdate;
    }

    public void setBirthdate(LocalDate birthdate) {
        this.birthdate = birthdate;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        if (gender == null || gender.isBlank()) {
            this.gender = "other";
        } else {
            this.gender = gender.toLowerCase();
        }
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "User{id=" + id + ", fullName='" + fullName + "' (username='" + username + "'), email='" + email
                + "', status='" + status + "'}";
    }
}