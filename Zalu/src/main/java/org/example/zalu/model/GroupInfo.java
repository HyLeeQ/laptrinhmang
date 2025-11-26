package org.example.zalu.model;

import java.io.Serial;
import java.io.Serializable;

public class GroupInfo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private int id;
    private String name;
    private int memberCount;
    private byte[] avatarData;
    private String avatarUrl;

    public GroupInfo() {}

    public GroupInfo(int id, String name, int memberCount) {
        this.id = id;
        this.name = name;
        this.memberCount = memberCount;
    }

    public int getId(){ return id; }
    public void setId(int id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getMemberCount () { return memberCount; }
    public void setMemberCount(int memberCount) { this.memberCount = memberCount; }
    public byte[] getAvatarData() { return avatarData; }
    public void setAvatarData(byte[] avatarData) { this.avatarData = avatarData; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
}
