package org.example.zalu.server;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.example.zalu.dao.*;
import org.example.zalu.model.UserActivity;
import org.example.zalu.util.database.MySQLConfigHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;

public class ChatServer {
    private static final Logger logger = LoggerFactory.getLogger(ChatServer.class);

    private static final Map<Integer, ObjectOutputStream> clients = new HashMap<>();
    private static final Map<Integer, String> onlineUsers = new HashMap<>();
    private static UserDAO userDAO;
    private static FriendDAO friendDAO;
    private static MessageDAO messageDAO;
    private static GroupDAO groupDAO;
    private static final ObservableList<String> userList = FXCollections.observableArrayList();
    private static Consumer<UserActivity> activityCallback;
    private static Runnable userListUpdateCallback;
    private static Consumer<org.example.zalu.model.ClientErrorLog> errorReportCallback;

    private static volatile boolean serverRunning = false;
    private static ServerSocket serverSocket;
    private static Thread serverThread;

    private static final java.util.Set<Integer> MUTED_USERS = java.util.Collections
            .synchronizedSet(new java.util.HashSet<>());
    public static final java.util.concurrent.atomic.AtomicLong TOTAL_MESSAGES_SENT = new java.util.concurrent.atomic.AtomicLong(
            0);
    public static final java.util.concurrent.atomic.AtomicLong TOTAL_BYTES_TRANSFERRED = new java.util.concurrent.atomic.AtomicLong(
            0);
    public static final java.util.concurrent.atomic.AtomicLong TOTAL_FILES_SENT = new java.util.concurrent.atomic.AtomicLong(
            0);
    public static final java.util.concurrent.atomic.AtomicInteger PEAK_CONCURRENT_USERS = new java.util.concurrent.atomic.AtomicInteger(
            0);
    private static LocalDateTime serverStartTime = null;

    public static void setUserListUpdateCallback(Runnable callback) {
        userListUpdateCallback = callback;
    }

    public static void setErrorReportingCallback(Consumer<org.example.zalu.model.ClientErrorLog> callback) {
        errorReportCallback = callback;
    }

    public static Consumer<org.example.zalu.model.ClientErrorLog> getErrorReportingCallback() {
        return errorReportCallback;
    }

    public static void main(String[] args) {
        logger.info("⚠ BỎ QUA KIỂM TRA LICENSE (yêu cầu bảo mật đã tắt cho bản dev).");

        // KHỞI TẠO TẤT CẢ DAO ĐÚNG CÁCH – KHÔNG CẦN CONNECTION
        userDAO = new UserDAO();
        friendDAO = new FriendDAO();
        messageDAO = new MessageDAO();
        groupDAO = new GroupDAO();
        logger.info("✓ Tất cả DAO đã khởi tạo thành công với HikariCP!");

        // Kiểm tra và cấu hình max_allowed_packet
        logger.info("=== Kiểm tra cấu hình MySQL ===");
        try {
            MySQLConfigHelper.checkAndSetMaxAllowedPacket();
            logger.info("================================\n");
        } catch (Exception e) {
            logger.error("⚠ Không thể kết nối tới MySQL: {}", e.getMessage());
            logger.error("⚠ Vui lòng đảm bảo MySQL đang chạy và cấu hình trong database.properties đúng!");
            logger.error("⚠ Server vẫn sẽ khởi động nhưng có thể không hoạt động đầy đủ.\n");
        }

        // Khởi động giao diện Server
        Platform.startup(() -> new Thread(() -> Application.launch(ServerUI.class)).start());
    }

    /**
     * Đọc port từ server.properties
     */
    private static int getServerPort() {
        try {
            Properties props = new Properties();
            InputStream is = ChatServer.class.getClassLoader()
                    .getResourceAsStream("server.properties");
            if (is != null) {
                props.load(is);
                String portStr = props.getProperty("server.port", "5000");
                is.close();
                return Integer.parseInt(portStr);
            }
        } catch (Exception e) {
            logger.warn("Không đọc được port từ server.properties, dùng mặc định 5000: {}", e.getMessage());
        }
        return 5000; // Default port
    }

    /**
     * Kiểm tra port có đang được sử dụng không
     */
    private static boolean isPortAvailable(int port) {
        try (ServerSocket testSocket = new ServerSocket(port)) {
            testSocket.setReuseAddress(false);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Tìm port khả dụng gần port được chỉ định
     * 
     * @param preferredPort Port mong muốn
     * @param maxAttempts   Số lần thử tối đa
     * @return Port khả dụng, hoặc -1 nếu không tìm được
     */
    private static int findAvailablePort(int preferredPort, int maxAttempts) {
        for (int i = 0; i < maxAttempts; i++) {
            int port = preferredPort + i;
            if (port > 65535)
                break; // Port tối đa
            if (isPortAvailable(port)) {
                return port;
            }
        }
        return -1;
    }

    /**
     * Bắt đầu server
     */
    public static void startServer() {
        if (serverRunning) {
            logger.warn("Server đã đang chạy");
            return;
        }

        if (serverThread != null && serverThread.isAlive()) {
            logger.warn("Server thread đã đang chạy");
            return;
        }

        serverRunning = true;
        serverThread = new Thread(() -> {
            int port = getServerPort();

            // Kiểm tra port có đang được sử dụng không
            if (!isPortAvailable(port)) {
                logger.warn("==========================================");
                logger.warn("⚠ PORT {} ĐÃ ĐƯỢC SỬ DỤNG!", port);
                logger.warn("==========================================");
                logger.warn("Đang tìm port khả dụng...");

                // Tự động tìm port khả dụng (thử 10 port tiếp theo)
                int availablePort = findAvailablePort(port, 10);

                if (availablePort > 0) {
                    logger.info("✓ Tìm thấy port khả dụng: {}", availablePort);
                    logger.info("  Server sẽ chạy trên port {} thay vì port {}", availablePort, port);
                    logger.warn("  LƯU Ý: Client cần cấu hình server.port={} trong server.properties", availablePort);
                    port = availablePort;
                } else {
                    logger.error("==========================================");
                    logger.error("❌ KHÔNG TÌM THẤY PORT KHẢ DỤNG!");
                    logger.error("==========================================");
                    logger.error("Có thể do:");
                    logger.error("  1. Server đang chạy ở instance khác");
                    logger.error("  2. Ứng dụng khác đang dùng port {}", port);
                    logger.error("  3. Server chưa được đóng đúng cách từ lần chạy trước");
                    logger.error("");
                    logger.error("Giải pháp:");
                    logger.error("  - Tắt instance server đang chạy");
                    logger.error("  - Hoặc đổi port trong server.properties");
                    logger.error("  - Windows: netstat -ano | findstr :{}", port);
                    logger.error("  - Linux/Mac: lsof -i :{}", port);
                    logger.error("==========================================");
                    serverRunning = false;
                    return;
                }
            }

            try {
                serverSocket = new ServerSocket(port);
                serverStartTime = LocalDateTime.now(); // Ghi nhận thời gian bắt đầu
                logger.info("🚀 Zalu Server đang chạy trên port {}", port);
                ClientBroadcaster broadcaster = new ClientBroadcaster(clients);
                // Khởi động Service Discovery (UDP Broadcast Listener)
                new ServerDiscoveryListener(port).start();

                while (serverRunning) {
                    try {
                        Socket client = serverSocket.accept();
                        logger.info("✅ Client mới kết nối: {}", client.getInetAddress());
                        ClientHandler handler = new ClientHandler(client, userDAO, friendDAO, messageDAO, groupDAO,
                                clients, onlineUsers, ChatServer::updateUserList, broadcaster,
                                ChatServer::addActivity);
                        clientHandlers.add(handler);
                        handler.start();
                    } catch (IOException e) {
                        if (serverRunning) {
                            logger.error("Lỗi khi chấp nhận client: {}", e.getMessage());
                        }
                    }
                }
            } catch (java.net.BindException e) {
                logger.error("==========================================");
                logger.error("❌ KHÔNG THỂ BIND PORT {}!", port);
                logger.error("==========================================");
                logger.error("Lỗi: {}", e.getMessage());
                logger.error("");
                logger.error("Port {} đã được sử dụng bởi process khác.", port);
                logger.error("Vui lòng:");
                logger.error("  1. Tắt process đang dùng port {}", port);
                logger.error("  2. Hoặc đổi port trong server.properties");
                logger.error("==========================================");
                serverRunning = false;
            } catch (IOException e) {
                logger.error("Server lỗi: {}", e.getMessage(), e);
                serverRunning = false;
            }
        });
        serverThread.start();
    }

    /**
     * Dừng server
     */
    public static void stopServer() {
        logger.info("Đang dừng server...");
        serverRunning = false;

        // Đóng server socket
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
                logger.debug("Server socket đã được đóng");
            }
        } catch (IOException e) {
            logger.error("Lỗi khi đóng server socket: {}", e.getMessage());
        }

        // Đợi thread kết thúc (tối đa 2 giây)
        if (serverThread != null && serverThread.isAlive()) {
            try {
                serverThread.join(2000);
                if (serverThread.isAlive()) {
                    logger.warn("Server thread chưa kết thúc sau 2 giây");
                }
            } catch (InterruptedException e) {
                logger.warn("Bị gián đoạn khi đợi server thread: {}", e.getMessage());
                Thread.currentThread().interrupt();
            }
        }

        logger.info("Server đã dừng");
    }

    private static void updateUserList() {
        Platform.runLater(() -> {
            userList.clear();
            onlineUsers.forEach((id, name) -> userList.add("ID: " + id + " | " + name));
            if (userListUpdateCallback != null) {
                userListUpdateCallback.run();
            }
        });
    }

    /**
     * Thêm hoạt động vào nhật ký
     */
    public static void addActivity(UserActivity activity) {
        if (activityCallback != null) {
            activityCallback.accept(activity);
        }
    }

    /**
     * Lấy callback để thêm hoạt động (dùng trong ClientHandler)
     */
    public static Consumer<UserActivity> getActivityCallback() {
        return ChatServer::addActivity;
    }

    /**
     * Đặt callback cho hoạt động (dùng bởi ServerUI)
     */
    public static void setActivityCallback(Consumer<UserActivity> callback) {
        activityCallback = callback;
    }

    /**
     * Lấy danh sách user online (dùng bởi ServerUI)
     */
    public static Map<Integer, String> getOnlineUsers() {
        return new HashMap<>(onlineUsers);
    }

    // ClientHandler đã được tách ra file riêng:
    // org.example.zalu.server.ClientHandler

    private static final java.util.List<ClientHandler> clientHandlers = java.util.Collections
            .synchronizedList(new java.util.ArrayList<>());

    public static void kickUser(int userId) {
        ObjectOutputStream clientOut = clients.get(userId);
        if (clientOut != null) {
            try {
                // Gửi thông báo KICKED trước khi đóng kết nối
                try {
                    clientOut.writeObject("KICKED|Bạn đã bị quản trị viên đá khỏi server");
                    clientOut.flush();
                    logger.info("Đã gửi KICKED message cho user {}", userId);
                } catch (IOException e) {
                    logger.warn("Không thể gửi KICKED message: {}", e.getMessage());
                }

                // Remove from map to prevent further messages
                clients.remove(userId);
                onlineUsers.remove(userId);

                // Get socket to close it forcefully
                synchronized (clientHandlers) {
                    ClientHandler targetHandler = null;
                    for (ClientHandler handler : clientHandlers) {
                        if (handler.getUserId() == userId) {
                            try {
                                // Đợi một chút để client nhận message
                                Thread.sleep(100);
                                handler.getSocket().close();
                            } catch (IOException e) {
                                logger.error("Lỗi khi đóng socket user bị kick: {}", e.getMessage());
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            targetHandler = handler;
                            break;
                        }
                    }
                    if (targetHandler != null) {
                        clientHandlers.remove(targetHandler);
                    }
                }

                // Log activity
                addActivity(new UserActivity(userId, "System", "KICK", LocalDateTime.now()));

                // Update UI list
                updateUserList();

                logger.info("Admin đã kick user {}", userId);
            } catch (Exception e) {
                logger.error("Lỗi khi kick user {}: {}", userId, e.getMessage());
            }
        }
    }

    public static void broadcastToUser(int userId, String message) {
        ObjectOutputStream out = clients.get(Integer.valueOf(userId));
        if (out != null) {
            try {
                out.writeObject(message);
                out.flush();
            } catch (Exception e) {
                logger.warn("Không gửi được " + message + " cho user " + userId, e);
            }
        }
    }

    // === CÁC TÍNH NĂNG MỚI (ANNOUNCEMENT, MUTE, STATS) ===

    public static void sendSystemAnnouncement(String content) {
        String packet = "SYSTEM_ANNOUNCEMENT|" + content;
        synchronized (clients) {
            for (ObjectOutputStream out : clients.values()) {
                try {
                    out.writeObject(packet);
                    out.flush();
                } catch (Exception e) {
                    // Bỏ qua lỗi gửi lẻ tẻ
                }
            }
        }
        logger.info("Admin đã gửi thông báo toàn server: {}", content);
        addActivity(new UserActivity(0, "System", "ANNOUNCEMENT", 0, content, LocalDateTime.now()));
    }

    public static void muteUser(int userId) {
        MUTED_USERS.add(userId);
        logger.info("Admin đã cấm chat (MUTE) user {}", userId);
        broadcastToUser(userId, "SYSTEM_ANNOUNCEMENT|Bạn đã bị Admin cấm chat!");
        addActivity(new UserActivity(userId, "System", "MUTE", LocalDateTime.now()));
    }

    public static void unmuteUser(int userId) {
        MUTED_USERS.remove(userId);
        logger.info("Admin đã bỏ cấm chat (UNMUTE) user {}", userId);
        broadcastToUser(userId, "SYSTEM_ANNOUNCEMENT|Bạn đã được Admin bỏ cấm chat.");
        addActivity(new UserActivity(userId, "System", "UNMUTE", LocalDateTime.now()));
    }

    public static boolean isUserMuted(int userId) {
        return MUTED_USERS.contains(userId);
    }

    public static LocalDateTime getServerStartTime() {
        return serverStartTime;
    }

    public static int getCurrentOnlineUsers() {
        return onlineUsers.size();
    }

    public static UserDAO getUserDAO() {
        return userDAO;
    }

    public static GroupDAO getGroupDAO() {
        return groupDAO;
    }
}
