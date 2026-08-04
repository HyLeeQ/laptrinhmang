-- ============================================================
-- Zalu Chat Application - Migration: Admin Features
-- Version : 2.1 (2026-08)
-- Mô tả  : Thêm cột is_locked vào bảng users (DB đã tồn tại)
-- Cách dùng: Chạy script này một lần duy nhất trên DB hiện có
-- ============================================================

USE laptrinhmang_db;

-- Thêm cột is_locked nếu chưa tồn tại
-- MySQL 8.0+ hỗ trợ IF NOT EXISTS cho ALTER TABLE
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS is_locked BOOLEAN NOT NULL DEFAULT FALSE
        COMMENT 'Tài khoản bị khóa bởi Admin: 0=bình thường, 1=bị khóa';

-- Đảm bảo mọi user hiện tại đều có is_locked = FALSE (an toàn)
UPDATE users SET is_locked = FALSE WHERE is_locked IS NULL;

-- Xác nhận kết quả
SELECT
    COLUMN_NAME,
    COLUMN_TYPE,
    IS_NULLABLE,
    COLUMN_DEFAULT,
    COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'laptrinhmang_db'
  AND TABLE_NAME   = 'users'
  AND COLUMN_NAME  = 'is_locked';
