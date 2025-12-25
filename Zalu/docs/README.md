# Zalu Documentation

Chào mừng đến với tài liệu dự án Zalu!

---

## 📚 Tài liệu UDP Auto-Discovery

### 🚀 Bắt đầu nhanh (5 phút)
- **[UDP_AUTO_DISCOVERY.md](UDP_AUTO_DISCOVERY.md)** - Hướng dẫn nhanh về tính năng tự động tìm server
- **[UDP_DISCOVERY_SUMMARY.md](UDP_DISCOVERY_SUMMARY.md)** - Tóm tắt toàn bộ tính năng và cách sử dụng

### 📖 Tài liệu chi tiết (15-30 phút)
- **[UDP_DISCOVERY_LOGGING.md](UDP_DISCOVERY_LOGGING.md)** - Tài liệu chi tiết về logging cho UDP Discovery
- **[UDP_DISCOVERY_LOG_CONFIG.md](UDP_DISCOVERY_LOG_CONFIG.md)** - Hướng dẫn cấu hình log level
- **[UDP_DISCOVERY_LOG_EXAMPLES.md](UDP_DISCOVERY_LOG_EXAMPLES.md)** - Ví dụ log output cho các kịch bản khác nhau

### 📝 Changelog & History
- **[CHANGELOG_UDP_DISCOVERY.md](CHANGELOG_UDP_DISCOVERY.md)** - Tóm tắt các thay đổi đã thực hiện

---

## 🎯 Tính năng UDP Auto-Discovery

### Mô tả
Tự động tìm kiếm và kết nối đến server trong mạng LAN mà không cần nhập địa chỉ IP thủ công.

### Cách hoạt động
1. Client gửi broadcast UDP "ZALU_DISCOVERY_REQUEST"
2. Server nhận và phản hồi với thông tin TCP port
3. Client tự động kết nối đến server

### Log quan trọng

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
```

---

## 🔧 Cấu hình Log

### Production (Mặc định):
```xml
<logger name="org.example.zalu.server.ServerDiscoveryListener" level="INFO"/>
<logger name="org.example.zalu.client.ServerDiscoveryClient" level="INFO"/>
```

### Debug Mode:
```xml
<logger name="org.example.zalu.server.ServerDiscoveryListener" level="DEBUG"/>
<logger name="org.example.zalu.client.ServerDiscoveryClient" level="DEBUG"/>
```

**File cấu hình**: `src/main/resources/logback.xml`

---

## 🐛 Troubleshooting

### Vấn đề: Connection Refused

**Lỗi**:
```
java.net.ConnectException: Connection refused: connect
```

**Nguyên nhân**: Server chưa khởi động hoặc cấu hình sai

**Giải pháp**: 👉 **[TROUBLESHOOTING_CONNECTION_REFUSED.md](TROUBLESHOOTING_CONNECTION_REFUSED.md)**

---

### Vấn đề: Không tìm thấy server

**Kiểm tra**:
1. ✅ Server có đang chạy không?
2. ✅ Firewall có block UDP port 8888 không?
3. ✅ Client và server có cùng mạng LAN không?

**Xem chi tiết**: [UDP_AUTO_DISCOVERY.md](UDP_AUTO_DISCOVERY.md#troubleshooting)

---

## 📊 Performance

- **Thời gian tìm kiếm thành công**: 50-200ms
- **Timeout**: 3000ms (3 giây)
- **Số broadcast packets**: 2-5 (tùy số network interfaces)

---

## 📖 Hướng dẫn đọc tài liệu

### Nếu bạn là Developer mới:
1. Đọc [UDP_DISCOVERY_SUMMARY.md](UDP_DISCOVERY_SUMMARY.md) - Hiểu tổng quan
2. Đọc [UDP_AUTO_DISCOVERY.md](UDP_AUTO_DISCOVERY.md) - Biết cách sử dụng
3. Xem [UDP_DISCOVERY_LOG_EXAMPLES.md](UDP_DISCOVERY_LOG_EXAMPLES.md) - Xem ví dụ log

### Nếu bạn cần debug:
1. Đọc [UDP_DISCOVERY_LOG_CONFIG.md](UDP_DISCOVERY_LOG_CONFIG.md) - Bật DEBUG mode
2. Xem [UDP_DISCOVERY_LOG_EXAMPLES.md](UDP_DISCOVERY_LOG_EXAMPLES.md) - So sánh log
3. Đọc [UDP_DISCOVERY_LOGGING.md](UDP_DISCOVERY_LOGGING.md) - Hiểu chi tiết

### Nếu bạn cần biết lịch sử thay đổi:
1. Đọc [CHANGELOG_UDP_DISCOVERY.md](CHANGELOG_UDP_DISCOVERY.md)

---

## 🔗 Links hữu ích

| Tài liệu | Mục đích | Thời gian đọc |
|----------|----------|---------------|
| [UDP_DISCOVERY_SUMMARY.md](UDP_DISCOVERY_SUMMARY.md) | Tóm tắt toàn bộ | 5 phút |
| [UDP_AUTO_DISCOVERY.md](UDP_AUTO_DISCOVERY.md) | Hướng dẫn nhanh | 5 phút |
| [UDP_DISCOVERY_LOG_CONFIG.md](UDP_DISCOVERY_LOG_CONFIG.md) | Cấu hình log | 5 phút |
| [UDP_DISCOVERY_LOG_EXAMPLES.md](UDP_DISCOVERY_LOG_EXAMPLES.md) | Ví dụ log | 10 phút |
| [UDP_DISCOVERY_LOGGING.md](UDP_DISCOVERY_LOGGING.md) | Tài liệu chi tiết | 15 phút |
| [CHANGELOG_UDP_DISCOVERY.md](CHANGELOG_UDP_DISCOVERY.md) | Changelog | 5 phút |

---

## 📞 Hỗ trợ

Nếu gặp vấn đề, vui lòng:
1. Kiểm tra log trong `logs/zalu.log`
2. Bật DEBUG mode để xem chi tiết
3. Tham khảo phần Troubleshooting trong tài liệu

---

**Cập nhật lần cuối**: 2025-12-25  
**Phiên bản**: 1.0
