package org.example.zalu.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.*;

/**
 * Lắng nghe các gói tin UDP Broadcast để client tìm thấy server
 */
public class ServerDiscoveryListener extends Thread {
    private static final Logger logger = LoggerFactory.getLogger(ServerDiscoveryListener.class);
    private static final int DISCOVERY_PORT = 8888;
    private final int tcpPort;
    private boolean running = true;
    private DatagramSocket socket;

    public ServerDiscoveryListener(int tcpPort) {
        this.tcpPort = tcpPort;
        this.setDaemon(true); // Tự động tắt khi main thread tắt
        this.setName("Discovery-Listener");
    }

    @Override
    public void run() {
        logger.info("=== UDP Discovery Listener Starting ===");
        logger.info("TCP Port to advertise: {}", tcpPort);
        logger.info("UDP Discovery Port: {}", DISCOVERY_PORT);

        try {
            // Lắng nghe trên 0.0.0.0 (tất cả interfaces)
            socket = new DatagramSocket(DISCOVERY_PORT);
            socket.setBroadcast(true);

            logger.info("✓ Server Discovery đang lắng nghe UDP trên port {}", DISCOVERY_PORT);
            logger.info("✓ Broadcast mode: ENABLED");
            logger.info("✓ Sẵn sàng nhận discovery requests từ clients...");

            byte[] buffer = new byte[1024];
            int requestCount = 0;

            while (running) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                try {
                    logger.debug("Đang chờ nhận UDP packet...");
                    socket.receive(packet);

                    String message = new String(packet.getData(), 0, packet.getLength()).trim();
                    String clientAddress = packet.getAddress().getHostAddress();
                    int clientPort = packet.getPort();

                    logger.debug("Nhận packet từ {}:{} - Nội dung: '{}'",
                            clientAddress, clientPort, message);

                    if ("ZALU_DISCOVERY_REQUEST".equals(message)) {
                        requestCount++;
                        logger.info(">>> [Request #{}] Discovery request từ {}:{}",
                                requestCount, clientAddress, clientPort);

                        // Phản hồi lại: "ZALU_DISCOVERY_RESPONSE|TCP_PORT"
                        // Client sẽ tự lấy IP từ người gửi gói tin này
                        String response = "ZALU_DISCOVERY_RESPONSE|" + tcpPort;
                        byte[] sendData = response.getBytes();

                        DatagramPacket sendPacket = new DatagramPacket(
                                sendData,
                                sendData.length,
                                packet.getAddress(),
                                packet.getPort());

                        socket.send(sendPacket);
                        logger.info("<<< [Response #{}] Đã gửi response tới {}:{} - TCP Port: {}",
                                requestCount, clientAddress, clientPort, tcpPort);
                    } else {
                        logger.warn("⚠ Nhận packet không hợp lệ từ {}:{} - Nội dung: '{}'",
                                clientAddress, clientPort, message);
                    }
                } catch (IOException e) {
                    if (running) {
                        logger.error("✗ Lỗi khi nhận/gửi packet discovery: {}", e.getMessage(), e);
                    } else {
                        logger.debug("Socket đã đóng (listener đang dừng)");
                    }
                }
            }

            logger.info("=== UDP Discovery Listener Stopped ===");
            logger.info("Total requests processed: {}", requestCount);

        } catch (BindException e) {
            logger.error("✗ Không thể bind port discovery {}. Có thể một instance server khác đang chạy.",
                    DISCOVERY_PORT);
            logger.error("Chi tiết lỗi: {}", e.getMessage());
        } catch (Exception e) {
            logger.error("✗ Lỗi khởi tạo Server Discovery: {}", e.getMessage(), e);
        } finally {
            if (socket != null && !socket.isClosed()) {
                socket.close();
                logger.info("✓ UDP Socket đã đóng");
            }
        }
    }

    public void stopListener() {
        logger.info("=== Đang dừng UDP Discovery Listener ===");
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
            logger.info("✓ UDP Socket đã đóng từ stopListener()");
        }
        logger.info("=== UDP Discovery Listener đã dừng ===");
    }
}
