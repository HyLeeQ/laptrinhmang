package org.example.zalu.client;

import javafx.application.Platform;
import org.example.zalu.exception.connection.ServerConnectionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.util.Properties;

public class ChatClient {
    private static final Logger logger = LoggerFactory.getLogger(ChatClient.class);

    private static String SERVER_ADDRESS = "localhost"; // Default: localhost
    private static int SERVER_PORT = 5000;

    // Đọc cấu hình từ file properties hoặc system property
    static {
        try {
            // Thử đọc từ file properties trước
            Properties props = new Properties();
            InputStream is = ChatClient.class.getClassLoader()
                    .getResourceAsStream("server.properties");
            if (is != null) {
                props.load(is);
                SERVER_ADDRESS = props.getProperty("server.address", "localhost");
                SERVER_PORT = Integer.parseInt(props.getProperty("server.port", "5000"));
                logger.info("✓ Đã đọc cấu hình từ server.properties: {}:{}", SERVER_ADDRESS, SERVER_PORT);
                is.close();
            }
        } catch (Exception e) {
            // Nếu không có file, thử đọc từ system property
            String address = System.getProperty("server.address");
            String port = System.getProperty("server.port");
            if (address != null) {
                SERVER_ADDRESS = address;
                logger.info("✓ Đã đọc server.address từ system property: {}", SERVER_ADDRESS);
            }
            if (port != null) {
                try {
                    SERVER_PORT = Integer.parseInt(port);
                    logger.info("✓ Đã đọc server.port từ system property: {}", SERVER_PORT);
                } catch (NumberFormatException ignored) {
                }
            }
        }

        // Nếu vẫn là localhost và không có cấu hình, dùng giá trị mặc định
        if ("localhost".equals(SERVER_ADDRESS)) {
            logger.info(
                    "ℹ Sử dụng IP mặc định: localhost (để kết nối từ máy khác, cấu hình trong server.properties hoặc dùng -Dserver.address=IP)");
        }
    }

    private static Socket socket;
    private static ObjectOutputStream out;
    private static ObjectInputStream in;

    private static volatile boolean connected = false;
    private static volatile boolean listening = false;

    public static int userId = -1;

    // Setter cho cấu hình server (dùng cho Service Discovery)
    public static void setServerConfig(String address, int port) {
        SERVER_ADDRESS = address;
        SERVER_PORT = port;
        logger.info("Đã cập nhật cấu hình server: {}:{}", SERVER_ADDRESS, SERVER_PORT);
        // Reset kết nối để lần connect tiếp theo dùng thông tin mới
        disconnect();
    }

    // Lưu login callback để gọi khi nhận response từ server
    private static LoginCallback pendingLoginCallback = null;

    public static boolean connectToServer() {
        logger.info("=== Đang kết nối đến Server ===");
        logger.info("Server Address: {}", SERVER_ADDRESS);
        logger.info("Server Port: {}", SERVER_PORT);

        closeConnection(false, false);

        try {
            logger.debug("Đang tạo socket connection...");
            socket = new Socket(SERVER_ADDRESS, SERVER_PORT);

            logger.debug("Đang cấu hình socket options...");
            socket.setSoTimeout(120000); // Tăng timeout lên 120 giây cho file lớn
            socket.setKeepAlive(true);
            socket.setTcpNoDelay(true); // Tắt Nagle algorithm
            socket.setSendBufferSize(1024 * 1024 * 2); // 2MB send buffer
            socket.setReceiveBufferSize(1024 * 1024 * 2); // 2MB receive buffer

            logger.debug("Đang khởi tạo ObjectOutputStream...");
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();

            logger.debug("Đang khởi tạo ObjectInputStream...");
            in = new ObjectInputStream(socket.getInputStream());

            connected = true;
            logger.info("=== ✓ Kết nối thành công ===");
            logger.info("Connected to: {}:{}", SERVER_ADDRESS, SERVER_PORT);
            logger.info("Socket timeout: 120s");
            logger.info("Buffer size: 2MB");
            return true;
        } catch (java.net.ConnectException e) {
            logger.error("=== ✗ Kết nối thất bại ===");
            logger.error("Lỗi: Connection refused");
            logger.error("Server: {}:{}", SERVER_ADDRESS, SERVER_PORT);
            logger.error("Nguyên nhân có thể:");
            logger.error("  1. Server chưa khởi động");
            logger.error("  2. Server đang chạy trên port khác");
            logger.error("  3. Firewall đang chặn kết nối");
            logger.error("  4. Địa chỉ IP không đúng");
            logger.debug("Chi tiết lỗi: {}", e.getMessage());
            connected = false;
            return false;
        } catch (java.net.UnknownHostException e) {
            logger.error("=== ✗ Kết nối thất bại ===");
            logger.error("Lỗi: Unknown host");
            logger.error("Server: {}", SERVER_ADDRESS);
            logger.error("Không thể resolve địa chỉ server");
            logger.debug("Chi tiết lỗi: {}", e.getMessage());
            connected = false;
            return false;
        } catch (IOException e) {
            logger.error("=== ✗ Kết nối thất bại ===");
            logger.error("Lỗi I/O: {}", e.getClass().getSimpleName());
            logger.error("Server: {}:{}", SERVER_ADDRESS, SERVER_PORT);
            logger.error("Chi tiết: {}", e.getMessage());
            logger.debug("Stack trace:", e);
            connected = false;
            return false;
        }
    }

    public static void disconnect() {
        closeConnection(true, true);
    }

    public static void reconnect() {
        logger.info("Đang thử kết nối lại...");
        closeConnection(false, false); // giữ nguyên session hiện tại

        for (int i = 1; i <= 3; i++) {
            if (connectToServer()) {
                logger.info("✓ Kết nối lại thành công sau {} lần thử!", i);
                if (userId > 0) {
                    startGlobalListener();
                    resumeSession();
                }
                return;
            }
            try {
                Thread.sleep(2000);
            } catch (Exception ignored) {
            }
        }
    }

    private static final java.util.concurrent.ExecutorService senderExecutor = java.util.concurrent.Executors
            .newSingleThreadExecutor();

    // Gửi tin nhắn dạng String
    public static void sendRequest(String request) {
        senderExecutor.submit(() -> {
            if (!connected || out == null) {
                logger.warn("✗ Không thể gửi (chưa kết nối): {}", request);
                reconnect();
                return;
            }
            try {
                out.writeObject(request);
                out.flush();
                logger.info("Gửi: {}", request);
            } catch (IOException e) {
                logger.error("Lỗi gửi request: {}", e.getMessage());
                reconnect();
            }
        });
    }

    // Gửi object (byte[] file, List, v.v.)
    public static void sendObject(Object obj) {
        senderExecutor.submit(() -> {
            if (!connected || out == null) {
                reconnect();
                return;
            }
            try {
                if (obj instanceof byte[]) {
                    byte[] data = (byte[]) obj;
                    logger.info("Gửi file: {} bytes", data.length);
                    // Gửi file data
                    out.writeObject(data);
                    out.flush();
                    logger.debug("Đã flush file data thành công");
                } else {
                    out.writeObject(obj);
                    out.flush();
                    logger.info("Gửi object: {}", obj);
                }
            } catch (IOException e) {
                logger.error("Lỗi gửi object: {}", e.getMessage(), e);
                reconnect();
            }
        });
    }

    public static void startGlobalListener() {
        if (listening || !connected)
            return;

        listening = true;
        new Thread(() -> {
            logger.info("Global listener đã khởi động");
            while (listening && connected && !socket.isClosed()) {
                try {
                    Object obj = in.readObject();
                    if (obj != null) {
                        ChatEventManager.getInstance().processEvent(obj);
                    }
                } catch (Exception e) {
                    if (listening && connected) { // chỉ reconnect khi thật sự đang muốn kết nối
                        logger.warn("Mất kết nối bất ngờ → thử reconnect...");
                        connected = false;
                        Platform.runLater(ChatClient::reconnect); // dùng Platform để tránh deadlock JavaFX
                    } else {
                        logger.info("Ngắt kết nối chủ động (logout) → không reconnect");
                    }
                    break;
                }
            }
        }, "Global-Listener").start();
    }

    private static void resumeSession() {
        if (!connected || out == null || userId <= 0) {
            return;
        }
        try {
            out.writeObject("RESUME_SESSION|" + userId);
            out.flush();
            logger.info("Đã gửi yêu cầu khôi phục session cho userId={}", userId);
        } catch (IOException e) {
            logger.error("Không thể gửi yêu cầu khôi phục session: {}", e.getMessage());
        }
    }

    private static void closeConnection(boolean resetSession, boolean notifyServer) {
        listening = false;
        connected = false;

        if (notifyServer && out != null) {
            try {
                out.writeObject("LOGOUT");
                out.flush();
                logger.info("Đã gửi LOGOUT tới server");
                Thread.sleep(200);
            } catch (Exception ignored) {
            }
        }

        try {
            if (out != null) {
                out.flush();
                out.close();
            }
        } catch (Exception ignored) {
        }

        try {
            if (in != null)
                in.close();
        } catch (Exception ignored) {
        }

        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (Exception ignored) {
        }

        out = null;
        in = null;
        socket = null;

        if (resetSession) {
            userId = -1;
        }

        if (notifyServer) {
            logger.info("Đã ngắt kết nối sạch sẽ – không reconnect tự động");
        }
    }

    public static void login(String username, String password, LoginCallback callback) {
        // Nếu chưa kết nối, thử kết nối trước
        if (!connected) {
            logger.info("Chưa kết nối server, đang thử kết nối...");
            if (!connectToServer()) {
                callback.onFail("Không thể kết nối tới server. Vui lòng kiểm tra server có đang chạy không.");
                return;
            }
        }

        startGlobalListener();

        try {
            Thread.sleep(100);
        } catch (InterruptedException ignored) {
        }

        // Lưu callback để gọi khi nhận response từ server
        pendingLoginCallback = callback;

        sendRequest("LOGIN_REQUEST|" + username + "|" + password);

        // KHÔNG gọi callback.onSuccess(-1) ở đây - đợi response từ server
        // Response sẽ được xử lý trong ChatEventManager.processEvent()
    }

    /**
     * Gọi login callback khi nhận response từ server
     * Được gọi từ ChatEventManager khi nhận LOGIN_RESPONSE
     */
    public static void triggerLoginCallback(boolean success, int userId, String errorMessage) {
        if (pendingLoginCallback != null) {
            if (success) {
                pendingLoginCallback.onSuccess(userId);
            } else {
                pendingLoginCallback.onFail(errorMessage != null ? errorMessage : "Đăng nhập thất bại");
            }
            pendingLoginCallback = null; // Clear sau khi gọi
        }
    }

    public static boolean isConnected() {
        return connected && socket != null && !socket.isClosed();
    }

    public static int getUserId() {
        return userId;
    }

    public static ObjectOutputStream getOut() {
        return out;
    }

    public interface LoginCallback {
        void onSuccess(int userId);

        void onFail(String message);
    }

    public static void sendErrorReport(Throwable e) {
        if (!connected)
            return;

        try {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            String stackTrace = sw.toString();

            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getName();

            // Lấy username hiện tại (nếu có thể, cần lưu username vào ChatClient hoặc lấy
            // từ đâu đó)
            // Tạm thời để username là "Unknown" nếu không lưu
            String username = "User " + userId;

            org.example.zalu.model.ClientErrorLog errorLog = new org.example.zalu.model.ClientErrorLog(
                    userId, username, msg, stackTrace);

            sendObject(errorLog);
            logger.info("Đã gửi báo cáo lỗi về server: {}", msg);
        } catch (Exception ex) {
            logger.error("Không thể gửi báo cáo lỗi: {}", ex.getMessage());
        }
    }
}