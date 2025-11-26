# 🔐 Hệ Thống Bảo Vệ License

## Tổng Quan

Hệ thống này đảm bảo code của bạn **CHỈ CHẠY** khi có License Server của bạn đang chạy. Khi đưa code cho đối tác mà họ không có License Server, code sẽ **KHÔNG CHẠY ĐƯỢC**.

## Cách Hoạt Động

1. **License Server** chạy trên máy của bạn (port 8888 mặc định)
2. Khi **ChatServer** hoặc **ZaluApplication** khởi động, chúng sẽ:
   - Kết nối tới License Server
   - Gửi license key để xác thực
   - Nếu không kết nối được hoặc license không hợp lệ → **THOÁT CHƯƠNG TRÌNH**

## Cấu Hình

### File `src/main/resources/license.properties`

```properties
# Địa chỉ License Server (máy của bạn)
license.server.url=localhost
license.server.port=8888

# License Key (phải khớp với License Server)
license.key=ZALU-2024-VALID
```

**LƯU Ý QUAN TRỌNG:**
- Khi đưa code cho đối tác, họ sẽ không có License Server chạy trên máy họ
- Code sẽ cố kết nối tới `localhost:8888` nhưng không tìm thấy → **KHÔNG CHẠY ĐƯỢC**
- Để code chạy được, họ cần có License Server của bạn chạy trên máy họ (điều này bạn không cung cấp)

## Cách Sử Dụng

### 1. Trên Máy Của Bạn (Có License Server)

1. Chạy ChatServer:
   ```bash
   mvn clean compile exec:java -Dexec.mainClass="org.example.zalu.server.ChatServer"
   ```
   - License Server sẽ tự động khởi động cùng ChatServer
   - Code sẽ chạy bình thường

2. Chạy Client:
   ```bash
   mvn clean javafx:run
   ```
   - Client sẽ kiểm tra license trước khi khởi động
   - Nếu License Server đang chạy → OK
   - Nếu không → Thoát

### 2. Trên Máy Đối Tác (Không Có License Server)

- Khi họ chạy code, sẽ thấy thông báo:
  ```
  ❌ CODE KHÔNG ĐƯỢC PHÉP CHẠY!
     Không thể xác thực license với License Server
     Code này chỉ hoạt động khi có License Server của bạn
  ```
- Code sẽ **THOÁT** ngay lập tức

## Tùy Chỉnh License Key

### Thay Đổi License Key

1. **Trên License Server** (`LicenseServer.java`):
   ```java
   private static final String VALID_LICENSE_KEY = "YOUR-CUSTOM-KEY";
   ```

2. **Trong file cấu hình** (`license.properties`):
   ```properties
   license.key=YOUR-CUSTOM-KEY
   ```

3. **Rebuild project**:
   ```bash
   mvn clean compile
   ```

### Thay Đổi Port License Server

1. **Trong file cấu hình** (`license.properties`):
   ```properties
   license.server.port=9999  # Port mới
   ```

2. **Rebuild project**

## Bảo Mật Thêm

### Để Tăng Cường Bảo Vệ:

1. **Mã hóa License Key**: Thay vì lưu plain text, mã hóa license key
2. **Thêm Hardware Fingerprinting**: Kiểm tra MAC address, CPU ID, etc.
3. **Periodic Validation**: Kiểm tra license định kỳ trong khi chạy
4. **Obfuscation**: Làm rối code để khó reverse engineer

### Ví Dụ: Thêm Periodic Validation

Trong `ZaluApplication` hoặc `ChatServer`, thêm thread kiểm tra định kỳ:

```java
// Kiểm tra license mỗi 5 phút
ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
scheduler.scheduleAtFixedRate(() -> {
    if (!LicenseValidator.revalidateLicense()) {
        logger.error("License không còn hợp lệ! Thoát ứng dụng...");
        Platform.exit();
    }
}, 5, 5, TimeUnit.MINUTES);
```

## Troubleshooting

### License Server không khởi động

- Kiểm tra port 8888 có bị chiếm không:
  ```bash
  # Windows
  netstat -ano | findstr :8888
  
  # Linux/Mac
  lsof -i :8888
  ```

### Client không kết nối được License Server

- Đảm bảo License Server đang chạy
- Kiểm tra firewall không chặn port 8888
- Kiểm tra `license.properties` có đúng cấu hình không

## Lưu Ý

⚠️ **QUAN TRỌNG**: 
- Hệ thống này **KHÔNG HOÀN TOÀN BẢO MẬT 100%**
- Người có kinh nghiệm vẫn có thể bypass bằng cách:
  - Reverse engineer code
  - Patch binary
  - Tạo fake License Server
- Đây là lớp bảo vệ cơ bản để ngăn người dùng thông thường
- Để bảo vệ mạnh hơn, cần kết hợp với obfuscation, encryption, và các kỹ thuật khác

## Cấu Trúc Files

```
src/main/java/org/example/zalu/
├── util/license/
│   └── LicenseValidator.java      # Class kiểm tra license
├── server/
│   ├── LicenseServer.java          # License Server
│   └── ChatServer.java             # Đã tích hợp license check
└── ZaluApplication.java            # Đã tích hợp license check

src/main/resources/
└── license.properties              # Cấu hình license
```

