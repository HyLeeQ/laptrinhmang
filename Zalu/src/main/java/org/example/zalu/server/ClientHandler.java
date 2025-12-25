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

    public Socket getSocket() {
        return socket;
    }

    public int getUserId() {
        return userId;
    }

    @Override
    public void run() {
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());

            // Đọc lệnh đầu tiên (LOGIN / REGISTER / RESUME)
            Object obj = in.readObject();
            if (!(obj instanceof String request))
                return;

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
                    logger.debug("Server: Đã đọc object từ user {}, type: {}", userId,
                            (obj != null ? obj.getClass().getName() : "null"));

                    if (obj instanceof String msg) {
                        logger.debug("Server: Nhận String message: {}", msg);
                        handleMessage(msg);
                    } else if (obj instanceof org.example.zalu.model.ClientErrorLog) {
                        org.example.zalu.model.ClientErrorLog errorLog = (org.example.zalu.model.ClientErrorLog) obj;
                        logger.error("REPORTED CLIENT ERROR from user {}: {}", userId, errorLog);
                        if (ChatServer.getErrorReportingCallback() != null) {
                            ChatServer.getErrorReportingCallback().accept(errorLog);
                        }
                    } else if (obj instanceof org.example.zalu.model.User) {
                        // Handle Profile Update
                        if (userId <= 0) {
                            out.writeObject("UPDATE_PROFILE|FAIL|Unauthorized");
                            out.flush();
                        } else {
                            org.example.zalu.model.User updatedUser = (org.example.zalu.model.User) obj;
                            // Ensure the user is updating their own profile
                            if (updatedUser.getId() == userId) {
                                try {
                                    if (userDAO.updateUser(updatedUser)) {
                                        out.writeObject("UPDATE_PROFILE|SUCCESS");
                                        // Update cache/list
                                        onlineUsers.put(userId, updatedUser.getUsername()); // or FullName if we tracked
                                                                                            // that
                                        if (updateUserListCallback != null)
                                            updateUserListCallback.run();

                                        // Broadcast profile update to all friends so they can reload avatar
                                        try {
                                            List<Integer> friendIds = friendDAO.getFriendsByUserId(userId);
                                            for (int friendId : friendIds) {
                                                broadcaster.broadcastToUser(friendId, "USER_PROFILE_UPDATED|" + userId);
                                            }
                                            logger.debug("Broadcasted profile update to {} friends of user {}",
                                                    friendIds.size(), userId);
                                        } catch (Exception e) {
                                            logger.error("Error broadcasting profile update", e);
                                        }
                                    } else {
                                        out.writeObject("UPDATE_PROFILE|FAIL|Update failed");
                                    }
                                } catch (Exception e) {
                                    logger.error("Error updating user profile", e);
                                    out.writeObject("UPDATE_PROFILE|FAIL|" + e.getMessage());
                                }
                            } else {
                                out.writeObject("UPDATE_PROFILE|FAIL|Invalid user ID");
                            }
                            out.flush();
                        }
                    } else if (obj instanceof byte[]) {
                        byte[] fileData = (byte[]) obj;
                        ChatServer.TOTAL_BYTES_TRANSFERRED.addAndGet(fileData.length);
                        logger.info("Server: Nhận được file data từ user {}, size: {} bytes", userId, fileData.length);
                        handleFileData(fileData);
                        logger.debug("Server: Đã xử lý xong file data từ user {}", userId);
                    } else {
                        logger.warn("Server: Nhận object không xác định từ user {}: {}", userId,
                                obj.getClass().getName());
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
        } catch (org.example.zalu.exception.database.DatabaseException
                | org.example.zalu.exception.database.DatabaseConnectionException e) {
            logger.error("Lỗi database khi đăng ký", e);
            out.writeObject("REGISTER_RESPONSE|FAIL|Lỗi server: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Lỗi không xác định khi đăng ký", e);
            out.writeObject("REGISTER_RESPONSE|FAIL|Lỗi server: " + e.getMessage());
        } finally {
            out.flush();
            try {
                socket.close();
            } catch (IOException ignored) {
            }
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
        // #region agent log
        try {
            String logPath = "d:\\Java\\LTM\\Zalu\\.cursor\\debug.log";
            java.nio.file.Files.write(java.nio.file.Paths.get(logPath), (String.format(
                    "{\"id\":\"log_%d_F\",\"timestamp\":%d,\"location\":\"ClientHandler.java:204\",\"message\":\"Login attempt - before DB call\",\"data\":{\"username\":\"%s\",\"threadName\":\"%s\",\"clientAddress\":\"%s\"},\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"F\"}\n",
                    System.currentTimeMillis(), System.currentTimeMillis(), p[1], Thread.currentThread().getName(),
                    socket.getRemoteSocketAddress())).getBytes(), java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception e) {
        }
        // #endregion
        User user = userDAO.login(p[1], p[2]);
        // #region agent log
        try {
            String logPath = "d:\\Java\\LTM\\Zalu\\.cursor\\debug.log";
            java.nio.file.Files.write(java.nio.file.Paths.get(logPath), (String.format(
                    "{\"id\":\"log_%d_F\",\"timestamp\":%d,\"location\":\"ClientHandler.java:206\",\"message\":\"Login attempt - after DB call\",\"data\":{\"username\":\"%s\",\"success\":%s,\"userId\":%d,\"threadName\":\"%s\"},\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"F\"}\n",
                    System.currentTimeMillis(), System.currentTimeMillis(), p[1], user != null,
                    user != null ? user.getId() : -1, Thread.currentThread().getName())).getBytes(),
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception e) {
        }
        // #endregion
        if (user == null) {
            logger.warn("Server: Login thất bại - sai tài khoản hoặc mật khẩu cho username: {}", p[1]);
            out.writeObject("LOGIN_RESPONSE|FAIL|Sai tài khoản hoặc mật khẩu");
            out.flush();
            // KHÔNG đóng socket ở đây để client có thể thử lại mà không cần kết nối lại
            // socket.close();
            return false;
        }

        userId = user.getId();
        clients.put(userId, out);
        onlineUsers.put(userId, user.getUsername());

        // Cập nhật peak concurrent users
        int currentOnline = onlineUsers.size();
        ChatServer.PEAK_CONCURRENT_USERS.updateAndGet(peak -> Math.max(peak, currentOnline));

        if (updateUserListCallback != null) {
            updateUserListCallback.run();
        }

        // Ghi nhận hoạt động đăng nhập
        if (activityCallback != null) {
            UserActivity loginActivity = new UserActivity(
                    userId, user.getUsername(), "LOGIN", LocalDateTime.now());
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

            // KHÔNG ghi nhận hoạt động LOGIN cho resume session
            // Resume session không phải là login mới, chỉ là khôi phục kết nối

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
        } catch (org.example.zalu.exception.database.DatabaseException
                | org.example.zalu.exception.database.DatabaseConnectionException e) {
            out.writeObject("RESUME_SESSION|FAIL|" + e.getMessage());
            out.flush();
            return false;
        }
    }

    private void handleMessage(String msg) throws Exception {
        // Tăng biến đếm tổng message
        ChatServer.TOTAL_MESSAGES_SENT.incrementAndGet();

        if (msg.startsWith("SEND_MESSAGE|")) {
            // Kiểm tra bị cấm chat không
            if (ChatServer.isUserMuted(userId)) {
                out.writeObject("SYSTEM_ANNOUNCEMENT|Bạn đang bị cấm chat, không thể gửi tin nhắn!");
                out.flush();
                return;
            }
            // Format: SEND_MESSAGE|receiverId|senderId|content or
            // SEND_MESSAGE|receiverId|senderId|content|REPLY_TO|repliedToMessageId|repliedToContent
            String[] p = msg.split("\\|");
            int receiverId = Integer.parseInt(p[1]);
            String content = p[3];
            Message m = new Message(0, userId, receiverId, content, false, LocalDateTime.now());
            m.setIsRead(false);
            m.setGroupId(0);

            // Parsing optional parts (REPLY_TO, TEMP_ID)
            for (int i = 4; i < p.length; i++) {
                if ("REPLY_TO".equals(p[i]) && i + 2 < p.length) {
                    m.setRepliedToMessageId(Integer.parseInt(p[i + 1]));
                    m.setRepliedToContent(p[i + 2]);
                    i += 2;
                } else if ("TEMP_ID".equals(p[i]) && i + 1 < p.length) {
                    m.setTempId(p[i + 1]);
                    i += 1;
                }
            }

            // #region agent log
            try {
                String logPath = "d:\\Java\\LTM\\Zalu\\.cursor\\debug.log";
                java.nio.file.Files.write(java.nio.file.Paths.get(logPath), (String.format(
                        "{\"id\":\"log_%d_F\",\"timestamp\":%d,\"location\":\"ClientHandler.java:353\",\"message\":\"Saving message - before DB call\",\"data\":{\"senderId\":%d,\"receiverId\":%d,\"threadName\":\"%s\"},\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"F\"}\n",
                        System.currentTimeMillis(), System.currentTimeMillis(), userId, receiverId,
                        Thread.currentThread().getName())).getBytes(), java.nio.file.StandardOpenOption.CREATE,
                        java.nio.file.StandardOpenOption.APPEND);
            } catch (Exception e) {
            }
            // #endregion
            if (messageDAO.saveMessage(m)) {
                // #region agent log
                try {
                    String logPath = "d:\\Java\\LTM\\Zalu\\.cursor\\debug.log";
                    java.nio.file.Files.write(java.nio.file.Paths.get(logPath), (String.format(
                            "{\"id\":\"log_%d_F\",\"timestamp\":%d,\"location\":\"ClientHandler.java:355\",\"message\":\"Message saved successfully\",\"data\":{\"messageId\":%d,\"senderId\":%d,\"receiverId\":%d,\"threadName\":\"%s\"},\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"F\"}\n",
                            System.currentTimeMillis(), System.currentTimeMillis(), m.getId(), userId, receiverId,
                            Thread.currentThread().getName())).getBytes(), java.nio.file.StandardOpenOption.CREATE,
                            java.nio.file.StandardOpenOption.APPEND);
                } catch (Exception e) {
                }
                // #endregion
                broadcaster.broadcastMessage(m, receiverId);
                out.writeObject("MESSAGE_SENT|OK|" + m.getId() + (m.getTempId() != null ? "|" + m.getTempId() : ""));

                // Ghi nhận hoạt động gửi tin nhắn
                if (activityCallback != null) {
                    String encryptedContent = UserActivity.encryptContent(content);
                    UserActivity messageActivity = new UserActivity(
                            userId, onlineUsers.get(userId), "MESSAGE",
                            receiverId, encryptedContent, LocalDateTime.now());
                    activityCallback.accept(messageActivity);
                }
            }
        } else if (msg.startsWith("SEND_FILE|") || msg.startsWith("SEND_VOICE|")) {
            // Format: SEND_FILE|senderId|receiverId|fileName|fileSize
            String[] p = msg.split("\\|");
            if (p.length >= 5) {
                // int senderId = Integer.parseInt(p[1]); // Not used
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
        } else if (msg.startsWith("SEND_GROUP_FILE|") || msg.startsWith("SEND_GROUP_VOICE|")) {
            // Format: SEND_GROUP_FILE|groupId|senderId|fileName|fileSize
            String[] p = msg.split("\\|");
            if (p.length >= 5) {
                pendingFileGroupId = Integer.parseInt(p[1]);
                // int senderId = Integer.parseInt(p[2]); // Not used
                pendingFileName = p[3];
                pendingFileReceiverId = -1; // Reset receiver
                out.writeObject("READY_FOR_FILE");
            }
        } else if (msg.startsWith("SEND_FRIEND_REQUEST|")) {
            String[] p = msg.split("\\|");
            int senderId = Integer.parseInt(p[1]);
            int receiverId = Integer.parseInt(p[2]);

            // #region agent log
            try {
                String logPath = "d:\\Java\\LTM\\Zalu\\.cursor\\debug.log";
                java.nio.file.Files.write(java.nio.file.Paths.get(logPath), (String.format(
                        "{\"id\":\"log_%d_G\",\"timestamp\":%d,\"location\":\"ClientHandler.java:419\",\"message\":\"Processing SEND_FRIEND_REQUEST\",\"data\":{\"senderId\":%d,\"receiverId\":%d,\"threadName\":\"%s\"},\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"G\"}\n",
                        System.currentTimeMillis(), System.currentTimeMillis(), senderId, receiverId,
                        Thread.currentThread().getName())).getBytes(), java.nio.file.StandardOpenOption.CREATE,
                        java.nio.file.StandardOpenOption.APPEND);
            } catch (Exception e) {
            }
            // #endregion

            boolean success = friendDAO.sendFriendRequest(senderId, receiverId);

            // #region agent log
            try {
                String logPath = "d:\\Java\\LTM\\Zalu\\.cursor\\debug.log";
                java.nio.file.Files.write(java.nio.file.Paths.get(logPath), (String.format(
                        "{\"id\":\"log_%d_G\",\"timestamp\":%d,\"location\":\"ClientHandler.java:425\",\"message\":\"Friend request processed\",\"data\":{\"success\":%s,\"senderId\":%d,\"receiverId\":%d},\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"G\"}\n",
                        System.currentTimeMillis(), System.currentTimeMillis(), success, senderId, receiverId))
                        .getBytes(), java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            } catch (Exception e) {
            }
            // #endregion

            out.writeObject(success ? "FRIEND_REQUEST_SENT|OK" : "FRIEND_REQUEST_SENT|FAIL");
            out.flush();

            if (success) {
                broadcaster.broadcastToUser(receiverId, "FRIEND_REQUEST_RECEIVED|" + senderId);
            }
        } else if (msg.startsWith("ACCEPT_FRIEND|")) {
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
        } else if (msg.startsWith("REJECT_FRIEND|")) {
            String[] p = msg.split("\\|");
            int receiverId = Integer.parseInt(p[1]);
            int senderId = Integer.parseInt(p[2]);

            boolean success = friendDAO.rejectFriendRequest(receiverId, senderId);
            if (success) {
                broadcaster.broadcastToUser(receiverId, "FRIENDS_UPDATE");
                broadcaster.broadcastToUser(senderId, "FRIENDS_UPDATE");
            }
        } else if (msg.equals("LOGOUT")) {
            logger.info("User {} đăng xuất chủ động", userId);
            int logoutUserId = userId;
            String username = onlineUsers.get(userId);

            // Ghi nhận hoạt động đăng xuất
            if (activityCallback != null && username != null) {
                UserActivity logoutActivity = new UserActivity(
                        logoutUserId, username, "LOGOUT", LocalDateTime.now());
                activityCallback.accept(logoutActivity);
            }

            try {
                out.writeObject("LOGOUT_OK");
                out.flush();
            } catch (Exception ignored) {
            }
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
        } else if (msg.startsWith("SEND_GROUP_MESSAGE|")) {
            // Format: SEND_GROUP_MESSAGE|groupId|content or
            // SEND_GROUP_MESSAGE|groupId|content|REPLY_TO|repliedToMessageId|repliedToContent
            String[] p = msg.split("\\|");
            int groupId = Integer.parseInt(p[1]);
            String content = p[2];

            Message m = new Message(0, userId, 0, content, false, LocalDateTime.now(), groupId);
            m.setIsRead(false);

            // Parsing optional parts
            for (int i = 3; i < p.length; i++) {
                if ("REPLY_TO".equals(p[i]) && i + 2 < p.length) {
                    m.setRepliedToMessageId(Integer.parseInt(p[i + 1]));
                    m.setRepliedToContent(p[i + 2]);
                    i += 2;
                } else if ("TEMP_ID".equals(p[i]) && i + 1 < p.length) {
                    m.setTempId(p[i + 1]);
                    i += 1;
                }
            }

            if (messageDAO.saveMessage(m)) {
                broadcaster.broadcastGroupMessage(m, groupId, groupDAO);
                out.writeObject(
                        "GROUP_MESSAGE_SENT|OK|" + m.getId() + (m.getTempId() != null ? "|" + m.getTempId() : ""));

                // Ghi nhận hoạt động gửi tin nhắn nhóm
                if (activityCallback != null) {
                    String encryptedContent = UserActivity.encryptContent(content);
                    UserActivity groupMessageActivity = new UserActivity(
                            userId, onlineUsers.get(userId), "GROUP_MESSAGE",
                            groupId, encryptedContent, LocalDateTime.now(), true);
                    activityCallback.accept(groupMessageActivity);
                }
            } else {
                out.writeObject("GROUP_MESSAGE_SENT|FAIL");
            }
        } else if (msg.startsWith("MARK_AS_READ|")) {
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
                } catch (org.example.zalu.exception.database.DatabaseException
                        | org.example.zalu.exception.database.DatabaseConnectionException e) {
                    out.writeObject("MARK_AS_READ|FAIL|" + e.getMessage());
                }
            }
        } else if (msg.startsWith("DELETE_MESSAGE|")) {
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
                                broadcaster.broadcastToGroup(message.getGroupId(), "MESSAGE_DELETED|" + messageId,
                                        groupDAO);
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
        } else if (msg.startsWith("RECALL_MESSAGE|")) {
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
                                broadcaster.broadcastToGroup(message.getGroupId(), "MESSAGE_RECALLED|" + messageId,
                                        groupDAO);
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
        } else if (msg.startsWith("EDIT_MESSAGE|")) {
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
        } else if (msg.startsWith("CREATE_GROUP|")) {
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
        } else if (msg.startsWith("GET_FILE|")) {
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
                            out.writeObject("FILE_DATA|" + messageId + "|" + message.getFileName() + "|"
                                    + message.getFileData().length);
                            out.flush();
                            // Gửi file data
                            out.writeObject(message.getFileData());
                            out.flush();
                            logger.info("Server: Đã gửi file cho messageId {} cho user {}", messageId, userId);
                        } else {
                            out.writeObject("FILE_DATA|FAIL|" + messageId + "|PERMISSION_DENIED");
                            out.flush();
                        }
                    } else {
                        out.writeObject("FILE_DATA|FAIL|" + messageId + "|FILE_NOT_FOUND");
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
        } else if (msg.startsWith("GET_GROUPS|")) {
            try {
                List<org.example.zalu.model.GroupInfo> groups = groupDAO.getUserGroups(userId);
                out.writeObject(groups);
            } catch (SQLException e) {
                logger.error("Lỗi SQL khi lấy danh sách nhóm", e);
                out.writeObject(new ArrayList<>());
            }
        } else if (msg.startsWith("GET_FRIENDS|")) {
            // Format: GET_FRIENDS|userId
            try {
                String[] p = msg.split("\\|");
                int requestUserId = p.length >= 2 ? Integer.parseInt(p[1]) : userId;
                List<Integer> friendIds = friendDAO.getFriendsByUserId(requestUserId);
                out.writeObject(friendIds);
                logger.debug("Đã gửi danh sách {} bạn bè cho user {}", friendIds.size(), requestUserId);
            } catch (SQLException e) {
                logger.error("Lỗi SQL khi lấy danh sách bạn bè", e);
                out.writeObject(new ArrayList<>());
            } catch (NumberFormatException e) {
                logger.error("Lỗi parse userId trong GET_FRIENDS", e);
                out.writeObject(new ArrayList<>());
            }
        } else if (msg.startsWith("GET_FRIEND_REQUESTS_INFO|")) {
            // Format: GET_FRIEND_REQUESTS_INFO|userId
            try {
                String[] p = msg.split("\\|");
                int requestUserId = p.length >= 2 ? Integer.parseInt(p[1]) : userId;

                // Reuse existing DAO logic logic via new helper in ClientHandler or direct
                // mapping
                // Since FriendDAO methods getPendingRequestsWithUserInfo are available but need
                // UserDAO
                // We use FriendDAO to get ID lists and mapped them manually or better yet use
                // FriendDAO methods if available
                // FriendDAO currently has getPendingRequests(int) -> List<Integer> and
                // getPendingRequestsWithUserInfo(int, UserDAO)

                List<User> incoming = friendDAO.getPendingRequestsWithUserInfo(requestUserId, userDAO);
                List<User> outgoing = friendDAO.getOutgoingRequestsWithUserInfo(requestUserId, userDAO);

                java.util.Map<String, List<User>> responseMap = new java.util.HashMap<>();
                responseMap.put("incoming", incoming);
                responseMap.put("outgoing", outgoing);
                responseMap.put("dataType", new ArrayList<>()); // Dummy to identify as map in client if needed, or key
                                                                // check
                // Actually client checks keys "incoming" and "outgoing"

                out.writeObject(responseMap);
                out.flush();
                logger.debug("Đã gửi friend requests info cho user {}", requestUserId);
            } catch (Exception e) {
                logger.error("Lỗi lấy friend requests info", e);
                // Send empty map to avoid client hanging
                java.util.Map<String, List<User>> emptyMap = new java.util.HashMap<>();
                emptyMap.put("incoming", new ArrayList<>());
                emptyMap.put("outgoing", new ArrayList<>());
                out.writeObject(emptyMap);
            }
        } else if (msg.startsWith("GET_FRIENDS_LIST_FULL|")) {
            // Format: GET_FRIENDS_LIST_FULL|userId
            try {
                String[] p = msg.split("\\|");
                int requestUserId = p.length >= 2 ? Integer.parseInt(p[1]) : userId;

                List<Integer> friendIds = friendDAO.getFriendsByUserId(requestUserId);
                List<User> friends = userDAO.getUsersByIds(friendIds);

                java.util.Map<String, Object> responseMap = new java.util.HashMap<>();
                responseMap.put("type", "FRIENDS_LIST_FULL");
                responseMap.put("data", friends);

                out.writeObject(responseMap);
                out.flush();
                logger.debug("Sent friends list full for user {}", requestUserId);

            } catch (Exception e) {
                logger.error("Error sending friends list full", e);
                try {
                    out.writeObject(new java.util.HashMap<>());
                } catch (IOException ignored) {
                }
            }
        } else if (msg.startsWith("GET_CONVERSATION|")) {
            // Format: GET_CONVERSATION|userId|friendId
            try {
                String[] p = msg.split("\\|");
                if (p.length >= 3) {
                    int rUserId = Integer.parseInt(p[1]);
                    int friendId = Integer.parseInt(p[2]);
                    List<Message> messages = messageDAO.getMessagesBetween(rUserId, friendId);

                    java.util.Map<String, Object> response = new java.util.HashMap<>();
                    response.put("type", "CONVERSATION_HISTORY");
                    response.put("data", messages);
                    response.put("friendId", friendId); // Echo back to identify context

                    out.writeObject(response);
                    out.flush();
                    logger.debug("Sent conversation history ({} messages) between {} and {}", messages.size(), rUserId,
                            friendId);
                }
            } catch (Exception e) {
                logger.error("Error handling GET_CONVERSATION", e);
                try {
                    java.util.Map<String, Object> errorResponse = new java.util.HashMap<>();
                    errorResponse.put("type", "CONVERSATION_HISTORY_FAIL");
                    errorResponse.put("error", e.getMessage());
                    out.writeObject(errorResponse);
                    out.flush();
                } catch (IOException ioEx) {
                    logger.error("Server: Không thể gửi error response cho GET_CONVERSATION", ioEx);
                }
            }
        } else if (msg.startsWith("GET_GROUP_CONVERSATION|")) {
            // Format: GET_GROUP_CONVERSATION|userId|groupId
            try {
                String[] p = msg.split("\\|");
                if (p.length >= 3) {
                    int groupId = Integer.parseInt(p[2]);
                    List<Message> messages = messageDAO.getMessagesForGroup(groupId);

                    java.util.Map<String, Object> response = new java.util.HashMap<>();
                    response.put("type", "CONVERSATION_HISTORY");
                    response.put("data", messages);
                    response.put("groupId", groupId); // Echo back

                    out.writeObject(response);
                    out.flush();
                    logger.debug("Sent group conversation history ({} messages) for group {}", messages.size(),
                            groupId);
                }
            } catch (Exception e) {
                logger.error("Error handling GET_GROUP_CONVERSATION", e);
                try {
                    java.util.Map<String, Object> errorResponse = new java.util.HashMap<>();
                    errorResponse.put("type", "CONVERSATION_HISTORY_FAIL");
                    errorResponse.put("error", e.getMessage());
                    out.writeObject(errorResponse);
                    out.flush();
                } catch (IOException ioEx) {
                    logger.error("Server: Không thể gửi error response cho GET_GROUP_CONVERSATION", ioEx);
                }
            }
        } else if (msg.startsWith("GET_GROUP_INFO|")) {
            try {
                int groupId = Integer.parseInt(msg.split("\\|")[1]);
                org.example.zalu.model.GroupInfo group = groupDAO.getGroupById(groupId);
                if (group != null) {
                    out.writeObject(group);
                } else {
                    out.writeObject(null);
                }
            } catch (SQLException e) {
                logger.error("Error getting group info", e);
                out.writeObject(null);
            }
        } else if (msg.startsWith("GET_FRIENDS_NOT_IN_GROUP|")) {
            try {
                String[] p = msg.split("\\|");
                int groupId = Integer.parseInt(p[1]);
                int requestUserId = Integer.parseInt(p[2]);
                List<Integer> friendIds = friendDAO.getFriendsByUserId(requestUserId);
                List<Integer> memberIds = groupDAO.getGroupMembers(groupId);
                List<User> result = new ArrayList<>();
                for (int friendId : friendIds) {
                    if (!memberIds.contains(friendId)) {
                        User u = userDAO.getUserById(friendId);
                        if (u != null)
                            result.add(u);
                    }
                }
                java.util.Map<String, Object> response = new java.util.HashMap<>();
                response.put("type", "FRIENDS_NOT_IN_GROUP");
                response.put("data", result);
                out.writeObject(response);
            } catch (Exception e) {
                logger.error("Error getting friends not in group", e);
                out.writeObject(new ArrayList<User>());
            }
        } else if (msg.startsWith("ADD_GROUP_MEMBER|")) {
            String[] p = msg.split("\\|");
            int groupId = Integer.parseInt(p[1]);
            int memberId = Integer.parseInt(p[2]);
            try {
                boolean success = groupDAO.addMemberToGroup(groupId, memberId);
                if (success) {
                    out.writeObject("ADD_GROUP_MEMBER|SUCCESS");
                    // Notify new member
                    broadcaster.broadcastToUser(memberId, "GROUPS_UPDATE");
                    // Notify existing members
                    List<Integer> members = groupDAO.getGroupMembers(groupId);
                    for (int mId : members) {
                        broadcaster.broadcastToUser(mId, "GROUP_MEMBERS_UPDATE|" + groupId);
                    }
                } else {
                    out.writeObject("ADD_GROUP_MEMBER|FAIL");
                }
            } catch (SQLException e) {
                out.writeObject("ADD_GROUP_MEMBER|FAIL|" + e.getMessage());
            }
        } else if (msg.startsWith("GET_MEMBER_ROLE|")) {
            String[] p = msg.split("\\|");
            int groupId = Integer.parseInt(p[1]);
            int memberId = Integer.parseInt(p[2]);
            try {
                String role = groupDAO.getMemberRole(groupId, memberId);
                out.writeObject("MEMBER_ROLE|" + (role != null ? role : "none"));
            } catch (SQLException e) {
                out.writeObject("MEMBER_ROLE|ERROR");
            }
        } else if (msg.startsWith("LEAVE_GROUP|")) {
            int groupId = Integer.parseInt(msg.split("\\|")[1]);
            try {
                boolean success = groupDAO.leaveGroup(groupId, userId);
                if (success) {
                    out.writeObject("LEAVE_GROUP|SUCCESS");
                    broadcaster.broadcastToUser(userId, "GROUPS_UPDATE");
                    List<Integer> members = groupDAO.getGroupMembers(groupId);
                    for (int m : members)
                        broadcaster.broadcastToUser(m, "GROUP_MEMBERS_UPDATE|" + groupId);
                } else {
                    out.writeObject("LEAVE_GROUP|FAIL");
                }
            } catch (SQLException e) {
                out.writeObject("LEAVE_GROUP|FAIL|" + e.getMessage());
            }
        } else if (msg.startsWith("DELETE_GROUP|")) {
            int groupId = Integer.parseInt(msg.split("\\|")[1]);
            try {
                String role = groupDAO.getMemberRole(groupId, userId);
                if ("admin".equals(role)) {
                    List<Integer> members = groupDAO.getGroupMembers(groupId);
                    boolean success = groupDAO.deleteGroup(groupId);
                    if (success) {
                        out.writeObject("DELETE_GROUP|SUCCESS");
                        for (int m : members)
                            broadcaster.broadcastToUser(m, "GROUPS_UPDATE");
                    } else {
                        out.writeObject("DELETE_GROUP|FAIL");
                    }
                } else {
                    out.writeObject("DELETE_GROUP|FAIL|PERMISSION_DENIED");
                }
            } catch (SQLException e) {
                out.writeObject("DELETE_GROUP|FAIL|" + e.getMessage());
            }
        } else if (msg.startsWith("SEARCH_USERS|"))

        {
            // Format: SEARCH_USERS|query|userId
            try {
                String[] p = msg.split("\\|", 3);
                if (p.length >= 2) {
                    String query = p[1];
                    int requestUserId = p.length >= 3 ? Integer.parseInt(p[2]) : userId;
                    List<org.example.zalu.model.User> users = userDAO.searchUsers(query);
                    // Filter out ONLY current user, but keep friends and pending requests
                    // Client will handle showing appropriate button (Gửi/Hủy/Đã là bạn)
                    List<org.example.zalu.model.User> filteredUsers = new ArrayList<>();
                    for (org.example.zalu.model.User user : users) {
                        if (user.getId() != requestUserId) {
                            filteredUsers.add(user);
                        }
                    }
                    out.writeObject(filteredUsers);
                    logger.debug("Đã gửi {} kết quả tìm kiếm cho query '{}'", filteredUsers.size(), query);
                } else {
                    out.writeObject(new ArrayList<>());
                }
            } catch (org.example.zalu.exception.database.DatabaseException
                    | org.example.zalu.exception.database.DatabaseConnectionException e) {
                logger.error("Lỗi khi tìm kiếm user", e);
                out.writeObject(new ArrayList<>());
            }
        } else if (msg.startsWith("GET_USER_AVATAR|")) {
            // Format: GET_USER_AVATAR|userId
            try {
                int targetUserId = Integer.parseInt(msg.split("\\|")[1]);
                byte[] avatarData = userDAO.getAvatarData(targetUserId);
                if (avatarData != null && avatarData.length > 0) {
                    out.writeObject("USER_AVATAR|" + targetUserId + "|" + avatarData.length);
                    out.flush();
                    out.writeObject(avatarData);
                    out.flush();
                } else {
                    out.writeObject("USER_AVATAR|" + targetUserId + "|0");
                    out.flush();
                }
            } catch (Exception e) {
                logger.error("Error getting avatar for user", e);
                out.writeObject("USER_AVATAR|FAIL");
            }
        } else if (msg.startsWith("GET_PENDING_REQUESTS|")) {
            // Format: GET_PENDING_REQUESTS|userId
            try {
                String[] p = msg.split("\\|");
                int requestUserId = p.length >= 2 ? Integer.parseInt(p[1]) : userId;
                List<Integer> incoming = friendDAO.getPendingFriendRequests(requestUserId);
                List<Integer> outgoing = friendDAO.getOutgoingRequest(requestUserId);
                // Gửi cả hai danh sách
                java.util.Map<String, List<Integer>> result = new java.util.HashMap<>();
                result.put("incoming", incoming);
                result.put("outgoing", outgoing);
                out.writeObject(result);
                logger.debug("Đã gửi {} incoming và {} outgoing requests cho user {}", incoming.size(), outgoing.size(),
                        requestUserId);
            } catch (SQLException e) {
                logger.error("Lỗi SQL khi lấy pending requests", e);
                java.util.Map<String, List<Integer>> emptyResult = new java.util.HashMap<>();
                emptyResult.put("incoming", new ArrayList<>());
                emptyResult.put("outgoing", new ArrayList<>());
                out.writeObject(emptyResult);
            }
        } else if (msg.startsWith("GET_MESSAGES|")) {
            // Format: GET_MESSAGES|userId|friendId
            try {
                String[] p = msg.split("\\|");
                if (p.length >= 3) {
                    int requestUserId = Integer.parseInt(p[1]);
                    int friendId = Integer.parseInt(p[2]);
                    List<Message> messages = messageDAO.getMessagesBetween(requestUserId, friendId);
                    out.writeObject(messages);
                    logger.debug("Đã gửi {} tin nhắn giữa user {} và {}", messages.size(), requestUserId, friendId);
                } else {
                    out.writeObject(new ArrayList<>());
                }
            } catch (org.example.zalu.exception.message.MessageException
                    | org.example.zalu.exception.database.DatabaseException
                    | org.example.zalu.exception.database.DatabaseConnectionException e) {
                logger.error("Lỗi khi lấy tin nhắn", e);
                out.writeObject(new ArrayList<>());
            }
        } else if (msg.startsWith("GET_GROUP_MESSAGES|")) {
            // Format: GET_GROUP_MESSAGES|groupId|userId
            try {
                String[] p = msg.split("\\|");
                if (p.length >= 2) {
                    int groupId = Integer.parseInt(p[1]);
                    List<Message> messages = messageDAO.getMessagesForGroup(groupId);
                    out.writeObject(messages);
                    logger.debug("Đã gửi {} tin nhắn cho nhóm {}", messages.size(), groupId);
                } else {
                    out.writeObject(new ArrayList<>());
                }
            } catch (org.example.zalu.exception.message.MessageException
                    | org.example.zalu.exception.database.DatabaseException
                    | org.example.zalu.exception.database.DatabaseConnectionException e) {
                logger.error("Lỗi khi lấy tin nhắn nhóm", e);
                out.writeObject(new ArrayList<>());
            }
        } else if (msg.startsWith("GET_USER_BY_ID|")) {
            // Format: GET_USER_BY_ID|userId
            try {
                String[] p = msg.split("\\|");
                if (p.length >= 2) {
                    int requestUserId = Integer.parseInt(p[1]);
                    org.example.zalu.model.User user = userDAO.getUserById(requestUserId);
                    out.writeObject(user);
                    logger.debug("Đã gửi thông tin user {}", requestUserId);
                } else {
                    out.writeObject(null);
                }
            } catch (org.example.zalu.exception.auth.UserNotFoundException e) {
                logger.warn("User không tồn tại", e);
                out.writeObject(null);
            } catch (org.example.zalu.exception.database.DatabaseException
                    | org.example.zalu.exception.database.DatabaseConnectionException e) {
                logger.error("Lỗi khi lấy thông tin user", e);
                out.writeObject(null);
            }
        } else if (msg.equals("KEEP_ALIVE")) {
            out.writeObject("KEEP_ALIVE_OK");
        } else if (msg.startsWith("TYPING|")) {
            // Format: TYPING|senderId|receiverId
            String[] p = msg.split("\\|");
            if (p.length >= 3) {
                int senderId = Integer.parseInt(p[1]);
                int receiverId = Integer.parseInt(p[2]);
                // Broadcast typing signal đến receiver
                broadcaster.broadcastToUser(receiverId, "TYPING_INDICATOR|" + senderId);
            }
        } else if (msg.startsWith("TYPING_STOP|")) {
            // Format: TYPING_STOP|senderId|receiverId
            String[] p = msg.split("\\|");
            if (p.length >= 3) {
                int senderId = Integer.parseInt(p[1]);
                int receiverId = Integer.parseInt(p[2]);
                // Broadcast stop typing signal đến receiver
                broadcaster.broadcastToUser(receiverId, "TYPING_STOP|" + senderId);
            }
        } else if (msg.startsWith("SEARCH_MESSAGES|")) {
            handleSearchMessages(msg);
        } else if (msg.startsWith("GET_PINNED_MESSAGES|")) {
            handleGetPinnedMessages(msg);
        } else if (msg.startsWith("PIN_MESSAGE|")) {
            handlePinMessage(msg);
        } else if (msg.startsWith("MARK_AS_READ|")) {
            handleMarkAsRead(msg);
        }
        out.flush();
    }

    private void handleFileData(byte[] fileData) {
        logger.debug(
                "Server: handleFileData() được gọi - fileData size: {}, pendingFileName: {}, pendingFileReceiverId: {}, pendingFileGroupId: {}",
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
                m = new Message(0, userId, 0, null, fileData, pendingFileName, false, LocalDateTime.now(),
                        pendingFileGroupId);
                logger.debug("Server: Tạo Message cho group - groupId: {}, fileName: {}", pendingFileGroupId,
                        pendingFileName);
                if (messageDAO.saveMessage(m)) {
                    logger.info("Server: Lưu file group thành công! Message ID: {}", m.getId());
                    ChatServer.TOTAL_FILES_SENT.incrementAndGet(); // Tăng counter
                    broadcaster.broadcastGroupMessage(m, pendingFileGroupId, groupDAO);
                    out.writeObject("GROUP_FILE_SENT|OK|" + m.getId());
                    out.flush();

                    // Ghi nhận hoạt động gửi file nhóm
                    if (activityCallback != null) {
                        UserActivity groupFileActivity = new UserActivity(
                                userId, onlineUsers.get(userId), "GROUP_FILE",
                                pendingFileGroupId, pendingFileName, LocalDateTime.now(), true);
                        activityCallback.accept(groupFileActivity);
                    }
                } else {
                    logger.error("Server: Lỗi lưu file group vào database!");
                    out.writeObject("GROUP_FILE_SENT|FAIL");
                    out.flush();
                }
            } else if (pendingFileReceiverId > 0) {
                m = new Message(0, userId, pendingFileReceiverId, null, fileData, pendingFileName, false,
                        LocalDateTime.now());
                logger.debug("Server: Tạo Message cho friend - receiverId: {}, fileName: {}", pendingFileReceiverId,
                        pendingFileName);
                if (messageDAO.saveMessage(m)) {
                    logger.info("Server: Lưu file thành công! Message ID: {}", m.getId());
                    ChatServer.TOTAL_FILES_SENT.incrementAndGet(); // Tăng counter
                    broadcaster.broadcastMessage(m, pendingFileReceiverId);
                    out.writeObject("FILE_SENT|OK|" + m.getId());
                    out.flush();

                    // Ghi nhận hoạt động gửi file
                    if (activityCallback != null) {
                        UserActivity fileActivity = new UserActivity(
                                userId, onlineUsers.get(userId), "FILE",
                                pendingFileReceiverId, pendingFileName, LocalDateTime.now());
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

    private void handleSearchMessages(String msg) {
        // Format: SEARCH_MESSAGES|userId|targetId|query|isGroup
        try {
            String[] p = msg.split("\\|", 5);
            if (p.length >= 5) {
                int rUserId = Integer.parseInt(p[1]);
                int targetId = Integer.parseInt(p[2]);
                String query = p[3];
                boolean isGroup = Boolean.parseBoolean(p[4]);

                List<Message> results;
                if (isGroup) {
                    results = messageDAO.searchGroupMessages(targetId, query);
                } else {
                    results = messageDAO.searchMessages(rUserId, targetId, query);
                }

                java.util.Map<String, Object> response = new java.util.HashMap<>();
                response.put("type", "SEARCH_MESSAGES_RESULT");
                response.put("data", results);
                out.writeObject(response);
            }
        } catch (Exception e) {
            logger.error("Error searching messages", e);
            try {
                out.writeObject(new ArrayList<>());
            } catch (IOException ignored) {
            }
        }
    }

    private void handleGetPinnedMessages(String msg) {
        // Format: GET_PINNED_MESSAGES|userId|targetId|isGroup
        try {
            String[] p = msg.split("\\|");
            if (p.length >= 4) {
                int rUserId = Integer.parseInt(p[1]);
                int targetId = Integer.parseInt(p[2]);
                boolean isGroup = Boolean.parseBoolean(p[3]);

                List<Message> results;
                if (isGroup) {
                    results = messageDAO.getPinnedGroupMessages(targetId);
                } else {
                    results = messageDAO.getPinnedMessages(rUserId, targetId);
                }
                java.util.Map<String, Object> response = new java.util.HashMap<>();
                response.put("type", "PINNED_MESSAGES_RESULT");
                response.put("data", results);
                out.writeObject(response);
            }
        } catch (Exception e) {
            logger.error("Error getting pinned messages", e);
            try {
                out.writeObject(new ArrayList<>());
            } catch (IOException ignored) {
            }
        }
    }

    private void handlePinMessage(String msg) {
        try {
            String[] p = msg.split("\\|");
            int messageId = Integer.parseInt(p[1]);
            boolean isPinned = Boolean.parseBoolean(p[2]);

            boolean success = messageDAO.pinMessage(messageId, isPinned);
            if (success) {
                out.writeObject("PIN_MESSAGE|SUCCESS|" + messageId + "|" + isPinned);
                Message m = messageDAO.getMessageById(messageId);
                if (m != null) {
                    String event = "MESSAGE_PIN_UPDATE|" + messageId + "|" + isPinned;
                    if (m.getGroupId() > 0) {
                        broadcaster.broadcastToGroup(m.getGroupId(), event, groupDAO);
                    } else {
                        broadcaster.broadcastToUser(m.getReceiverId(), event);
                        broadcaster.broadcastToUser(m.getSenderId(), event);
                    }
                }
            } else {
                out.writeObject("PIN_MESSAGE|FAIL");
            }
        } catch (Exception e) {
            logger.error("Error pinning message", e);
            try {
                out.writeObject("PIN_MESSAGE|FAIL|" + e.getMessage());
            } catch (IOException ignored) {
            }
        }
    }

    private void handleMarkAsRead(String msg) {
        try {
            String[] p = msg.split("\\|");
            int rUserId = Integer.parseInt(p[1]);
            int targetId = Integer.parseInt(p[2]);
            boolean isGroup = p.length >= 4 && "GROUP".equals(p[3]);

            if (!isGroup) {
                messageDAO.markMessagesAsRead(targetId, rUserId);
                broadcaster.broadcastToUser(targetId, "MESSAGES_READ|" + rUserId);
            }
        } catch (Exception e) {
            logger.error("Error marking messages as read", e);
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
                        disconnectedUserId, username, "LOGOUT", LocalDateTime.now());
                activityCallback.accept(logoutActivity);
            }

            logger.info("User {} đã thoát", userId);

            // Broadcast USER_OFFLINE cho tất cả friends
            try {
                List<Integer> friendIds = friendDAO.getFriendsByUserId(disconnectedUserId);
                for (int friendId : friendIds) {
                    broadcaster.broadcastToUser(friendId, "USER_OFFLINE|" + disconnectedUserId);
                }
                logger.debug("Đã broadcast USER_OFFLINE cho {} friends của user {}", friendIds.size(),
                        disconnectedUserId);
            } catch (Exception e) {
                logger.error("Lỗi khi broadcast USER_OFFLINE", e);
            }
        }
        try {
            socket.close();
        } catch (Exception ignored) {
        }
    }
}
