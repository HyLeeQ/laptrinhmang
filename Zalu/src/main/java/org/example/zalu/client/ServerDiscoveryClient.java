package org.example.zalu.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.*;

/**
 * Client utility để tìm kiếm server trong mạng LAN
 */
public class ServerDiscoveryClient {
    private static final Logger logger = LoggerFactory.getLogger(ServerDiscoveryClient.class);
    private static final int DISCOVERY_PORT = 8888;
    private static final int TIMEOUT_MS = 3000; // Đợi tối đa 3 giây

    public static class ServerInfo {
        public String address;
        public int port;

        public ServerInfo(String address, int port) {
            this.address = address;
            this.port = port;
        }
    }

    /**
     * Tìm kiếm server trong mạng LAN bằng UDP Broadcast
     * 
     * @return ServerInfo nếu tìm thấy, null nếu không tìm thấy
     */
    public static ServerInfo findServer() {
        logger.info("=== Bắt đầu tìm kiếm Server trong mạng LAN ===");
        logger.info("Discovery Port: {}", DISCOVERY_PORT);
        logger.info("Timeout: {}ms", TIMEOUT_MS);

        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket();
            socket.setBroadcast(true);
            socket.setSoTimeout(TIMEOUT_MS);

            logger.info("✓ UDP Socket đã khởi tạo");
            logger.info("✓ Broadcast mode: ENABLED");
            logger.info("✓ Timeout: {}ms", TIMEOUT_MS);

            byte[] sendData = "ZALU_DISCOVERY_REQUEST".getBytes();
            int broadcastCount = 0;

            // 1. Gửi tới 255.255.255.255 (Global Broadcast)
            try {
                DatagramPacket sendPacket = new DatagramPacket(
                        sendData,
                        sendData.length,
                        InetAddress.getByName("255.255.255.255"),
                        DISCOVERY_PORT);
                socket.send(sendPacket);
                broadcastCount++;
                logger.info(">>> [Broadcast #{}] Sent to 255.255.255.255:{}", broadcastCount, DISCOVERY_PORT);
            } catch (Exception e) {
                logger.warn("✗ Failed to send to 255.255.255.255: {}", e.getMessage());
            }

            // 2. Gửi tới broadcast address của TẤT CẢ các interface mạng (Wifi, LAN, etc.)
            try {
                logger.info("Đang quét các network interfaces...");
                java.util.Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
                int interfaceCount = 0;

                while (interfaces.hasMoreElements()) {
                    NetworkInterface networkInterface = interfaces.nextElement();
                    interfaceCount++;

                    // Bỏ qua loopback (127.0.0.1) và các interface chưa bật
                    if (networkInterface.isLoopback()) {
                        logger.debug("  [Interface #{}] {} - SKIPPED (loopback)",
                                interfaceCount, networkInterface.getDisplayName());
                        continue;
                    }

                    if (!networkInterface.isUp()) {
                        logger.debug("  [Interface #{}] {} - SKIPPED (down)",
                                interfaceCount, networkInterface.getDisplayName());
                        continue;
                    }

                    logger.info("  [Interface #{}] {} - ACTIVE",
                            interfaceCount, networkInterface.getDisplayName());

                    int addressCount = 0;
                    for (java.net.InterfaceAddress interfaceAddress : networkInterface.getInterfaceAddresses()) {
                        InetAddress broadcast = interfaceAddress.getBroadcast();
                        addressCount++;

                        if (broadcast != null) {
                            try {
                                DatagramPacket sendPacket = new DatagramPacket(
                                        sendData, sendData.length, broadcast, DISCOVERY_PORT);
                                socket.send(sendPacket);
                                broadcastCount++;
                                logger.info("    >>> [Broadcast #{}] Sent to {} ({})",
                                        broadcastCount, broadcast.getHostAddress(),
                                        networkInterface.getDisplayName());
                            } catch (Exception e) {
                                logger.warn("    ✗ Failed to send to {}: {}", broadcast, e.getMessage());
                            }
                        } else {
                            logger.debug("    [Address #{}] No broadcast address", addressCount);
                        }
                    }
                }

                logger.info("✓ Đã quét {} network interfaces", interfaceCount);

            } catch (Exception e) {
                logger.error("✗ Error iterating network interfaces: {}", e.getMessage(), e);
            }

            logger.info("=== Tổng số broadcast packets gửi đi: {} ===", broadcastCount);
            logger.info("Đang chờ nhận phản hồi từ server (timeout: {}ms)...", TIMEOUT_MS);

            // Chờ nhận phản hồi
            byte[] recvBuf = new byte[1024];
            DatagramPacket recvPacket = new DatagramPacket(recvBuf, recvBuf.length);

            try {
                long startTime = System.currentTimeMillis();
                socket.receive(recvPacket);
                long responseTime = System.currentTimeMillis() - startTime;

                String message = new String(recvPacket.getData(), 0, recvPacket.getLength()).trim();
                String serverAddress = recvPacket.getAddress().getHostAddress();
                int serverPort = recvPacket.getPort();

                logger.info("<<< Nhận phản hồi từ {}:{} ({}ms)", serverAddress, serverPort, responseTime);
                logger.debug("Nội dung phản hồi: '{}'", message);

                if (message.startsWith("ZALU_DISCOVERY_RESPONSE|")) {
                    String[] parts = message.split("\\|");
                    if (parts.length >= 2) {
                        int tcpPort = Integer.parseInt(parts[1]);
                        String address = recvPacket.getAddress().getHostAddress();

                        logger.info("=== ✓ TÌM THẤY SERVER ===");
                        logger.info("Server Address: {}", address);
                        logger.info("TCP Port: {}", tcpPort);
                        logger.info("Response Time: {}ms", responseTime);

                        return new ServerInfo(address, tcpPort);
                    } else {
                        logger.warn("⚠ Phản hồi không đúng định dạng: '{}'", message);
                    }
                } else {
                    logger.warn("⚠ Nhận packet không hợp lệ: '{}'", message);
                }
            } catch (SocketTimeoutException e) {
                logger.warn("=== ✗ KHÔNG TÌM THẤY SERVER ===");
                logger.warn("Không nhận được phản hồi sau {}ms", TIMEOUT_MS);
                logger.warn("Đảm bảo server đang chạy và cùng mạng LAN");
            }

        } catch (Exception e) {
            logger.error("=== ✗ LỖI KHI TÌM KIẾM SERVER ===");
            logger.error("Chi tiết: {}", e.getMessage(), e);
        } finally {
            if (socket != null && !socket.isClosed()) {
                socket.close();
                logger.debug("✓ UDP Socket đã đóng");
            }
        }

        logger.info("=== Kết thúc tìm kiếm Server ===");
        return null;
    }
}
