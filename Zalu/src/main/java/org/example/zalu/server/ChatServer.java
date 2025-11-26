package org.example.zalu.server;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.example.zalu.dao.*;
import org.example.zalu.model.UserActivity;
import org.example.zalu.util.database.MySQLConfigHelper;
import org.example.zalu.util.license.LicenseValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
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
    private static volatile boolean serverRunning = false;
    private static ServerSocket serverSocket;
    private static Thread serverThread;

    public static void main(String[] args) {
        // ============================================
        // BẢO VỆ LICENSE - CHỈ CHẠY KHI CÓ LICENSE SERVER
        // ============================================
        logger.info("\n" + "=".repeat(50));
        logger.info("KIỂM TRA LICENSE - BẢO VỆ CODE");
        logger.info("=".repeat(50));
        
        // Khởi động License Server trước
        LicenseServer.startLicenseServer();
        
        // Đợi một chút để License Server khởi động
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Kiểm tra license
        if (!LicenseValidator.validateLicense()) {
            logger.error("\n" + "=".repeat(50));
            logger.error("❌ CODE KHÔNG ĐƯỢC PHÉP CHẠY!");
            logger.error("   Không thể xác thực license với License Server");
            logger.error("   Code này chỉ hoạt động khi có License Server của bạn");
            logger.error("   Vui lòng đảm bảo License Server đang chạy");
            logger.error("=".repeat(50) + "\n");
            System.exit(1);
        }
        
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
            int port = 5000;
            try {
                serverSocket = new ServerSocket(port);
                logger.info("🚀 Zalu Server đang chạy trên port {}", port);
                ClientBroadcaster broadcaster = new ClientBroadcaster(clients);
                while (serverRunning) {
                    try {
                        Socket client = serverSocket.accept();
                        logger.info("✅ Client mới kết nối: {}", client.getInetAddress());
                        new ClientHandler(client, userDAO, friendDAO, messageDAO, groupDAO,
                                         clients, onlineUsers, ChatServer::updateUserList, broadcaster,
                                         ChatServer::addActivity).start();
                    } catch (IOException e) {
                        if (serverRunning) {
                            logger.error("Lỗi khi chấp nhận client: {}", e.getMessage());
                        }
                    }
                }
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
        serverRunning = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            logger.error("Lỗi khi đóng server: {}", e.getMessage());
        }
        logger.info("Server đã dừng");
        
        // Dừng License Server khi dừng Chat Server
        LicenseServer.stopLicenseServer();
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
     * Đặt callback để cập nhật danh sách user (dùng bởi ServerUI)
     */
    public static void setUserListUpdateCallback(Runnable callback) {
        userListUpdateCallback = callback;
    }
    
    /**
     * Lấy danh sách user online (dùng bởi ServerUI)
     */
    public static Map<Integer, String> getOnlineUsers() {
        return new HashMap<>(onlineUsers);
    }

    // ClientHandler đã được tách ra file riêng: org.example.zalu.server.ClientHandler
    
    public static void broadcastToUser(int userId, String message) {
        ObjectOutputStream out = clients.get(userId);
        if (out != null) {
            try {
                out.writeObject(message);
                out.flush();
            } catch (Exception e) {
                logger.warn("Không gửi được {} cho user {}", message, userId, e);
            }
        }
    }
}
