package org.example.zalu.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * License Server - Server xác thực license
 * Chạy trên máy của bạn để xác thực license cho code
 */
public class LicenseServer {
    private static final Logger logger = LoggerFactory.getLogger(LicenseServer.class);
    
    private static final int DEFAULT_PORT = 8888;
    private static final String VALID_LICENSE_KEY = "ZALU-2024-VALID";
    
    private static volatile boolean running = false;
    private static ServerSocket serverSocket;
    private static Thread serverThread;
    
    /**
     * Khởi động License Server
     */
    public static void startLicenseServer() {
        if (running) {
            logger.warn("License Server đã đang chạy");
            return;
        }
        
        int port = getLicenseServerPort();
        
        serverThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                running = true;
                logger.info("🔐 License Server đang chạy trên port {}", port);
                logger.info("   Chỉ code có license hợp lệ mới được phép chạy");
                
                while (running) {
                    try {
                        Socket client = serverSocket.accept();
                        logger.debug("License request từ: {}", client.getInetAddress());
                        
                        // Xử lý request trong thread riêng
                        new Thread(() -> handleLicenseRequest(client)).start();
                    } catch (IOException e) {
                        if (running) {
                            logger.error("Lỗi khi chấp nhận client license: {}", e.getMessage());
                        }
                    }
                }
            } catch (IOException e) {
                logger.error("License Server lỗi: {}", e.getMessage(), e);
                running = false;
            }
        });
        
        serverThread.setDaemon(true);
        serverThread.start();
    }
    
    /**
     * Dừng License Server
     */
    public static void stopLicenseServer() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            logger.error("Lỗi khi đóng License Server: {}", e.getMessage());
        }
        logger.info("License Server đã dừng");
    }
    
    /**
     * Xử lý request license
     */
    private static void handleLicenseRequest(Socket client) {
        try {
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
            OutputStream writer = client.getOutputStream();
            
            // Đọc request
            StringBuilder request = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                request.append(line).append("\n");
            }
            
            // Đọc body nếu có
            if (request.toString().contains("Content-Length:")) {
                int contentLength = 0;
                for (String reqLine : request.toString().split("\n")) {
                    if (reqLine.toLowerCase().startsWith("content-length:")) {
                        contentLength = Integer.parseInt(reqLine.split(":")[1].trim());
                        break;
                    }
                }
                
                if (contentLength > 0) {
                    char[] body = new char[contentLength];
                    reader.read(body, 0, contentLength);
                    request.append(new String(body));
                }
            }
            
            String requestStr = request.toString();
            logger.debug("License request: {}", requestStr);
            
            // Kiểm tra license key
            boolean isValid = false;
            if (requestStr.contains(VALID_LICENSE_KEY)) {
                isValid = true;
            }
            
            // Gửi response
            String response;
            if (isValid) {
                response = "HTTP/1.1 200 OK\r\n" +
                          "Content-Type: application/json\r\n" +
                          "Access-Control-Allow-Origin: *\r\n" +
                          "\r\n" +
                          "{\"valid\":true,\"message\":\"License hợp lệ\"}";
                logger.info("✓ License hợp lệ từ {}", client.getInetAddress());
            } else {
                response = "HTTP/1.1 403 Forbidden\r\n" +
                          "Content-Type: application/json\r\n" +
                          "\r\n" +
                          "{\"valid\":false,\"message\":\"License không hợp lệ\"}";
                logger.warn("✗ License không hợp lệ từ {}", client.getInetAddress());
            }
            
            writer.write(response.getBytes(StandardCharsets.UTF_8));
            writer.flush();
            
        } catch (Exception e) {
            logger.error("Lỗi khi xử lý license request: {}", e.getMessage());
        } finally {
            try {
                client.close();
            } catch (IOException e) {
                // Ignore
            }
        }
    }
    
    /**
     * Lấy port từ cấu hình
     */
    private static int getLicenseServerPort() {
        try {
            Properties props = new Properties();
            var inputStream = LicenseServer.class.getClassLoader()
                    .getResourceAsStream("license.properties");
            
            if (inputStream != null) {
                props.load(inputStream);
                return Integer.parseInt(
                    props.getProperty("license.server.port", String.valueOf(DEFAULT_PORT))
                );
            }
        } catch (Exception e) {
            logger.warn("Không đọc được cấu hình license port, dùng mặc định: {}", DEFAULT_PORT);
        }
        return DEFAULT_PORT;
    }
    
    /**
     * Kiểm tra License Server có đang chạy không
     */
    public static boolean isRunning() {
        return running;
    }
    
    /**
     * Main method để chạy License Server độc lập (nếu cần)
     */
    public static void main(String[] args) {
        logger.info("=== ZALU LICENSE SERVER ===");
        logger.info("Khởi động License Server...");
        startLicenseServer();
        
        // Giữ server chạy
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Đang tắt License Server...");
            stopLicenseServer();
        }));
        
        try {
            // Giữ main thread chạy
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            logger.info("License Server bị ngắt");
            stopLicenseServer();
        }
    }
}

