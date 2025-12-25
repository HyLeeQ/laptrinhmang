# Troubleshooting: Connection Refused

## Lỗi

```
java.net.ConnectException: Connection refused: connect
```

## Nguyên nhân

Lỗi này xảy ra khi **client không thể kết nối đến server** vì:

1. ❌ **Server chưa khởi động**
2. ❌ **Server đang chạy trên port khác**
3. ❌ **Firewall đang chặn kết nối**
4. ❌ **Địa chỉ IP không đúng**

---

## Giải pháp

### 1. Kiểm tra Server có đang chạy không

#### Cách 1: Xem log server
Mở console của server và tìm dòng:
```
INFO - ✓ Server Discovery đang lắng nghe UDP trên port 8888
INFO - Server đang lắng nghe trên port 12345
```

Nếu **KHÔNG** thấy → Server chưa chạy → **Khởi động server**

#### Cách 2: Kiểm tra port
**Windows:**
```powershell
netstat -an | findstr 12345
```

**Linux/Mac:**
```bash
netstat -an | grep 12345
```

Nếu thấy:
```
TCP    0.0.0.0:12345    0.0.0.0:0    LISTENING
```
→ Server đang chạy ✅

Nếu **KHÔNG** thấy → Server chưa chạy → **Khởi động server**

---

### 2. Kiểm tra cấu hình Port

#### Server-side:
Xem file `ChatServer.java` hoặc log server để biết port:
```java
private static final int PORT = 12345; // Port server đang dùng
```

#### Client-side:
Xem file `src/main/resources/server.properties`:
```properties
server.address=localhost
server.port=12345
```

**Đảm bảo port giống nhau!**

---

### 3. Kiểm tra Firewall

#### Windows Firewall:

**Tắt tạm thời để test:**
1. Mở **Windows Defender Firewall**
2. Click **Turn Windows Defender Firewall on or off**
3. Chọn **Turn off** (cho Private và Public)
4. Test lại

**Hoặc thêm rule cho port:**
```powershell
# Chạy PowerShell as Administrator
New-NetFirewallRule -DisplayName "Zalu Server" -Direction Inbound -Protocol TCP -LocalPort 12345 -Action Allow
```

#### Linux Firewall (ufw):
```bash
sudo ufw allow 12345/tcp
```

---

### 4. Sử dụng UDP Auto-Discovery

**Cách tốt nhất**: Để UDP Auto-Discovery tự động tìm server!

#### Bước 1: Khởi động Server
```bash
# Server sẽ tự động bật UDP Discovery Listener
```

#### Bước 2: Khởi động Client
```bash
# Client sẽ tự động tìm server trong mạng LAN
```

#### Xem log client:
```
INFO - === Khởi động Auto-Discovery ===
INFO - === Bắt đầu tìm kiếm Server trong mạng LAN ===
INFO - >>> [Broadcast #1] Sent to 255.255.255.255:8888
INFO - === ✓ TÌM THẤY SERVER ===
INFO - Server Address: 192.168.1.50
INFO - TCP Port: 12345
```

Nếu thấy "✓ TÌM THẤY SERVER" → Client sẽ tự động kết nối ✅

---

### 5. Kết nối thủ công (nếu Auto-Discovery thất bại)

#### Tạo file `server.properties`:

**Vị trí**: `src/main/resources/server.properties`

**Nội dung**:
```properties
# Địa chỉ server (IP hoặc hostname)
server.address=192.168.1.50

# Port server đang lắng nghe
server.port=12345
```

**Lưu ý**: Thay `192.168.1.50` bằng IP thực của server

#### Cách lấy IP server:

**Windows:**
```powershell
ipconfig
```
Tìm dòng `IPv4 Address`

**Linux/Mac:**
```bash
ifconfig
# hoặc
ip addr show
```

---

### 6. Test kết nối thủ công

#### Dùng Telnet:

**Windows:**
```powershell
# Bật Telnet client trước (nếu chưa có)
# Control Panel > Programs > Turn Windows features on or off > Telnet Client

telnet 192.168.1.50 12345
```

**Linux/Mac:**
```bash
telnet 192.168.1.50 12345
```

Nếu kết nối thành công → Thấy màn hình trống (nhấn Ctrl+] để thoát)  
Nếu thất bại → Thấy "Connection refused" hoặc timeout

#### Dùng netcat (nc):
```bash
nc -zv 192.168.1.50 12345
```

Nếu thành công:
```
Connection to 192.168.1.50 12345 port [tcp/*] succeeded!
```

---

## Quy trình Debug từng bước

### Bước 1: Kiểm tra Server
```bash
# 1. Khởi động server
# 2. Xem log server
# 3. Tìm dòng: "Server đang lắng nghe trên port 12345"
```

### Bước 2: Kiểm tra Port
```bash
# Windows:
netstat -an | findstr 12345

# Linux/Mac:
netstat -an | grep 12345
```

### Bước 3: Test kết nối
```bash
telnet localhost 12345
# hoặc
nc -zv localhost 12345
```

### Bước 4: Kiểm tra Firewall
```bash
# Tắt firewall tạm thời
# Test lại
```

### Bước 5: Kiểm tra cấu hình
```bash
# Xem server.properties
# Đảm bảo IP và port đúng
```

### Bước 6: Xem log chi tiết
```bash
# Bật DEBUG mode trong logback.xml
# Xem log client và server
```

---

## Log Messages

### ✅ Kết nối thành công:
```
INFO - === Đang kết nối đến Server ===
INFO - Server Address: 192.168.1.50
INFO - Server Port: 12345
DEBUG - Đang tạo socket connection...
DEBUG - Đang cấu hình socket options...
DEBUG - Đang khởi tạo ObjectOutputStream...
DEBUG - Đang khởi tạo ObjectInputStream...
INFO - === ✓ Kết nối thành công ===
INFO - Connected to: 192.168.1.50:12345
```

### ❌ Connection Refused:
```
INFO - === Đang kết nối đến Server ===
INFO - Server Address: localhost
INFO - Server Port: 12345
ERROR - === ✗ Kết nối thất bại ===
ERROR - Lỗi: Connection refused
ERROR - Server: localhost:12345
ERROR - Nguyên nhân có thể:
ERROR -   1. Server chưa khởi động
ERROR -   2. Server đang chạy trên port khác
ERROR -   3. Firewall đang chặn kết nối
ERROR -   4. Địa chỉ IP không đúng
```

### ❌ Unknown Host:
```
ERROR - === ✗ Kết nối thất bại ===
ERROR - Lỗi: Unknown host
ERROR - Server: invalid-hostname
ERROR - Không thể resolve địa chỉ server
```

---

## Best Practices

### 1. Luôn dùng UDP Auto-Discovery
- ✅ Tự động tìm server
- ✅ Không cần cấu hình IP thủ công
- ✅ Hoạt động trong mạng LAN

### 2. Backup: Cấu hình thủ công
- Tạo file `server.properties`
- Điền IP và port chính xác

### 3. Kiểm tra log
- Bật DEBUG mode khi cần
- Xem log server và client

### 4. Test kết nối
- Dùng telnet hoặc netcat
- Kiểm tra firewall

---

## FAQ

### Q: Tại sao client không tự động kết nối?

**A**: Từ phiên bản mới, client **KHÔNG** kết nối ngay khi khởi động. Thay vào đó:
1. UDP Auto-Discovery tìm server
2. Client kết nối khi login

### Q: Làm sao biết UDP Discovery có hoạt động không?

**A**: Xem log client:
```
INFO - === Khởi động Auto-Discovery ===
INFO - === ✓ TÌM THẤY SERVER ===
```

### Q: UDP Discovery không tìm thấy server?

**A**: Kiểm tra:
1. Server có đang chạy không?
2. Firewall có block UDP port 8888 không?
3. Client và server có cùng mạng LAN không?

### Q: Muốn kết nối đến server ở mạng khác?

**A**: UDP Discovery chỉ hoạt động trong LAN. Để kết nối qua Internet:
1. Tạo file `server.properties`
2. Điền IP public của server
3. Forward port trên router

---

## Liên hệ

Nếu vẫn gặp vấn đề:
1. Xem log chi tiết (DEBUG mode)
2. Kiểm tra tất cả các bước trên
3. Tham khảo tài liệu UDP Discovery

---

**Cập nhật**: 2025-12-25  
**Phiên bản**: 1.0
