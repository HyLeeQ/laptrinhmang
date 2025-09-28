package org.example.zalu.model;

public class User {
    private int id;
    private String username;
    private String password;
    private String email;
    private String status;

    public User(int id, String username, String password, String email, String status) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be null or empty");
        }
        if (password == null || password.trim().isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email cannot be null or empty");
        }
        // Gán giá trị trước
        this.id = id;
        this.username = username;
        this.password = password;
        this.email = email;
        this.status = status;
        // Kiểm tra sau khi gán
        if (this.status != null && !this.status.equals("online") && !this.status.equals("offline")) {
            throw new IllegalArgumentException("Status must be 'online' or 'offline'");
        }
    }

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
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        if (password == null || password.trim().isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }
        this.password = password; // Nên hash password trước khi set
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email cannot be null or empty");
        }
        this.email = email;
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
}