# UDP Auto-Discovery - Hướng dẫn nhanh

## Tính năng

Tự động tìm kiếm và kết nối đến server trong mạng LAN mà không cần nhập IP thủ công.

## Cách hoạt động

1. **Client** gửi broadcast UDP "ZALU_DISCOVERY_REQUEST" trên port 8888
2. **Server** nhận request và phản hồi "ZALU_DISCOVERY_RESPONSE|{TCP_PORT}"
3. **Client** nhận response và tự động kết nối đến server

## Xem Log

### Bật DEBUG mode để xem chi tiết:

**File**: `src/main/resources/logback.xml`

```xml
<logger name="org.example.zalu.server.ServerDiscoveryListener" level="DEBUG"/>
<logger name="org.example.zalu.client.ServerDiscoveryClient" level="DEBUG"/>
<logger name="org.example.zalu.controller.auth.LoginController" level="DEBUG"/>
```

### Log quan trọng cần chú ý:

#### ✅ Server khởi động thành công:
```
INFO - ✓ Server Discovery đang lắng nghe UDP trên port 8888
```

#### ✅ Client tìm thấy server:
```
INFO - === ✓ TÌM THẤY SERVER ===
INFO - Server Address: 192.168.1.50
INFO - TCP Port: 12345
```

#### ❌ Client không tìm thấy server:
```
WARN - === ✗ KHÔNG TÌM THẤY SERVER ===
WARN - Không nhận được phản hồi sau 3000ms
```

## Troubleshooting

### Vấn đề: Server không khởi động được

**Log:**
```
ERROR - ✗ Không thể bind port discovery 8888
```

**Giải pháp:** Port 8888 đã được sử dụng. Tắt instance server khác.

---

### Vấn đề: Client không tìm thấy server

**Kiểm tra:**

1. Server có đang chạy không?
   ```
   # Tìm log này trong server console:
   INFO - ✓ Server Discovery đang lắng nghe UDP trên port 8888
   ```

2. Firewall có block UDP port 8888 không?
   - Windows: Tắt Windows Defender Firewall tạm thời để test
   - Linux: `sudo ufw allow 8888/udp`

3. Client và server có cùng mạng LAN không?
   - Kiểm tra IP: `ipconfig` (Windows) hoặc `ifconfig` (Linux/Mac)
   - Đảm bảo cùng subnet (ví dụ: 192.168.1.x)

4. Xem log chi tiết của client:
   ```
   INFO - Đang quét các network interfaces...
   INFO - [Interface #1] Wi-Fi - ACTIVE
   INFO - >>> [Broadcast #2] Sent to 192.168.1.255 (Wi-Fi)
   ```

---

### Vấn đề: Timeout khi tìm server

**Log:**
```
WARN - Không nhận được phản hồi sau 3000ms
```

**Nguyên nhân:**
- Server không chạy
- Firewall block
- Không cùng mạng

**Giải pháp:**
1. Kiểm tra server đang chạy
2. Tắt firewall để test
3. Ping server để kiểm tra kết nối: `ping <server_ip>`

---

## Test thủ công

### Test Server (trên máy server):

```bash
# Kiểm tra port 8888 có đang lắng nghe không
netstat -an | findstr 8888
```

### Test Client (trên máy client):

```bash
# Gửi test packet đến server
# (Cần cài đặt netcat hoặc công cụ tương tự)
echo "ZALU_DISCOVERY_REQUEST" | nc -u -w1 <server_ip> 8888
```

---

## Cấu hình

### Thay đổi Discovery Port:

**File**: `ServerDiscoveryListener.java` và `ServerDiscoveryClient.java`

```java
private static final int DISCOVERY_PORT = 8888; // Đổi thành port khác
```

### Thay đổi Timeout:

**File**: `ServerDiscoveryClient.java`

```java
private static final int TIMEOUT_MS = 3000; // Đổi thành thời gian khác (ms)
```

---

## Xem thêm

Tài liệu chi tiết: [UDP_DISCOVERY_LOGGING.md](UDP_DISCOVERY_LOGGING.md)

---

**Lưu ý:** Tính năng này chỉ hoạt động trong mạng LAN. Không hoạt động qua Internet.
