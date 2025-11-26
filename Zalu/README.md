# Zalu - Ứng dụng Chat Real-time

Ứng dụng chat real-time được xây dựng bằng JavaFX, hỗ trợ chat 1-1, nhóm, gửi file, voice message và nhiều tính năng khác.

## 📋 Mục Lục

- [Tính năng](#tính-năng)
- [Yêu cầu hệ thống](#yêu-cầu-hệ-thống)
- [Cài đặt](#cài-đặt)
- [Cấu hình](#cấu-hình)
- [Chạy ứng dụng](#chạy-ứng-dụng)
- [Cấu trúc dự án](#cấu-trúc-dự-án)
- [Tài liệu](#tài-liệu)

## ✨ Tính năng

### Đã hoàn thành ✅
- **Xác thực**: Đăng nhập, đăng ký với mã hóa mật khẩu BCrypt
- **Chat 1-1**: Gửi/nhận tin nhắn text real-time
- **Chat nhóm**: Tạo nhóm, quản lý thành viên, chat nhóm
- **File sharing**: Gửi/nhận file (hình ảnh, tài liệu, v.v.)
- **Voice message**: Ghi và gửi tin nhắn thoại
- **Quản lý bạn bè**: Gửi/chấp nhận/từ chối lời mời kết bạn
- **Trạng thái online/offline**: Hiển thị trạng thái real-time
- **Read receipts**: Đánh dấu đã đọc tin nhắn
- **Typing indicator**: Hiển thị khi người dùng đang gõ
- **Profile**: Chỉnh sửa thông tin, avatar, bio
- **Media gallery**: Xem tất cả file đã gửi/nhận
- **Unread count**: Đếm số tin nhắn chưa đọc
- **Message actions**: Xóa, thu hồi, chỉnh sửa tin nhắn

### Đang phát triển 🚧
- Reply to message (UI indicator)
- Download file từ media gallery
- Unit tests

## 🖥️ Yêu cầu hệ thống

- **Java**: JDK 21 hoặc cao hơn
- **MySQL**: 8.0 hoặc cao hơn
- **Maven**: 3.6+ (để build project)
- **Hệ điều hành**: Windows, Linux, macOS

## 📦 Cài đặt

### 1. Clone repository
```bash
git clone <repository-url>
cd Zalu
```

### 2. Tạo database

Chạy script SQL để tạo database và các bảng:

```bash
mysql -u root -p < database/schema.sql
```

Hoặc mở file `database/schema.sql` và chạy trong MySQL Workbench / phpMyAdmin.

Xem chi tiết trong [database/README.md](database/README.md).

### 3. Cấu hình MySQL
Đảm bảo `max_allowed_packet` đủ lớn để gửi file:
```sql
SET GLOBAL max_allowed_packet=16777216;  -- 16MB
```

Hoặc thêm vào `my.ini`/`my.cnf`:
```ini
max_allowed_packet=16M
```

### 4. Build project
```bash
mvn clean compile
```

## ⚙️ Cấu hình

### Server Configuration (`src/main/resources/server.properties`)
```properties
# IP và port của server
server.address=localhost
server.port=5000
```

**Lưu ý**: Khi chạy client trên máy khác, thay `localhost` bằng IP thực của máy server.

### Database Configuration (`src/main/resources/database.properties`)
```properties
# Database connection
db.url=jdbc:mysql://localhost:3306/laptrinhmang_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
db.username=root
db.password=
db.driver=com.mysql.cj.jdbc.Driver
```

## 🚀 Chạy ứng dụng

### Chạy Server
```bash
# Cách 1: Dùng Maven
mvn clean compile exec:java -Dexec.mainClass="org.example.zalu.server.ChatServer"

# Cách 2: Từ IDE
# Run ChatServer.main()
```

Server sẽ chạy trên port **5000** và hiển thị giao diện quản lý người dùng online.

### Chạy Client
```bash
# Cách 1: Dùng Maven
mvn clean compile exec:java -Dexec.mainClass="org.example.zalu.ZaluApplication"

# Cách 2: Dùng JavaFX Maven plugin
mvn clean javafx:run

# Cách 3: Từ IDE
# Run ZaluApplication.main()
```

### Chạy trên 2 máy khác nhau
Xem chi tiết trong file [HUONG_DAN_CHAY_2_MAY.md](HUONG_DAN_CHAY_2_MAY.md)

## 📁 Cấu trúc dự án

```
Zalu/
├── src/main/java/org/example/zalu/
│   ├── client/              # Client-side networking
│   │   ├── ChatClient.java
│   │   ├── ChatEventManager.java
│   │   └── LoginSession.java
│   ├── server/              # Server-side
│   │   ├── ChatServer.java
│   │   ├── ClientHandler.java
│   │   └── ClientBroadcaster.java
│   ├── controller/          # JavaFX controllers
│   │   ├── auth/           # Login, Register
│   │   ├── chat/           # Chat UI
│   │   ├── friend/         # Friend management
│   │   ├── group/          # Group management
│   │   ├── profile/        # Profile editing
│   │   └── media/          # Media gallery
│   ├── dao/                 # Data Access Objects
│   │   ├── UserDAO.java
│   │   ├── FriendDAO.java
│   │   ├── MessageDAO.java
│   │   ├── GroupDAO.java
│   │   └── VoiceMessageDAO.java
│   ├── model/               # Data models
│   ├── service/             # Business logic services
│   ├── util/                # Utilities
│   │   ├── database/       # DB connection, config
│   │   ├── audio/         # Audio recording/playback
│   │   └── ui/             # UI helpers
│   └── exception/           # Custom exceptions
├── src/main/resources/
│   ├── org/example/zalu/views/  # FXML files
│   ├── images/                  # Images, avatars
│   ├── server.properties        # Server config
│   ├── database.properties      # Database config
│   └── styles.css               # CSS styles
├── pom.xml                      # Maven dependencies
└── README.md                    # This file
```

## 📚 Tài liệu

- [Hướng dẫn chạy trên 2 máy](HUONG_DAN_CHAY_2_MAY.md)
- [Exception Handling](src/main/java/org/example/zalu/exception/README.md)
- [Setup MySQL max_allowed_packet](setup_mysql_max_packet.sql)

## 🛠️ Công nghệ sử dụng

- **JavaFX 21**: UI framework
- **MySQL 8.0**: Database
- **HikariCP**: Connection pooling
- **BCrypt**: Password hashing
- **Maven**: Build tool
- **SLF4J**: Logging

## 🐛 Troubleshooting

### Lỗi kết nối server
- Kiểm tra server đã chạy chưa
- Kiểm tra IP trong `server.properties`
- Kiểm tra firewall có chặn port 5000

### Lỗi kết nối database
- Kiểm tra MySQL đã chạy chưa
- Kiểm tra username/password trong `database.properties`
- Kiểm tra database `laptrinhmang_db` đã tạo chưa

### Lỗi gửi file lớn
- Kiểm tra `max_allowed_packet` trong MySQL >= 16MB
- Xem file `setup_mysql_max_packet.sql`

## 📝 License

Dự án này được tạo cho mục đích học tập.

## 👥 Đóng góp

Mọi đóng góp đều được chào đón! Vui lòng tạo issue hoặc pull request.

---

**Zalu** - Kết nối mọi người, mọi lúc, mọi nơi 💬

