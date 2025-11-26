# Database Schema - Zalu Chat Application

## 📋 Tổng quan

File này chứa schema SQL để tạo database cho ứng dụng Zalu Chat.

## 🗄️ Cấu trúc Database

### Bảng chính:

1. **users** - Thông tin người dùng
   - Lưu username, password (BCrypt), email, phone
   - Avatar (URL và binary data)
   - Bio, birthdate, gender, status (online/offline)

2. **friends** - Quan hệ bạn bè
   - Quan hệ giữa 2 users
   - Status: pending, accepted, blocked

3. **groups** - Nhóm chat
   - Tên nhóm, avatar, description

4. **group_members** - Thành viên nhóm
   - Quan hệ many-to-many giữa groups và users
   - Role: admin, member

5. **messages** - Tin nhắn
   - Hỗ trợ cả private (1-1) và group messages
   - File attachments (LONGBLOB)
   - Reply, edit, delete, recall, pin features

6. **voice_messages** - Tin nhắn thoại
   - Lưu đường dẫn file audio
   - Read status

## 🚀 Cách sử dụng

### 1. Tạo database

```bash
mysql -u root -p < database/schema.sql
```

Hoặc chạy từng lệnh trong MySQL Workbench / phpMyAdmin.

### 2. Cấu hình MySQL cho file lớn

Để gửi file lớn, cần tăng `max_allowed_packet`:

```sql
SET GLOBAL max_allowed_packet=16777216;  -- 16MB
```

Hoặc thêm vào `my.ini` / `my.cnf`:

```ini
[mysqld]
max_allowed_packet=16M
```

Xem file `setup_mysql_max_packet.sql` để biết thêm chi tiết.

### 3. Kiểm tra database

```sql
USE laptrinhmang_db;
SHOW TABLES;
DESCRIBE users;
DESCRIBE messages;
```

## 📝 Lưu ý

- **Password**: Được hash bằng BCrypt (không lưu plain text)
- **File Data**: Lưu trong LONGBLOB (có thể lưu file lên đến 4GB)
- **Foreign Keys**: Có CASCADE DELETE để tự động xóa dữ liệu liên quan
- **Indexes**: Đã được tối ưu cho các query thường dùng
- **Charset**: utf8mb4 để hỗ trợ emoji và ký tự đặc biệt

## 🔄 Migration

Nếu bạn đã có database cũ, có thể cần migration:

1. Backup database hiện tại
2. Chạy schema.sql để tạo lại
3. Import lại dữ liệu từ backup (nếu cần)

## 🐛 Troubleshooting

### Lỗi: "max_allowed_packet too small"
- Chạy: `SET GLOBAL max_allowed_packet=16777216;`
- Hoặc thêm vào my.ini: `max_allowed_packet=16M`

### Lỗi: "Foreign key constraint fails"
- Kiểm tra dữ liệu có đúng không
- Đảm bảo các bảng được tạo đúng thứ tự

### Lỗi: "Table already exists"
- Dùng `CREATE TABLE IF NOT EXISTS` (đã có trong schema)
- Hoặc xóa bảng cũ trước: `DROP TABLE IF EXISTS table_name;`

## 📚 Tài liệu tham khảo

- [MySQL Documentation](https://dev.mysql.com/doc/)
- [InnoDB Storage Engine](https://dev.mysql.com/doc/refman/8.0/en/innodb-storage-engine.html)
- [BCrypt Password Hashing](https://en.wikipedia.org/wiki/Bcrypt)

