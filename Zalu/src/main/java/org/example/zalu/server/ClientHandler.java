package org.example.zalu.server;

import org.example.zalu.dao.*;
import org.example.zalu.model.Message;
import org.example.zalu.model.User;
import org.example.zalu.model.UserActivity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Handler để xử lý kết nối từ một client
 */
public class ClientHandler extends Thread {
    private static final Logger logger = LoggerFactory.getLogger(ClientHandler.class);
    
    private final Socket socket;
    private final UserDAO userDAO;
    private final FriendDAO friendDAO;
    private final MessageDAO messageDAO;
    private final GroupDAO groupDAO;
    private final Map<Integer, ObjectOutputStream> clients;
    private final Map<Integer, String> onlineUsers;
    private final Runnable updateUserListCallback;
    private final ClientBroadcaster broadcaster;
    private final Consumer<UserActivity> activityCallback;
    
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private int userId = -1;
    // Lưu metadata file tạm thời
    private int pendingFileReceiverId = -1;
    private int pendingFileGroupId = -1;
    private String pendingFileName = null;

    public ClientHandler(Socket socket, UserDAO userDAO, FriendDAO friendDAO, 
                        MessageDAO messageDAO, GroupDAO groupDAO,
                        Map<Integer, ObjectOutputStream> clients,
                        Map<Integer, String> onlineUsers,
                        Runnable updateUserListCallback,
                        ClientBroadcaster broadcaster,
                        Consumer<UserActivity> activityCallback) {
        this.socket = socket;
        this.userDAO = userDAO;
        this.friendDAO = friendDAO;
        this.messageDAO = messageDAO;
        this.groupDAO = groupDAO;
        this.clients = clients;
        this.onlineUsers = onlineUsers;
        this.updateUserListCallback = updateUserListCallback;
        this.broadcaster = broadcaster;
        this.activityCallback = activityCallback;
        
        try {
            // Tăng timeout và buffer size cho file lớn
            socket.setSoTimeout(120000); // 120 giây timeout
            socket.setTcpNoDelay(true); // Tắt Nagle algorithm để gửi nhanh hơn
            socket.setSendBufferSize(1024 * 1024 * 2); // 2MB send buffer
            socket.setReceiveBufferSize(1024 * 1024 * 2); // 2MB receive buffer
        } catch (Exception e) {
            logger.error("Error configuring socket", e);
        }
    }

    @Override
    public void run() {
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());

            // Đọc lệnh đầu tiên (LOGIN / REGISTER / RESUME)
            Object obj = in.readObject();
            if (!(obj instanceof String request)) return;

            if (request.startsWith("REGISTER_REQUEST|")) {
                handleRegister(request);
                return; // Sau khi đăng ký xong thì đóng kết nối, client sẽ tự login lại
            }

            boolean authenticated = false;

            if (request.startsWith("RESUME_SESSION|")) {
                authenticated = handleResumeSession(request);
            }

            if (!authenticated && request.startsWith("LOGIN_REQUEST|")) {
                authenticated = handleLogin(request);
            }

            if (!authenticated) {
                out.writeObject("ERROR|AUTH_REQUIRED");
                out.flush();
                socket.close();
                return;
            }

            // Vòng lặp xử lý tin nhắn sau khi login
            while (true) {
                try {
                    logger.debug("Server: Đang chờ đọc object từ user {}", userId);
                    obj = in.readObject();
                    logger.debug("Server: Đã đọc object từ user {}, type: {}", userId, (obj != null ? obj.getClass().getName() : "null"));
                    
                    if (obj instanceof String msg) {
                        logger.debug("Server: Nhận String message: {}", msg);
                        handleMessage(msg);
                    } else if (obj instanceof byte[]) {
                        byte[] fileData = (byte[]) obj;
                        logger.info("Server: Nhận được file data từ user {}, size: {} bytes", userId, fileData.length);
                        handleFileData(fileData);
                        logger.debug("Server: Đã xử lý xong file data từ user {}", userId);
                    } else {
                        logger.warn("Server: Nhận object không xác định từ user {}: {}", userId, obj.getClass().getName());
                    }
                } catch (EOFException e) {
                    logger.info("Server: Client {} đã đóng kết nối (EOF)", userId);
                    break;
                } catch (SocketException e) {
                    logger.info("Server: Client {} đã đóng kết nối (Socket): {}", userId, e.getMessage());
                    break;
                } catch (java.net.SocketTimeoutException e) {
                    logger.debug("Server: Timeout khi đọc từ client {}: {}", userId, e.getMessage());
                    // Không break, tiếp tục chờ
                } catch (Exception e) {
                    logger.error("Server: Lỗi xử lý từ client {}: {}", userId, e.getMessage(), e);
                    // Không break ngay, tiếp tục xử lý các request khác
                    // Chỉ break nếu là lỗi kết nối nghiêm trọng
                    if (e instanceof IOException && !(e instanceof java.net.SocketTimeoutException)) {
                        logger.error("Server: Lỗi IO nghiêm trọng, đóng kết nối với client {}", userId);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Server: Lỗi nghiêm trọng trong ClientHandler", e);
        } finally {
            cleanup();
        }
    }

    private void handleRegister(String msg) throws IOException {
        String[] parts = msg.split("\\|");
        if (parts.length < 6) {
            out.writeObject("REGISTER_RESPONSE|FAIL|Thiếu dữ liệu. Vui lòng điền đầy đủ thông tin.");
            out.flush();
            return;
        }

        String username = parts[1].trim();
        String fullName = parts[2].trim();
        String password = parts[3].trim();
        String email = parts[4].trim();
        String phone = parts[5].trim();

        try {
            User newUser = new User(-1, username, fullName, password, email, phone,
                    "/images/default-avatar.jpg", "", null, "offline", "other");
            boolean ok = userDAO.register(newUser);
            if (ok) {
                out.writeObject("REGISTER_RESPONSE|SUCCESS|Đăng ký thành công. Hãy đăng nhập để tiếp tục.");
            } else {
                out.writeObject("REGISTER_RESPONSE|FAIL|Không thể tạo tài khoản. Thử lại sau.");
            }
        } catch (org.example.zalu.exception.auth.RegistrationFailedException e) {
            out.writeObject("REGISTER_RESPONSE|FAIL|" + e.getMessage());
        } catch (org.example.zalu.exception.database.DatabaseException | org.example.zalu.exception.database.DatabaseConnectionException e) {
            logger.error("Lỗi database khi đăng ký", e);
            out.writeObject("REGISTER_RESPONSE|FAIL|Lỗi server: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Lỗi không xác định khi đăng ký", e);
            out.writeObject("REGISTER_RESPONSE|FAIL|Lỗi server: " + e.getMessage());
        } finally {
            out.flush();
            try {
                socket.close();
            } catch (IOException ignored) {}
        }
    }

    private boolean handleLogin(String request) throws Exception {
        logger.info("Server: Xử lý LOGIN_REQUEST: {}", request);
        String[] p = request.split("\\|");
        if (p.length < 3) {
            logger.warn("Server: LOGIN_REQUEST thiếu thông tin");
            out.writeObject("LOGIN_RESPONSE|FAIL|Thiếu thông tin đăng nhập");
            out.flush();
            return false;
        }

        logger.debug("Server: Đang kiểm tra login cho username: {}", p[1]);
        User user = userDAO.login(p[1], p[2]);
        if (user == null) {
            logger.warn("Server: Login thất bại - sai tài khoản hoặc mật khẩu cho username: {}", p[1]);
            out.writeObject("LOGIN_RESPONSE|FAIL|Sai tài khoản hoặc mật khẩu");
            out.flush();
            socket.close();
            return false;
        }

        userId = user.getId();
        clients.put(userId, out);
        onlineUsers.put(userId, user.getUsername());
        if (updateUserListCallback != null) {
            updateUserListCallback.run();
        }
        
        // Ghi nhận hoạt động đăng nhập
        if (activityCallback != null) {
            UserActivity loginActivity = new UserActivity(
                userId, user.getUsername(), "LOGIN", LocalDateTime.now()
            );
            activityCallback.accept(loginActivity);
        }

        logger.info("Server: Login thành công, gửi LOGIN_RESPONSE cho user {}", userId);
        out.writeObject("LOGIN_RESPONSE|SUCCESS|" + userId);
        out.flush();

        // 1. Gửi danh sách bạn bè
        List<Integer> friendIds = friendDAO.getFriendsByUserId(userId);
        out.writeObject(friendIds);
        out.flush();
        logger.debug("Đã gửi danh sách {} bạn bè cho user {}", friendIds.size(), userId);

        // 2. Gửi toàn bộ tin nhắn cũ (từ DB)
        List<Message> allMessages = messageDAO.getAllMessagesForUser(userId);
        out.writeObject(allMessages);
        out.flush();
        logger.debug("Đã gửi {} tin nhắn cũ cho user {}", allMessages.size(), userId);

        // 3. Gửi danh sách lời mời kết bạn đang chờ (nếu có)
        List<Integer> pending = friendDAO.getPendingFriendRequests(userId);
        if (!pending.isEmpty()) {
            out.writeObject(pending);
            out.flush();
            logger.debug("Đã gửi {} lời mời kết bạn đang chờ", pending.size());
        }

        // 4. Gửi danh sách nhóm của user
        List<org.example.zalu.model.GroupInfo> userGroups = groupDAO.getUserGroups(userId);
        out.writeObject(userGroups);
        out.flush();
        logger.debug("Đã gửi {} nhóm cho user {}", userGroups.size(), userId);

        // 5. Gửi danh sách online users
        List<Integer> onlineUserIds = new ArrayList<>(onlineUsers.keySet());
        out.writeObject(onlineUserIds);
        out.flush();
        logger.debug("Đã gửi danh sách {} online users cho user {}", onlineUserIds.size(), userId);

        // 6. Broadcast USER_ONLINE cho tất cả friends
        try {
            for (int friendId : friendIds) {
                broadcaster.broadcastToUser(friendId, "USER_ONLINE|" + userId);
            }
            logger.debug("Đã broadcast USER_ONLINE cho {} friends của user {}", friendIds.size(), userId);
        } catch (Exception e) {
            logger.error("Lỗi khi broadcast USER_ONLINE", e);
        }

        logger.info("✓ {} (ID: {}) đăng nhập thành công", user.getUsername(), userId);
        return true;
    }

    private boolean handleResumeSession(String request) throws IOException {
        String[] parts = request.split("\\|");
        if (parts.length < 2) {
            out.writeObject("RESUME_SESSION|FAIL|INVALID_FORMAT");
            out.flush();
            return false;
        }
        int resumeId;
        try {
            resumeId = Integer.parseInt(parts[1]);
        } catch (NumberFormatException ex) {
            out.writeObject("RESUME_SESSION|FAIL|INVALID_USER_ID");
            out.flush();
            return false;
        }

        try {
            User user = userDAO.getUserById(resumeId);
            userId = resumeId;
            clients.put(userId, out);
            onlineUsers.put(userId, user.getUsername());
            if (updateUserListCallback != null) {
                updateUserListCallback.run();
            }
            
            // Ghi nhận hoạt động đăng nhập (resume session)
            if (activityCallback != null) {
                UserActivity loginActivity = new UserActivity(
                    userId, user.getUsername(), "LOGIN", LocalDateTime.now()
                );
                activityCallback.accept(loginActivity);
            }
            
            out.writeObject("RESUME_SESSION|OK");
            out.flush();

            // Nhắc client tự refresh lại danh sách
            broadcaster.broadcastToUser(userId, "FRIENDS_UPDATE");
            broadcaster.broadcastToUser(userId, "GROUPS_UPDATE");
            logger.info("Khôi phục session thành công cho userId {}", userId);
            return true;
        } catch (org.example.zalu.exception.auth.UserNotFoundException e) {
            out.writeObject("RESUME_SESSION|FAIL|USER_NOT_FOUND");
            out.flush();
            return false;
        } catch (org.example.zalu.exception.database.DatabaseException | org.example.zalu.exception.database.DatabaseConnectionException e) {
            out.writeObject("RESUME_SESSION|FAIL|" + e.getMessage());
            out.flush();
            return false;
        }
    }

    private void handleMessage(String msg) throws Exception {
        if (msg.startsWith("SEND_MESSAGE|")) {
            // Format: SEND_MESSAGE|receiverId|senderId|content or SEND_MESSAGE|receiverId|senderId|content|REPLY_TO|repliedToMessageId|repliedToContent
            String[] p = msg.split("\\|");
            int receiverId = Integer.parseInt(p[1]);
            String content = p[3];
            Message m = new Message(0, userId, receiverId, content, false, LocalDateTime.now());
            m.setIsRead(false);
            m.setGroupId(0);
            
            // Check if this is a reply
            if (p.length >= 7 && "REPLY_TO".equals(p[4])) {
                try {
                    int repliedToMessageId = Integer.parseInt(p[5]);
                    String repliedToContent = p[6];
                    m.setRepliedToMessageId(repliedToMessageId);
                    m.setRepliedToContent(repliedToContent != null && !repliedToContent.isEmpty() 
                        ? repliedToContent : "Tin nhắn");
                } catch (NumberFormatException e) {
                    logger.warn("Invalid repliedToMessageId in SEND_MESSAGE: {}", e.getMessage());
                }
            }
            
            if (messageDAO.saveMessage(m)) {
                broadcaster.broadcastMessage(m, receiverId);
                out.writeObject("MESSAGE_SENT|OK|" + m.getId());
                
                // Ghi nhận hoạt động gửi tin nhắn
                if (activityCallback != null) {
                    String encryptedContent = UserActivity.encryptContent(content);
                    UserActivity messageActivity = new UserActivity(
                        userId, onlineUsers.get(userId), "MESSAGE", 
                        receiverId, encryptedContent, LocalDateTime.now()
                    );
                    activityCallback.accept(messageActivity);
                }
            }
        }
        else if (msg.startsWith("SEND_FILE|") || msg.startsWith("SEND_VOICE|")) {
            // Format: SEND_FILE|senderId|receiverId|fileName|fileSize
            String[] p = msg.split("\\|");
            if (p.length >= 5) {
                int senderId = Integer.parseInt(p[1]);
                pendingFileReceiverId = Integer.parseInt(p[2]);
                pendingFileName = p[3];
                long fileSize = Long.parseLong(p[4]);
                pendingFileGroupId = -1; // Reset group
                logger.info("Server: Nhận SEND_FILE từ user {} - fileName: {}, receiverId: {}, fileSize: {} bytes", 
                           userId, pendingFileName, pendingFileReceiverId, fileSize);
                out.writeObject("READY_FOR_FILE");
                out.flush();
                logger.debug("Server: Đã gửi READY_FOR_FILE, chờ nhận file data...");
            }
        }
        else if (msg.startsWith("SEND_GROUP_FILE|") || msg.startsWith("SEND_GROUP_VOICE|")) {
            // Format: SEND_GROUP_FILE|groupId|senderId|fileName|fileSize
            String[] p = msg.split("\\|");
            if (p.length >= 5) {
                pendingFileGroupId = Integer.parseInt(p[1]);
                int senderId = Integer.parseInt(p[2]);
                pendingFileName = p[3];
                pendingFileReceiverId = -1; // Reset receiver
                out.writeObject("READY_FOR_FILE");
            }
        }
        else if (msg.startsWith("SEND_FRIEND_REQUEST|")) {
            String[] p = msg.split("\\|");
            int senderId = Integer.parseInt(p[1]);
            int receiverId = Integer.parseInt(p[2]);

            boolean success = friendDAO.sendFriendRequest(senderId, receiverId);
            out.writeObject(success ? "FRIEND_REQUEST_SENT|OK" : "FRIEND_REQUEST_SENT|FAIL");

            broadcaster.broadcastToUser(receiverId, "FRIEND_REQUEST_RECEIVED|" + senderId);
        }
        else if (msg.startsWith("ACCEPT_FRIEND|")) {
            String[] p = msg.split("\\|");
            int receiverId = Integer.parseInt(p[1]);
            int senderId = Integer.parseInt(p[2]);

            boolean success = friendDAO.acceptFriendRequest(receiverId, senderId);
            out.writeObject(success ? "ACCEPT_FRIEND_OK" : "ACCEPT_FRIEND_FAIL");

            if (success) {
                logger.info("Server: Accept thành công từ {} đến {}", senderId, receiverId);
                broadcaster.broadcastToUser(receiverId, "FRIENDS_UPDATE");
                broadcaster.broadcastToUser(senderId, "FRIENDS_UPDATE");
            }
        }
        else if (msg.startsWith("REJECT_FRIEND|")) {
            String[] p = msg.split("\\|");
            int receiverId = Integer.parseInt(p[1]);
            int senderId = Integer.parseInt(p[2]);

            boolean success = friendDAO.rejectFriendRequest(receiverId, senderId);
            if (success) {
                broadcaster.broadcastToUser(receiverId, "FRIENDS_UPDATE");
                broadcaster.broadcastToUser(senderId, "FRIENDS_UPDATE");
            }
        }
        else if (msg.equals("LOGOUT")) {
            logger.info("User {} đăng xuất chủ động", userId);
            int logoutUserId = userId;
            String username = onlineUsers.get(userId);
            
            // Ghi nhận hoạt động đăng xuất
            if (activityCallback != null && username != null) {
                UserActivity logoutActivity = new UserActivity(
                    logoutUserId, username, "LOGOUT", LocalDateTime.now()
                );
                activityCallback.accept(logoutActivity);
            }
            
            try {
                out.writeObject("LOGOUT_OK");
                out.flush();
            } catch (Exception ignored) {}
            // Broadcast USER_OFFLINE trước khi cleanup
            try {
                List<Integer> friendIds = friendDAO.getFriendsByUserId(logoutUserId);
                for (int friendId : friendIds) {
                    broadcaster.broadcastToUser(friendId, "USER_OFFLINE|" + logoutUserId);
                }
                logger.debug("Đã broadcast USER_OFFLINE cho {} friends của user {}", friendIds.size(), logoutUserId);
            } catch (Exception e) {
                logger.error("Lỗi khi broadcast USER_OFFLINE", e);
            }
            return;
        }
        else if (msg.startsWith("SEND_GROUP_MESSAGE|")) {
            // Format: SEND_GROUP_MESSAGE|groupId|content or SEND_GROUP_MESSAGE|groupId|content|REPLY_TO|repliedToMessageId|repliedToContent
            String[] p = msg.split("\\|");
            int groupId = Integer.parseInt(p[1]);
            String content = p[2];
            
            Message m = new Message(0, userId, 0, content, false, LocalDateTime.now(), groupId);
            m.setIsRead(false);
            
            // Check if this is a reply
            if (p.length >= 6 && "REPLY_TO".equals(p[3])) {
                try {
                    int repliedToMessageId = Integer.parseInt(p[4]);
                    String repliedToContent = p[5];
                    m.setRepliedToMessageId(repliedToMessageId);
                    m.setRepliedToContent(repliedToContent != null && !repliedToContent.isEmpty() 
                        ? repliedToContent : "Tin nhắn");
                } catch (NumberFormatException e) {
                    logger.warn("Invalid repliedToMessageId in SEND_GROUP_MESSAGE: {}", e.getMessage());
                }
            }
            
            if (messageDAO.saveMessage(m)) {
                broadcaster.broadcastGroupMessage(m, groupId, groupDAO);
                out.writeObject("GROUP_MESSAGE_SENT|OK|" + m.getId());
                
                // Ghi nhận hoạt động gửi tin nhắn nhóm
                if (activityCallback != null) {
                    String encryptedContent = UserActivity.encryptContent(content);
                    UserActivity groupMessageActivity = new UserActivity(
                        userId, onlineUsers.get(userId), "GROUP_MESSAGE", 
                        groupId, encryptedContent, LocalDateTime.now(), true
                    );
                    activityCallback.accept(groupMessageActivity);
                }
            } else {
                out.writeObject("GROUP_MESSAGE_SENT|FAIL");
            }
        }
        else if (msg.startsWith("MARK_AS_READ|")) {
            String[] p = msg.split("\\|");
            if (p.length >= 3) {
                try {
                    if (p.length == 4 && "GROUP".equals(p[3])) {
                        int userId = Integer.parseInt(p[1]);
                        int groupId = Integer.parseInt(p[2]);
                        boolean success = messageDAO.markGroupMessagesAsRead(userId, groupId);
                        if (success) {
                            out.writeObject("MARK_AS_READ|OK");
                        }
                    } else {
                        int receiverId = Integer.parseInt(p[1]);
                        int senderId = Integer.parseInt(p[2]);
                        boolean success = messageDAO.markMessagesAsRead(receiverId, senderId);
                        if (success) {
                            out.writeObject("MARK_AS_READ|OK");
                            broadcaster.broadcastToUser(senderId, "MESSAGES_READ|" + receiverId);
                        }
                    }
                } catch (NumberFormatException e) {
                    out.writeObject("MARK_AS_READ|FAIL|" + e.getMessage());
                } catch (org.example.zalu.exception.database.DatabaseException | 
                         org.example.zalu.exception.database.DatabaseConnectionException e) {
                    out.writeObject("MARK_AS_READ|FAIL|" + e.getMessage());
                }
            }
        }
        else if (msg.startsWith("DELETE_MESSAGE|")) {
            // Format: DELETE_MESSAGE|messageId|userId
            String[] p = msg.split("\\|");
            if (p.length >= 3) {
                try {
                    int messageId = Integer.parseInt(p[1]);
                    int requestUserId = Integer.parseInt(p[2]);
                    Message message = messageDAO.getMessageById(messageId);
                    if (message != null && message.getSenderId() == requestUserId) {
                        boolean success = messageDAO.deleteMessage(messageId, requestUserId);
                        if (success) {
                            out.writeObject("DELETE_MESSAGE|OK|" + messageId);
                            // Broadcast to receiver
                            if (message.getGroupId() > 0) {
                                broadcaster.broadcastToGroup(message.getGroupId(), "MESSAGE_DELETED|" + messageId, groupDAO);
                            } else {
                                broadcaster.broadcastToUser(message.getReceiverId(), "MESSAGE_DELETED|" + messageId);
                            }
                        } else {
                            out.writeObject("DELETE_MESSAGE|FAIL");
                        }
                    } else {
                        out.writeObject("DELETE_MESSAGE|FAIL|PERMISSION_DENIED");
                    }
                } catch (Exception e) {
                    out.writeObject("DELETE_MESSAGE|FAIL|" + e.getMessage());
                }
            }
        }
        else if (msg.startsWith("RECALL_MESSAGE|")) {
            // Format: RECALL_MESSAGE|messageId|userId
            String[] p = msg.split("\\|");
            if (p.length >= 3) {
                try {
                    int messageId = Integer.parseInt(p[1]);
                    int requestUserId = Integer.parseInt(p[2]);
                    Message message = messageDAO.getMessageById(messageId);
                    if (message != null && message.getSenderId() == requestUserId) {
                        boolean success = messageDAO.recallMessage(messageId, requestUserId);
                        if (success) {
                            out.writeObject("RECALL_MESSAGE|OK|" + messageId);
                            // Broadcast to receiver(s)
                            if (message.getGroupId() > 0) {
                                broadcaster.broadcastToGroup(message.getGroupId(), "MESSAGE_RECALLED|" + messageId, groupDAO);
                            } else {
                                broadcaster.broadcastToUser(message.getReceiverId(), "MESSAGE_RECALLED|" + messageId);
                                // Also broadcast to sender
                                broadcaster.broadcastToUser(requestUserId, "MESSAGE_RECALLED|" + messageId);
                            }
                        } else {
                            out.writeObject("RECALL_MESSAGE|FAIL");
                        }
                    } else {
                        out.writeObject("RECALL_MESSAGE|FAIL|PERMISSION_DENIED");
                    }
                } catch (Exception e) {
                    out.writeObject("RECALL_MESSAGE|FAIL|" + e.getMessage());
                }
            }
        }
        else if (msg.startsWith("EDIT_MESSAGE|")) {
            // Format: EDIT_MESSAGE|messageId|userId|newContent
            String[] p = msg.split("\\|", 4);
            if (p.length >= 4) {
                try {
                    int messageId = Integer.parseInt(p[1]);
                    int requestUserId = Integer.parseInt(p[2]);
                    String newContent = p[3];
                    Message message = messageDAO.getMessageById(messageId);
                    if (message != null && message.getSenderId() == requestUserId && !message.isRecalled()) {
                        boolean success = messageDAO.editMessage(messageId, requestUserId, newContent);
                        if (success) {
                            out.writeObject("EDIT_MESSAGE|OK|" + messageId);
                            // Broadcast to receiver(s)
                            Message updatedMessage = messageDAO.getMessageById(messageId);
                            if (updatedMessage != null) {
                                if (updatedMessage.getGroupId() > 0) {
                                    broadcaster.broadcastToGroup(updatedMessage.getGroupId(), 
                                        "MESSAGE_EDITED|" + messageId + "|" + newContent, groupDAO);
                                } else {
                                    broadcaster.broadcastToUser(updatedMessage.getReceiverId(), 
                                        "MESSAGE_EDITED|" + messageId + "|" + newContent);
                                    // Also broadcast to sender
                                    broadcaster.broadcastToUser(requestUserId, 
                                        "MESSAGE_EDITED|" + messageId + "|" + newContent);
                                }
                            }
                        } else {
                            out.writeObject("EDIT_MESSAGE|FAIL");
                        }
                    } else {
                        out.writeObject("EDIT_MESSAGE|FAIL|PERMISSION_DENIED");
                    }
                } catch (Exception e) {
                    out.writeObject("EDIT_MESSAGE|FAIL|" + e.getMessage());
                }
            }
        }
        else if (msg.startsWith("CREATE_GROUP|")) {
            String[] p = msg.split("\\|");
            String groupName = p[1];
            List<Integer> memberIds = new ArrayList<>();
            for (int i = 2; i < p.length; i++) {
                memberIds.add(Integer.parseInt(p[i]));
            }
            
            try {
                int groupId = groupDAO.createGroup(groupName, userId, memberIds);
                if (groupId > 0) {
                    out.writeObject("CREATE_GROUP|OK|" + groupId);
                    List<Integer> allMembers = groupDAO.getGroupMembers(groupId);
                    for (int memberId : allMembers) {
                        broadcaster.broadcastToUser(memberId, "GROUPS_UPDATE");
                    }
                } else {
                    out.writeObject("CREATE_GROUP|FAIL");
                }
            } catch (SQLException e) {
                logger.error("Lỗi SQL khi tạo nhóm", e);
                out.writeObject("CREATE_GROUP|FAIL|" + e.getMessage());
            }
        }
        else if (msg.startsWith("GET_FILE|")) {
            // Format: GET_FILE|messageId
            String[] p = msg.split("\\|");
            if (p.length >= 2) {
                try {
                    int messageId = Integer.parseInt(p[1]);
                    Message message = messageDAO.getMessageById(messageId);
                    if (message != null && message.getFileData() != null) {
                        // Kiểm tra quyền: user phải là sender hoặc receiver (hoặc member của group)
                        boolean hasPermission = false;
                        if (message.getGroupId() > 0) {
                            // Group message: kiểm tra user có trong group không
                            List<Integer> members = groupDAO.getGroupMembers(message.getGroupId());
                            hasPermission = members.contains(userId);
                        } else {
                            // 1-1 message: kiểm tra user là sender hoặc receiver
                            hasPermission = (message.getSenderId() == userId || message.getReceiverId() == userId);
                        }
                        
                        if (hasPermission) {
                            // Gửi metadata trước
                            out.writeObject("FILE_DATA|" + messageId + "|" + message.getFileName() + "|" + message.getFileData().length);
                            out.flush();
                            // Gửi file data
                            out.writeObject(message.getFileData());
                            out.flush();
                            logger.info("Server: Đã gửi file cho messageId {} cho user {}", messageId, userId);
                        } else {
                            out.writeObject("FILE_DATA|FAIL|PERMISSION_DENIED");
                            out.flush();
                        }
                    } else {
                        out.writeObject("FILE_DATA|FAIL|FILE_NOT_FOUND");
                        out.flush();
                    }
                } catch (Exception e) {
                    logger.error("Lỗi khi xử lý GET_FILE", e);
                    try {
                        out.writeObject("FILE_DATA|FAIL|" + e.getMessage());
                        out.flush();
                    } catch (IOException ioEx) {
                        logger.error("Server: Không thể gửi error response", ioEx);
                    }
                }
            }
        }
        else if (msg.startsWith("GET_GROUPS|")) {
            try {
                List<org.example.zalu.model.GroupInfo> groups = groupDAO.getUserGroups(userId);
                out.writeObject(groups);
            } catch (SQLException e) {
                logger.error("Lỗi SQL khi lấy danh sách nhóm", e);
                out.writeObject(new ArrayList<>());
            }
        }
        else if (msg.equals("KEEP_ALIVE")) {
            out.writeObject("KEEP_ALIVE_OK");
        }
        else if (msg.startsWith("TYPING|")) {
            // Format: TYPING|senderId|receiverId
            String[] p = msg.split("\\|");
            if (p.length >= 3) {
                int senderId = Integer.parseInt(p[1]);
                int receiverId = Integer.parseInt(p[2]);
                // Broadcast typing signal đến receiver
                broadcaster.broadcastToUser(receiverId, "TYPING_INDICATOR|" + senderId);
            }
        }
        else if (msg.startsWith("TYPING_STOP|")) {
            // Format: TYPING_STOP|senderId|receiverId
            String[] p = msg.split("\\|");
            if (p.length >= 3) {
                int senderId = Integer.parseInt(p[1]);
                int receiverId = Integer.parseInt(p[2]);
                // Broadcast stop typing signal đến receiver
                broadcaster.broadcastToUser(receiverId, "TYPING_STOP|" + senderId);
            }
        }
        out.flush();
    }

    private void handleFileData(byte[] fileData) {
        logger.debug("Server: handleFileData() được gọi - fileData size: {}, pendingFileName: {}, pendingFileReceiverId: {}, pendingFileGroupId: {}", 
                    fileData.length, pendingFileName, pendingFileReceiverId, pendingFileGroupId);
        
        try {
            if (pendingFileName == null) {
                logger.warn("Server: Nhận file data nhưng không có metadata!");
                out.writeObject("FILE_SENT|FAIL|No metadata");
                out.flush();
                return;
            }

            Message m = null;
            if (pendingFileGroupId > 0) {
                m = new Message(0, userId, 0, null, fileData, pendingFileName, false, LocalDateTime.now(), pendingFileGroupId);
                logger.debug("Server: Tạo Message cho group - groupId: {}, fileName: {}", pendingFileGroupId, pendingFileName);
                if (messageDAO.saveMessage(m)) {
                    logger.info("Server: Lưu file group thành công! Message ID: {}", m.getId());
                    broadcaster.broadcastGroupMessage(m, pendingFileGroupId, groupDAO);
                    out.writeObject("GROUP_FILE_SENT|OK|" + m.getId());
                    out.flush();
                    
                    // Ghi nhận hoạt động gửi file nhóm
                    if (activityCallback != null) {
                        UserActivity groupFileActivity = new UserActivity(
                            userId, onlineUsers.get(userId), "GROUP_FILE", 
                            pendingFileGroupId, pendingFileName, LocalDateTime.now(), true
                        );
                        activityCallback.accept(groupFileActivity);
                    }
                } else {
                    logger.error("Server: Lỗi lưu file group vào database!");
                    out.writeObject("GROUP_FILE_SENT|FAIL");
                    out.flush();
                }
            } else if (pendingFileReceiverId > 0) {
                m = new Message(0, userId, pendingFileReceiverId, null, fileData, pendingFileName, false, LocalDateTime.now());
                logger.debug("Server: Tạo Message cho friend - receiverId: {}, fileName: {}", pendingFileReceiverId, pendingFileName);
                if (messageDAO.saveMessage(m)) {
                    logger.info("Server: Lưu file thành công! Message ID: {}", m.getId());
                    broadcaster.broadcastMessage(m, pendingFileReceiverId);
                    out.writeObject("FILE_SENT|OK|" + m.getId());
                    out.flush();
                    
                    // Ghi nhận hoạt động gửi file
                    if (activityCallback != null) {
                        UserActivity fileActivity = new UserActivity(
                            userId, onlineUsers.get(userId), "FILE", 
                            pendingFileReceiverId, pendingFileName, LocalDateTime.now()
                        );
                        activityCallback.accept(fileActivity);
                    }
                } else {
                    logger.error("Server: Lỗi lưu file vào database!");
                    out.writeObject("FILE_SENT|FAIL|Database error");
                    out.flush();
                }
            } else {
                logger.warn("Server: Không có receiver hoặc group để gửi file!");
                out.writeObject("FILE_SENT|FAIL|Invalid receiver");
                out.flush();
            }
        } catch (Exception e) {
            logger.error("Server: Lỗi trong handleFileData: {}", e.getMessage(), e);
            try {
                out.writeObject("FILE_SENT|FAIL|Error: " + e.getMessage());
                out.flush();
            } catch (IOException ioEx) {
                logger.error("Server: Không thể gửi error response", ioEx);
            }
        } finally {
            pendingFileReceiverId = -1;
            pendingFileGroupId = -1;
            pendingFileName = null;
        }
    }

    private void cleanup() {
        if (userId != -1) {
            int disconnectedUserId = userId;
            String username = onlineUsers.get(userId);
            clients.remove(userId);
            onlineUsers.remove(userId);
            if (updateUserListCallback != null) {
                updateUserListCallback.run();
            }
            
            // Ghi nhận hoạt động đăng xuất
            if (activityCallback != null && username != null) {
                UserActivity logoutActivity = new UserActivity(
                    disconnectedUserId, username, "LOGOUT", LocalDateTime.now()
                );
                activityCallback.accept(logoutActivity);
            }
            
            logger.info("User {} đã thoát", userId);
            
            // Broadcast USER_OFFLINE cho tất cả friends
            try {
                List<Integer> friendIds = friendDAO.getFriendsByUserId(disconnectedUserId);
                for (int friendId : friendIds) {
                    broadcaster.broadcastToUser(friendId, "USER_OFFLINE|" + disconnectedUserId);
                }
                logger.debug("Đã broadcast USER_OFFLINE cho {} friends của user {}", friendIds.size(), disconnectedUserId);
            } catch (Exception e) {
                logger.error("Lỗi khi broadcast USER_OFFLINE", e);
            }
        }
        try { socket.close(); } catch (Exception ignored) {}
    }
}

