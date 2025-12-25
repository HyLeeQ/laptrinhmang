package org.example.zalu.server;

import org.example.zalu.dao.GroupDAO;
import org.example.zalu.model.Message;

import java.io.ObjectOutputStream;
import java.util.List;
import java.util.Map;

/**
 * Utility class để broadcast messages đến clients
 */
public class ClientBroadcaster {
    private final Map<Integer, ObjectOutputStream> clients;

    public ClientBroadcaster(Map<Integer, ObjectOutputStream> clients) {
        this.clients = clients;
    }

    /**
     * Broadcast message đến một user cụ thể
     */
    public void broadcastMessage(Message msg, int receiverId) {
        ObjectOutputStream target = clients.get(receiverId);
        if (target != null) {
            try {
                if (msg.getFileData() != null) {
                    target.writeObject("NEW_FILE|" + msg.getSenderId() + "|" + receiverId + "|" + msg.getFileName()
                            + "|" + msg.getFileData().length);
                    target.writeObject(msg.getFileData());
                } else {
                    String msgContent = "NEW_MESSAGE|" + msg.getSenderId() + "|" + receiverId + "|" + msg.getContent();
                    if (msg.getRepliedToMessageId() > 0 && msg.getRepliedToContent() != null) {
                        msgContent += "|REPLY_TO|" + msg.getRepliedToMessageId() + "|" + msg.getRepliedToContent();
                    }
                    if (msg.getTempId() != null) {
                        msgContent += "|TEMP_ID|" + msg.getTempId();
                    }
                    target.writeObject(msgContent);
                }
                target.flush();
            } catch (Exception e) {
                System.out.println("Không gửi được cho user " + receiverId);
            }
        }
    }

    /**
     * Broadcast group message đến tất cả thành viên
     */
    public void broadcastGroupMessage(Message msg, int groupId, GroupDAO groupDAO) {
        try {
            List<Integer> members = groupDAO.getGroupMembers(groupId);
            for (int memberId : members) {
                ObjectOutputStream target = clients.get(memberId);
                if (target != null) {
                    try {
                        if (msg.getFileData() != null) {
                            target.writeObject(
                                    "NEW_GROUP_FILE|" + groupId + "|" + msg.getSenderId() + "|" + msg.getFileName());
                            target.writeObject(msg.getFileData());
                        } else {
                            String msgContent = "NEW_GROUP_MESSAGE|" + groupId + "|" + msg.getSenderId() + "|"
                                    + msg.getContent();
                            if (msg.getRepliedToMessageId() > 0 && msg.getRepliedToContent() != null) {
                                msgContent += "|REPLY_TO|" + msg.getRepliedToMessageId() + "|"
                                        + msg.getRepliedToContent();
                            }
                            if (msg.getTempId() != null) {
                                msgContent += "|TEMP_ID|" + msg.getTempId();
                            }
                            target.writeObject(msgContent);
                        }
                        target.flush();
                    } catch (Exception e) {
                        System.out.println("Không gửi được tin nhắn nhóm cho user " + memberId);
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Lỗi khi lấy danh sách thành viên nhóm: " + e.getMessage());
        }
    }

    /**
     * Broadcast message đến một user cụ thể
     */
    public void broadcastToUser(int userId, String message) {
        ObjectOutputStream out = clients.get(userId);
        if (out != null) {
            try {
                out.writeObject(message);
                out.flush();
            } catch (Exception e) {
                System.out.println("Không gửi được " + message + " cho user " + userId);
            }
        }
    }

    /**
     * Broadcast message đến tất cả thành viên trong nhóm
     */
    public void broadcastToGroup(int groupId, String message, GroupDAO groupDAO) {
        try {
            List<Integer> members = groupDAO.getGroupMembers(groupId);
            for (int memberId : members) {
                broadcastToUser(memberId, message);
            }
        } catch (Exception e) {
            System.out.println("Lỗi khi broadcast đến nhóm " + groupId + ": " + e.getMessage());
        }
    }
}
