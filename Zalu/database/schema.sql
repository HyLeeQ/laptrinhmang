-- ============================================
-- Zalu Chat Application - Database Schema
-- ============================================
-- Database: laptrinhmang_db
-- Version: 1.0
-- Created: 2024
-- ============================================

-- Tạo database nếu chưa có
CREATE DATABASE IF NOT EXISTS laptrinhmang_db;
USE laptrinhmang_db;

-- ============================================
-- 1. BẢNG USERS - Người dùng
-- ============================================
CREATE TABLE IF NOT EXISTS users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    full_name VARCHAR(100) NOT NULL,
    password VARCHAR(255) NOT NULL,  -- BCrypt hashed password
    email VARCHAR(100) NOT NULL UNIQUE,
    phone VARCHAR(20) UNIQUE,
    avatar_url VARCHAR(255) DEFAULT '/images/default-avatar.jpg',
    avatar_data LONGBLOB,  -- Binary avatar data
    bio TEXT,
    birthdate DATE,
    gender VARCHAR(20) DEFAULT 'other',
    status VARCHAR(20) DEFAULT 'offline',  -- online, offline, away, busy
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_username (username),
    INDEX idx_email (email),
    INDEX idx_phone (phone),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- 2. BẢNG FRIENDS - Quan hệ bạn bè
-- ============================================
CREATE TABLE IF NOT EXISTS friends (
    user_id INT NOT NULL,
    friend_id INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'pending',  -- pending, accepted, blocked
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    PRIMARY KEY (user_id, friend_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (friend_id) REFERENCES users(id) ON DELETE CASCADE,
    
    INDEX idx_user_id (user_id),
    INDEX idx_friend_id (friend_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- 3. BẢNG GROUPS - Nhóm chat
-- ============================================
CREATE TABLE IF NOT EXISTS groups (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    avatar_url VARCHAR(255),
    avatar_data LONGBLOB,  -- Binary avatar data
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- 4. BẢNG GROUP_MEMBERS - Thành viên nhóm
-- ============================================
CREATE TABLE IF NOT EXISTS group_members (
    group_id INT NOT NULL,
    user_id INT NOT NULL,
    role VARCHAR(20) DEFAULT 'member',  -- admin, member
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    PRIMARY KEY (group_id, user_id),
    FOREIGN KEY (group_id) REFERENCES groups(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    
    INDEX idx_group_id (group_id),
    INDEX idx_user_id (user_id),
    INDEX idx_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- 5. BẢNG MESSAGES - Tin nhắn
-- ============================================
CREATE TABLE IF NOT EXISTS messages (
    id INT AUTO_INCREMENT PRIMARY KEY,
    sender_id INT NOT NULL,
    receiver_id INT,  -- NULL nếu là group message
    group_id INT,  -- NULL nếu là private message (1-1)
    content TEXT,
    file_data LONGBLOB,  -- Binary file data (images, documents, etc.)
    file_name VARCHAR(255),
    is_read BOOLEAN DEFAULT FALSE,
    is_deleted BOOLEAN DEFAULT FALSE,  -- Xóa cho mình
    is_recalled BOOLEAN DEFAULT FALSE,  -- Thu hồi (xóa cho cả hai)
    is_edited BOOLEAN DEFAULT FALSE,
    edited_content TEXT,  -- Nội dung sau khi sửa
    replied_to_message_id INT,  -- ID tin nhắn được trả lời
    replied_to_content TEXT,  -- Preview nội dung tin nhắn được trả lời
    is_pinned BOOLEAN DEFAULT FALSE,  -- Tin nhắn đã được ghim
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (receiver_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (group_id) REFERENCES groups(id) ON DELETE CASCADE,
    FOREIGN KEY (replied_to_message_id) REFERENCES messages(id) ON DELETE SET NULL,
    
    INDEX idx_sender_id (sender_id),
    INDEX idx_receiver_id (receiver_id),
    INDEX idx_group_id (group_id),
    INDEX idx_created_at (created_at),
    INDEX idx_is_read (is_read),
    INDEX idx_is_pinned (is_pinned),
    
    -- Đảm bảo chỉ có receiver_id HOẶC group_id, không có cả hai
    CHECK ((receiver_id IS NULL AND group_id IS NOT NULL) OR 
           (receiver_id IS NOT NULL AND group_id IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- 6. BẢNG VOICE_MESSAGES - Tin nhắn thoại
-- ============================================
CREATE TABLE IF NOT EXISTS voice_messages (
    id INT AUTO_INCREMENT PRIMARY KEY,
    sender_id INT NOT NULL,
    receiver_id INT NOT NULL,
    file_path VARCHAR(500) NOT NULL,  -- Đường dẫn file audio
    is_read BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (receiver_id) REFERENCES users(id) ON DELETE CASCADE,
    
    INDEX idx_sender_id (sender_id),
    INDEX idx_receiver_id (receiver_id),
    INDEX idx_created_at (created_at),
    INDEX idx_is_read (is_read)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- CẤU HÌNH MYSQL CHO FILE LỚN
-- ============================================
-- Chạy lệnh sau để cho phép gửi file lớn (16MB):
-- SET GLOBAL max_allowed_packet=16777216;
-- 
-- Hoặc thêm vào my.ini/my.cnf:
-- max_allowed_packet=16M

-- ============================================
-- XÓA TẤT CẢ DỮ LIỆU (CHỈ DÙNG KHI CẦN RESET)
-- ============================================
-- UNCOMMENT CÁC DÒNG SAU NẾU MUỐN XÓA TẤT CẢ:
-- SET FOREIGN_KEY_CHECKS = 0;
-- TRUNCATE TABLE voice_messages;
-- TRUNCATE TABLE messages;
-- TRUNCATE TABLE group_members;
-- TRUNCATE TABLE groups;
-- TRUNCATE TABLE friends;
-- TRUNCATE TABLE users;
-- SET FOREIGN_KEY_CHECKS = 1;

-- ============================================
-- KẾT THÚC SCHEMA
-- ============================================

