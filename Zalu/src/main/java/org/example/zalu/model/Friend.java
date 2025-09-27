package org.example.zalu.model;

public class Friend {
    private int user_id;
    private int friend_id;
    private String status;

    public Friend (int user_id, int friend_id, String status){
        this.user_id = user_id;
        this.friend_id = friend_id;
        // Kiểm tra status hợp lệ (tùy chọn)
        if (!status.equals("pending") && !status.equals("accepted") && !status.equals("blocked")) {
            throw new IllegalArgumentException("Status must be 'pending', 'accepted', or 'blocked'");
        }
        this.status = status;
    }

    public int getUserId() {
        return user_id;
    }
    public int getFriendId() {
        return friend_id;
    }
    public String getStatus() {
        return status;
    }
    public void setId(int user_id) {
        this.user_id = user_id;
    }
    public void setUsername(int friend_id) {
        this.friend_id = friend_id;
    }
    public void setPassword(String status) {
        if (!status.equals("pending") && !status.equals("accepted") && !status.equals("blocked")) {
            throw new IllegalArgumentException("Status must be 'pending', 'accepted', or 'blocked'");
        }
        this.status = status;
    }
}
