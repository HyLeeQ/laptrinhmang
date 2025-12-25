# Cấu hình Log Level cho UDP Discovery

## Cách sử dụng

Mở file `src/main/resources/logback.xml` và thay đổi log level theo nhu cầu.

---

## 1. Production Mode (Mặc định - INFO)

**Mục đích**: Chỉ xem log quan trọng, giảm log spam

```xml
<logger name="org.example.zalu.server.ServerDiscoveryListener" level="INFO"/>
<logger name="org.example.zalu.client.ServerDiscoveryClient" level="INFO"/>
<logger name="org.example.zalu.controller.auth.LoginController" level="INFO"/>
```

**Log sẽ thấy**:
- ✅ Server khởi động/dừng
- ✅ Nhận/gửi discovery requests
- ✅ Kết quả tìm kiếm server
- ❌ KHÔNG thấy chi tiết từng packet
- ❌ KHÔNG thấy chi tiết network interfaces

---

## 2. Debug Mode (DEBUG)

**Mục đích**: Xem tất cả chi tiết để debug khi có vấn đề

```xml
<logger name="org.example.zalu.server.ServerDiscoveryListener" level="DEBUG"/>
<logger name="org.example.zalu.client.ServerDiscoveryClient" level="DEBUG"/>
<logger name="org.example.zalu.controller.auth.LoginController" level="DEBUG"/>
```

**Log sẽ thấy**:
- ✅ TẤT CẢ log từ INFO mode
- ✅ Chi tiết từng packet nhận/gửi
- ✅ Chi tiết từng network interface được quét
- ✅ Broadcast addresses của từng interface
- ✅ Nội dung packet (request/response)

---

## 3. Silent Mode (WARN)

**Mục đích**: Chỉ xem cảnh báo và lỗi, ẩn tất cả log bình thường

```xml
<logger name="org.example.zalu.server.ServerDiscoveryListener" level="WARN"/>
<logger name="org.example.zalu.client.ServerDiscoveryClient" level="WARN"/>
<logger name="org.example.zalu.controller.auth.LoginController" level="WARN"/>
```

**Log sẽ thấy**:
- ✅ Chỉ cảnh báo và lỗi
- ❌ KHÔNG thấy log bình thường

---

## 4. Debug chỉ Server

**Mục đích**: Debug server-side, giữ client ở INFO

```xml
<logger name="org.example.zalu.server.ServerDiscoveryListener" level="DEBUG"/>
<logger name="org.example.zalu.client.ServerDiscoveryClient" level="INFO"/>
<logger name="org.example.zalu.controller.auth.LoginController" level="INFO"/>
```

---

## 5. Debug chỉ Client

**Mục đích**: Debug client-side, giữ server ở INFO

```xml
<logger name="org.example.zalu.server.ServerDiscoveryListener" level="INFO"/>
<logger name="org.example.zalu.client.ServerDiscoveryClient" level="DEBUG"/>
<logger name="org.example.zalu.controller.auth.LoginController" level="DEBUG"/>
```

---

## Ví dụ Log Output

### INFO Level (Production):

```
2025-12-25 00:00:00.123 [Discovery-Listener] INFO  o.e.z.s.ServerDiscoveryListener - === UDP Discovery Listener Starting ===
2025-12-25 00:00:00.124 [Discovery-Listener] INFO  o.e.z.s.ServerDiscoveryListener - ✓ Server Discovery đang lắng nghe UDP trên port 8888
2025-12-25 00:00:05.456 [Discovery-Listener] INFO  o.e.z.s.ServerDiscoveryListener - >>> [Request #1] Discovery request từ 192.168.1.100:54321
2025-12-25 00:00:05.457 [Discovery-Listener] INFO  o.e.z.s.ServerDiscoveryListener - <<< [Response #1] Đã gửi response tới 192.168.1.100:54321 - TCP Port: 12345
```

### DEBUG Level (Development):

```
2025-12-25 00:00:00.123 [Discovery-Listener] INFO  o.e.z.s.ServerDiscoveryListener - === UDP Discovery Listener Starting ===
2025-12-25 00:00:00.124 [Discovery-Listener] INFO  o.e.z.s.ServerDiscoveryListener - ✓ Server Discovery đang lắng nghe UDP trên port 8888
2025-12-25 00:00:05.450 [Discovery-Listener] DEBUG o.e.z.s.ServerDiscoveryListener - Đang chờ nhận UDP packet...
2025-12-25 00:00:05.455 [Discovery-Listener] DEBUG o.e.z.s.ServerDiscoveryListener - Nhận packet từ 192.168.1.100:54321 - Nội dung: 'ZALU_DISCOVERY_REQUEST'
2025-12-25 00:00:05.456 [Discovery-Listener] INFO  o.e.z.s.ServerDiscoveryListener - >>> [Request #1] Discovery request từ 192.168.1.100:54321
2025-12-25 00:00:05.457 [Discovery-Listener] INFO  o.e.z.s.ServerDiscoveryListener - <<< [Response #1] Đã gửi response tới 192.168.1.100:54321 - TCP Port: 12345
```

---

## Lưu ý

1. **Thay đổi log level không cần restart ứng dụng** (nếu dùng logback auto-reload)
2. **DEBUG mode sẽ tạo nhiều log** - chỉ dùng khi cần debug
3. **Production nên dùng INFO hoặc WARN** để tiết kiệm tài nguyên
4. **Log file được lưu tại**: `logs/zalu.log` và `logs/zalu-error.log`

---

## Troubleshooting

### Không thấy log UDP Discovery?

**Kiểm tra**:
1. File `logback.xml` có cấu hình đúng không?
2. Log level có đúng không? (INFO hoặc DEBUG)
3. Ứng dụng có đang chạy không?

### Log quá nhiều?

**Giải pháp**: Đổi sang WARN hoặc ERROR

```xml
<logger name="org.example.zalu.server.ServerDiscoveryListener" level="WARN"/>
<logger name="org.example.zalu.client.ServerDiscoveryClient" level="WARN"/>
```

### Muốn lưu UDP Discovery log riêng?

**Thêm appender riêng**:

```xml
<!-- UDP Discovery File Appender -->
<appender name="UDP_DISCOVERY_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
    <file>logs/udp-discovery.log</file>
    <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
        <fileNamePattern>logs/udp-discovery.%d{yyyy-MM-dd}.log</fileNamePattern>
        <maxHistory>7</maxHistory>
    </rollingPolicy>
    <encoder>
        <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
    </encoder>
</appender>

<!-- Áp dụng cho UDP Discovery loggers -->
<logger name="org.example.zalu.server.ServerDiscoveryListener" level="DEBUG" additivity="false">
    <appender-ref ref="UDP_DISCOVERY_FILE"/>
    <appender-ref ref="CONSOLE"/>
</logger>

<logger name="org.example.zalu.client.ServerDiscoveryClient" level="DEBUG" additivity="false">
    <appender-ref ref="UDP_DISCOVERY_FILE"/>
    <appender-ref ref="CONSOLE"/>
</logger>
```

---

**Xem thêm**: [UDP_DISCOVERY_LOGGING.md](UDP_DISCOVERY_LOGGING.md) để biết chi tiết về các log messages
