# Cấu trúc Exception - Zalu Application

Các exception được tổ chức theo các package con dựa trên chức năng của ứng dụng.

## 📁 Cấu trúc Package

```
org.example.zalu.exception/
├── auth/              # Exception liên quan đến xác thực
├── database/          # Exception liên quan đến database
├── friend/            # Exception liên quan đến bạn bè
├── group/             # Exception liên quan đến nhóm
├── message/           # Exception liên quan đến tin nhắn
├── file/              # Exception liên quan đến file và audio
├── connection/        # Exception liên quan đến kết nối mạng
└── validation/        # Exception liên quan đến validation dữ liệu
```

## 🔐 exception.auth (4 exceptions)

**Chức năng**: Xử lý các lỗi liên quan đến đăng nhập, đăng ký, và quản lý user.

- `LoginFailedException` - Đăng nhập thất bại
- `RegistrationFailedException` - Đăng ký tài khoản thất bại
- `InvalidCredentialsException` - Thông tin đăng nhập không hợp lệ
- `UserNotFoundException` - Không tìm thấy user

**Sử dụng trong**:
- `controller.auth.LoginController`
- `controller.auth.RegisterController`
- `dao.UserDAO`

---

## 🗄️ exception.database (2 exceptions)

**Chức năng**: Xử lý các lỗi liên quan đến kết nối và thao tác database.

- `DatabaseConnectionException` - Không thể kết nối đến database
- `DatabaseException` - Exception tổng quát cho database

**Sử dụng trong**:
- `dao.*` (tất cả các DAO)
- `util.database.DBConnection`
- `util.database.MySQLConfigHelper`

---

## 👥 exception.friend (3 exceptions)

**Chức năng**: Xử lý các lỗi liên quan đến quản lý bạn bè.

- `FriendRequestException` - Lỗi trong quá trình gửi/xử lý friend request
- `FriendAlreadyExistsException` - User đã là bạn hoặc đã gửi lời mời
- `FriendNotFoundException` - Không tìm thấy friend relationship

**Sử dụng trong**:
- `controller.friend.AddFriendController`
- `controller.friend.FriendRequestController`
- `dao.FriendDAO`
- `service.FriendService`

---

## 👨‍👩‍👧‍👦 exception.group (3 exceptions)

**Chức năng**: Xử lý các lỗi liên quan đến quản lý nhóm.

- `GroupOperationException` - Exception tổng quát cho group operations
- `GroupNotFoundException` - Không tìm thấy group
- `UnauthorizedGroupAccessException` - Không có quyền truy cập group

**Sử dụng trong**:
- `controller.group.CreateGroupController`
- `controller.group.ManageGroupController`
- `dao.GroupDAO`

---

## 💬 exception.message (2 exceptions)

**Chức năng**: Xử lý các lỗi liên quan đến tin nhắn.

- `MessageException` - Exception tổng quát cho message
- `MessageSendFailedException` - Gửi message thất bại

**Sử dụng trong**:
- `controller.chat.ChatController`
- `controller.chat.MessageListController`
- `dao.MessageDAO`
- `dao.VoiceMessageDAO`
- `service.MessageUpdateService`

---

## 📎 exception.file (2 exceptions)

**Chức năng**: Xử lý các lỗi liên quan đến file và audio.

- `FileOperationException` - Lỗi xử lý file (upload, download, read, write)
- `AudioException` - Lỗi ghi/phát audio

**Sử dụng trong**:
- `util.audio.AudioRecorder`
- `util.audio.VoicePlayer`
- `controller.chat.ChatController` (file upload)

---

## 🌐 exception.connection (2 exceptions)

**Chức năng**: Xử lý các lỗi liên quan đến kết nối mạng.

- `ConnectionException` - Lỗi kết nối network tổng quát
- `ServerConnectionException` - Không thể kết nối đến server

**Sử dụng trong**:
- `client.ChatClient`
- `server.ChatServer`
- `server.ClientHandler`
- `ZaluApplication`

---

## ✔️ exception.validation (2 exceptions)

**Chức năng**: Xử lý các lỗi validation dữ liệu đầu vào.

- `ValidationException` - Dữ liệu đầu vào không hợp lệ (tổng quát)
- `InvalidInputException` - Input không hợp lệ (email, phone, password, etc.)

**Sử dụng trong**:
- `controller.auth.RegisterController`
- `controller.auth.LoginController`
- `controller.profile.ProfileController`
- Tất cả các controller có form input

---

## 📝 Ví dụ Sử dụng

### Authentication Exception
```java
import org.example.zalu.exception.auth.LoginFailedException;

try {
    // Login logic
    if (!isValidPassword(username, password)) {
        throw new LoginFailedException("Mật khẩu không chính xác");
    }
} catch (LoginFailedException e) {
    // Handle login failure
}
```

### Database Exception
```java
import org.example.zalu.exception.database.DatabaseConnectionException;

try {
    Connection conn = DBConnection.getConnection();
} catch (SQLException e) {
    throw new DatabaseConnectionException("Không thể kết nối database", e);
}
```

### Friend Exception
```java
import org.example.zalu.exception.friend.FriendAlreadyExistsException;

if (friendDAO.isExistingFriendOrRequest(userId, friendId)) {
    throw new FriendAlreadyExistsException("Đã là bạn hoặc đã gửi lời mời");
}
```

### Message Exception
```java
import org.example.zalu.exception.message.MessageSendFailedException;

if (!messageDAO.saveMessage(message)) {
    throw new MessageSendFailedException("Không thể gửi tin nhắn");
}
```

### Validation Exception
```java
import org.example.zalu.exception.validation.InvalidInputException;

if (!isValidEmail(email)) {
    throw new InvalidInputException("Email không hợp lệ");
}
```

---

## 🔄 Migration Guide

Nếu bạn đang sử dụng exception cũ (trong package `exception`), cần cập nhật import:

**Trước:**
```java
import org.example.zalu.exception.LoginFailedException;
```

**Sau:**
```java
import org.example.zalu.exception.auth.LoginFailedException;
```

---

## 📋 Tổng kết

- **Tổng số exception**: 19
- **Số package con**: 8
- **Mỗi exception có**: 2 constructor (message, message + cause)
- **Tất cả exception kế thừa**: `java.lang.Exception`

Cấu trúc này giúp:
- ✅ Dễ dàng tìm và quản lý exception theo chức năng
- ✅ Tránh xung đột tên class
- ✅ Code có tổ chức và dễ maintain
- ✅ Tương thích với cấu trúc package của ứng dụng

