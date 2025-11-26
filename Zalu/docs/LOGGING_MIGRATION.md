# 🔄 Hướng dẫn Migration từ System.out.println sang Logger

## ✅ Đã hoàn thành

- ✅ Upgrade từ `slf4j-simple` lên `logback-classic` (tốt hơn)
- ✅ Tạo `logback.xml` config file
- ✅ Thay thế trong `ChatServer.java`
- ✅ Thay thế trong `ZaluApplication.java`

## 📋 Cách thay thế

### 1. Thêm import

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
```

### 2. Tạo logger instance

```java
private static final Logger logger = LoggerFactory.getLogger(ClassName.class);
```

### 3. Thay thế các câu lệnh

| System.out.println | → | logger.info() |
|-------------------|----|---------------|
| System.err.println | → | logger.error() |
| printStackTrace() | → | logger.error("message", e) |

### Ví dụ:

**Trước:**
```java
System.out.println("User logged in: " + username);
System.err.println("Error: " + e.getMessage());
e.printStackTrace();
```

**Sau:**
```java
logger.info("User logged in: {}", username);
logger.error("Error: {}", e.getMessage(), e);
logger.error("Error occurred", e);  // Với exception
```

## 📝 Log Levels

- **TRACE**: Chi tiết nhất (debugging sâu)
- **DEBUG**: Thông tin debug (queries, flow)
- **INFO**: Thông tin chung (startup, connections)
- **WARN**: Cảnh báo (retry, fallback)
- **ERROR**: Lỗi (exceptions, failures)

## 🎯 Ưu tiên thay thế

### Ưu tiên cao (quan trọng):
1. ✅ `ChatServer.java` - Đã xong
2. ✅ `ZaluApplication.java` - Đã xong
3. ⏳ `ClientHandler.java` - Server-side request handling
4. ⏳ `ChatClient.java` - Client networking
5. ⏳ `ChatEventManager.java` - Event processing

### Ưu tiên trung bình:
6. `MessageDAO.java` - Database operations
7. `UserDAO.java` - Authentication
8. `FriendDAO.java` - Friend management
9. `GroupDAO.java` - Group management

### Ưu tiên thấp:
10. Controllers - UI logging
11. Services - Business logic
12. Utils - Helper functions

## 🔍 Tìm kiếm System.out.println

```bash
# Tìm tất cả System.out.println
grep -r "System.out.println" src/main/java

# Tìm System.err.println
grep -r "System.err.println" src/main/java

# Tìm printStackTrace
grep -r "printStackTrace" src/main/java
```

## 📊 Log Files

Sau khi setup, logs sẽ được lưu vào:
- `logs/zalu.log` - Tất cả logs
- `logs/zalu-error.log` - Chỉ ERROR logs
- Logs được rotate hàng ngày
- Giữ 30 ngày, tối đa 1GB

## ⚙️ Cấu hình Logback

File: `src/main/resources/logback.xml`

Có thể điều chỉnh:
- Log levels cho từng package
- Format của log messages
- File locations
- Rotation policy

## 🚀 Lợi ích

1. **Production-ready**: Logs vào file, không chỉ console
2. **Log levels**: Dễ filter và debug
3. **Performance**: Logback nhanh hơn System.out
4. **Rotation**: Tự động rotate logs
5. **Structured**: Dễ parse và analyze

## 📚 Tài liệu tham khảo

- [SLF4J Manual](http://www.slf4j.org/manual.html)
- [Logback Documentation](http://logback.qos.ch/documentation.html)

