-- Script để cấu hình max_allowed_packet cho MySQL
-- Chạy script này với quyền admin (root) trong MySQL

-- Cách 1: Set GLOBAL (tạm thời, mất khi restart MySQL)
SET GLOBAL max_allowed_packet=16777216; -- 16MB

-- Kiểm tra giá trị hiện tại
SHOW VARIABLES LIKE 'max_allowed_packet';

-- Lưu ý: Để cấu hình vĩnh viễn, cần thêm vào file my.ini/my.cnf:
-- [mysqld]
-- max_allowed_packet=16M
-- Sau đó restart MySQL server

