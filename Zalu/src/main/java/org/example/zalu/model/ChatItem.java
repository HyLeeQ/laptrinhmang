package org.example.zalu.model;

public class ChatItem {
    private User user;
    private GroupInfo group;
    private boolean isGroup;

    public ChatItem(User user) {
        this.user = user;
        this.group = null;
        this.isGroup = false;
    }

    public ChatItem(GroupInfo group) {
        this.user = null;
        this.group = group;
        this.isGroup = true;
    }

    public User getUser() {
        return user;
    }

    public GroupInfo getGroup() {
        return group;
    }

    public boolean isGroup() {
        return isGroup;
    }

    public String getDisplayName() {
        return isGroup ? group.getName() : user.getUsername();
    }

    public int getId() {
        return isGroup ? group.getId() : user.getId();
    }
}

