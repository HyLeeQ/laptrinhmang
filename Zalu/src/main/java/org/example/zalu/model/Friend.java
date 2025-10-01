package org.example.zalu.model;

public class Friend {
    private int userId;
    private int friendId;
    private String status;

    public Friend(int userId, int friendId, String status) {
        this.userId = userId;
        this.friendId = friendId;
        if (status == null || (!status.equals("pending") && !status.equals("accepted") && !status.equals("blocked"))) {
            throw new IllegalArgumentException("Status must be 'pending', 'accepted', or 'blocked'");
        }
        this.status = status;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public int getFriendId() {
        return friendId;
    }

    public void setFriendId(int friendId) {
        this.friendId = friendId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        if (status == null || (!status.equals("pending") && !status.equals("accepted") && !status.equals("blocked"))) {
            throw new IllegalArgumentException("Status must be 'pending', 'accepted', or 'blocked'");
        }
        this.status = status;
    }
}