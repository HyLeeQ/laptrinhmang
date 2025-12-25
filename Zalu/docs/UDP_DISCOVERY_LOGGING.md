# UDP Discovery Logging - Tài liệu

## Tổng quan

Tính năng **UDP Discovery** cho phép client tự động tìm kiếm và kết nối đến server trong mạng LAN mà không cần nhập địa chỉ IP thủ công. Tài liệu này mô tả chi tiết về logging đã được thêm vào để theo dõi quá trình này.

## Kiến trúc

### 1. Server-side: `ServerDiscoveryListener.java`

**Chức năng**: Lắng nghe các gói tin UDP broadcast từ clients và phản hồi lại với thông tin server.

**Port**: 8888 (UDP)

**Luồng hoạt động**:
1. Server khởi động và bind UDP socket trên port 8888
2. Lắng nghe các gói tin broadcast với nội dung "ZALU_DISCOVERY_REQUEST"
3. Khi nhận được request, gửi lại response: "ZALU_DISCOVERY_RESPONSE|{TCP_PORT}"
4. Client nhận response và lấy IP từ địa chỉ nguồn của packet

### 2. Client-side: `ServerDiscoveryClient.java`

**Chức năng**: Gửi broadcast requests để tìm server trong mạng LAN.

**Timeout**: 3000ms (3 giây)

**Luồng hoạt động**:
1. Tạo UDP socket với broadcast mode
2. Gửi broadcast "ZALU_DISCOVERY_REQUEST" đến:
   - 255.255.255.255 (global broadcast)
   - Tất cả broadcast addresses của các network interfaces (WiFi, LAN, etc.)
3. Chờ nhận response trong 3 giây
4. Parse response để lấy server address và TCP port
5. Trả về `ServerInfo` hoặc `null` nếu không tìm thấy

### 3. Integration: `LoginController.java`

**Chức năng**: Tự động gọi UDP discovery khi màn hình login khởi động.

**Luồng hoạt động**:
1. Khi `initialize()` được gọi, tự động chạy `findServerInBackground()`
2. Chạy discovery trong background thread để không block UI
3. Nếu tìm thấy server: cấu hình `ChatClient` với địa chỉ tìm được
4. Nếu không tìm thấy: sử dụng localhost mặc định

## Log Messages Chi Tiết

### Server Logs (ServerDiscoveryListener)

#### Khởi động thành công:
```
INFO  - === UDP Discovery Listener Starting ===
INFO  - TCP Port to advertise: 12345
INFO  - UDP Discovery Port: 8888
INFO  - ✓ Server Discovery đang lắng nghe UDP trên port 8888
INFO  - ✓ Broadcast mode: ENABLED
INFO  - ✓ Sẵn sàng nhận discovery requests từ clients...
```

#### Nhận và xử lý request:
```
DEBUG - Đang chờ nhận UDP packet...
DEBUG - Nhận packet từ 192.168.1.100:54321 - Nội dung: 'ZALU_DISCOVERY_REQUEST'
INFO  - >>> [Request #1] Discovery request từ 192.168.1.100:54321
INFO  - <<< [Response #1] Đã gửi response tới 192.168.1.100:54321 - TCP Port: 12345
```

#### Nhận packet không hợp lệ:
```
WARN  - ⚠ Nhận packet không hợp lệ từ 192.168.1.100:54321 - Nội dung: 'INVALID_MESSAGE'
```

#### Lỗi bind port:
```
ERROR - ✗ Không thể bind port discovery 8888. Có thể một instance server khác đang chạy.
ERROR - Chi tiết lỗi: Address already in use
```

#### Dừng listener:
```
INFO  - === Đang dừng UDP Discovery Listener ===
INFO  - ✓ UDP Socket đã đóng từ stopListener()
INFO  - === UDP Discovery Listener đã dừng ===
INFO  - === UDP Discovery Listener Stopped ===
INFO  - Total requests processed: 5
```

### Client Logs (ServerDiscoveryClient)

#### Bắt đầu tìm kiếm:
```
INFO  - === Bắt đầu tìm kiếm Server trong mạng LAN ===
INFO  - Discovery Port: 8888
INFO  - Timeout: 3000ms
INFO  - ✓ UDP Socket đã khởi tạo
INFO  - ✓ Broadcast mode: ENABLED
INFO  - ✓ Timeout: 3000ms
```

#### Gửi broadcast packets:
```
INFO  - >>> [Broadcast #1] Sent to 255.255.255.255:8888
INFO  - Đang quét các network interfaces...
INFO  - [Interface #1] Wi-Fi - ACTIVE
INFO  -     >>> [Broadcast #2] Sent to 192.168.1.255 (Wi-Fi)
INFO  - [Interface #2] Ethernet - ACTIVE
INFO  -     >>> [Broadcast #3] Sent to 192.168.0.255 (Ethernet)
DEBUG - [Interface #3] Loopback Pseudo-Interface 1 - SKIPPED (loopback)
INFO  - ✓ Đã quét 3 network interfaces
INFO  - === Tổng số broadcast packets gửi đi: 3 ===
INFO  - Đang chờ nhận phản hồi từ server (timeout: 3000ms)...
```

#### Tìm thấy server:
```
INFO  - <<< Nhận phản hồi từ 192.168.1.50:8888 (125ms)
DEBUG - Nội dung phản hồi: 'ZALU_DISCOVERY_RESPONSE|12345'
INFO  - === ✓ TÌM THẤY SERVER ===
INFO  - Server Address: 192.168.1.50
INFO  - TCP Port: 12345
INFO  - Response Time: 125ms
DEBUG - ✓ UDP Socket đã đóng
INFO  - === Kết thúc tìm kiếm Server ===
```

#### Không tìm thấy server:
```
WARN  - === ✗ KHÔNG TÌM THẤY SERVER ===
WARN  - Không nhận được phản hồi sau 3000ms
WARN  - Đảm bảo server đang chạy và cùng mạng LAN
DEBUG - ✓ UDP Socket đã đóng
INFO  - === Kết thúc tìm kiếm Server ===
```

#### Lỗi khi tìm kiếm:
```
ERROR - === ✗ LỖI KHI TÌM KIẾM SERVER ===
ERROR - Chi tiết: Network is unreachable
```

### LoginController Logs

#### Khởi động auto-discovery:
```
INFO  - === Khởi động Auto-Discovery ===
INFO  - Bắt đầu quá trình tìm kiếm server tự động...
```

#### Thành công:
```
INFO  - === ✓ Auto-Discovery Thành Công ===
INFO  - Server được tìm thấy: 192.168.1.50:12345
INFO  - Thời gian tìm kiếm: 150ms
INFO  - Đã cấu hình ChatClient với server: 192.168.1.50:12345
INFO  - === Kết thúc Auto-Discovery ===
```

#### Thất bại:
```
WARN  - === ✗ Auto-Discovery Thất Bại ===
WARN  - Không tìm thấy server sau 3050ms
WARN  - Sẽ sử dụng cấu hình mặc định (localhost)
INFO  - === Kết thúc Auto-Discovery ===
```

## Cấu hình Log Level

### Để xem tất cả log chi tiết (bao gồm DEBUG):

Trong file `logback.xml` hoặc `application.properties`:

```xml
<!-- logback.xml -->
<logger name="org.example.zalu.server.ServerDiscoveryListener" level="DEBUG"/>
<logger name="org.example.zalu.client.ServerDiscoveryClient" level="DEBUG"/>
<logger name="org.example.zalu.controller.auth.LoginController" level="DEBUG"/>
```

hoặc

```properties
# application.properties
logging.level.org.example.zalu.server.ServerDiscoveryListener=DEBUG
logging.level.org.example.zalu.client.ServerDiscoveryClient=DEBUG
logging.level.org.example.zalu.controller.auth.LoginController=DEBUG
```

### Chỉ xem log quan trọng (INFO trở lên):

```xml
<!-- logback.xml -->
<logger name="org.example.zalu.server.ServerDiscoveryListener" level="INFO"/>
<logger name="org.example.zalu.client.ServerDiscoveryClient" level="INFO"/>
<logger name="org.example.zalu.controller.auth.LoginController" level="INFO"/>
```

## Troubleshooting

### 1. Server không nhận được discovery requests

**Kiểm tra log server:**
```
INFO  - ✓ Server Discovery đang lắng nghe UDP trên port 8888
```

Nếu thấy:
```
ERROR - ✗ Không thể bind port discovery 8888
```

**Giải pháp**: Port 8888 đã được sử dụng. Tắt instance server khác hoặc đổi port.

### 2. Client không tìm thấy server

**Kiểm tra log client:**
```
INFO  - === Tổng số broadcast packets gửi đi: 0 ===
```

**Nguyên nhân**: Không có network interface nào active hoặc không có broadcast address.

**Giải pháp**: 
- Kiểm tra kết nối mạng
- Đảm bảo WiFi/LAN đang bật
- Kiểm tra firewall có block UDP port 8888 không

### 3. Timeout khi tìm server

**Log client:**
```
WARN  - Không nhận được phản hồi sau 3000ms
```

**Nguyên nhân**:
- Server không chạy
- Firewall block UDP traffic
- Client và server không cùng subnet

**Giải pháp**:
- Kiểm tra server đang chạy
- Tắt firewall tạm thời để test
- Đảm bảo cùng mạng LAN

### 4. Nhận packet không hợp lệ

**Log server:**
```
WARN  - ⚠ Nhận packet không hợp lệ từ 192.168.1.100:54321
```

**Nguyên nhân**: Có ứng dụng khác gửi broadcast trên port 8888

**Giải pháp**: Bỏ qua, không ảnh hưởng đến hoạt động

## Performance Metrics

### Thời gian tìm kiếm thông thường:

- **Thành công**: 50-200ms
- **Thất bại (timeout)**: 3000ms

### Số lượng broadcast packets:

- **Minimum**: 1 (chỉ global broadcast)
- **Typical**: 2-5 (tùy số network interfaces)
- **Maximum**: 10+ (nhiều network interfaces)

## Best Practices

1. **Luôn kiểm tra log khi debug kết nối**
   - Xem log server để đảm bảo listener đang chạy
   - Xem log client để biết quá trình broadcast

2. **Sử dụng DEBUG level khi phát triển**
   - Thấy chi tiết từng packet
   - Thấy từng network interface được quét

3. **Sử dụng INFO level trong production**
   - Giảm log spam
   - Vẫn thấy được kết quả quan trọng

4. **Monitor response time**
   - Nếu > 500ms: có thể có vấn đề về network
   - Nếu timeout thường xuyên: kiểm tra firewall/network config

## Tích hợp với Monitoring

Các log này có thể được tích hợp với các hệ thống monitoring như:

- **ELK Stack** (Elasticsearch, Logstash, Kibana)
- **Splunk**
- **Grafana + Loki**

Để theo dõi:
- Số lượng discovery requests/responses
- Response time trung bình
- Tỷ lệ thành công/thất bại
- Network interfaces được sử dụng

## Kết luận

Với logging chi tiết này, bạn có thể:
- ✅ Theo dõi quá trình auto-discovery từ đầu đến cuối
- ✅ Debug nhanh chóng khi có vấn đề kết nối
- ✅ Monitor performance của UDP discovery
- ✅ Phát hiện các vấn đề về network/firewall
- ✅ Tối ưu hóa quá trình tìm kiếm server

---

**Ngày tạo**: 2025-12-25  
**Phiên bản**: 1.0  
**Tác giả**: Zalu Development Team
