-- ============================================
-- Zalu Chat Application - Database Schema
-- ============================================
-- Database: laptrinhmang_db
-- Version : 2.0 (2025-11)
-- ============================================

-- 0. CREATE DATABASE & DEFAULT CHARSET
CREATE DATABASE IF NOT EXISTS laptrinhmang_db
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;
USE laptrinhmang_db;
SET NAMES 'utf8mb4';

-- ============================================
-- 1. USERS
-- ============================================
CREATE TABLE IF NOT EXISTS users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    full_name VARCHAR(100) NOT NULL,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    phone VARCHAR(20) UNIQUE,
    avatar_url VARCHAR(255) DEFAULT '/images/default-avatar.jpg',
    avatar_data LONGBLOB,
    bio TEXT,
    birthdate DATE,
    gender VARCHAR(20) DEFAULT 'other',
    status VARCHAR(20) DEFAULT 'offline',
    last_login_at TIMESTAMP NULL,
    last_seen_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_users_username (username),
    INDEX idx_users_email (email),
    INDEX idx_users_phone (phone),
    INDEX idx_users_status (status)
) ENGINE=InnoDB;

-- ============================================
-- 2. FRIENDS (friendship + request state)
-- ============================================
CREATE TABLE IF NOT EXISTS friends (
    user_id INT NOT NULL,
    friend_id INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, friend_id),
    CONSTRAINT fk_friends_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_friends_friend FOREIGN KEY (friend_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_friend_not_self CHECK (user_id <> friend_id),
    INDEX idx_friends_user (user_id),
    INDEX idx_friends_friend (friend_id),
    INDEX idx_friends_status (status)
) ENGINE=InnoDB;

-- ============================================
-- 3. GROUPS
-- ============================================
CREATE TABLE IF NOT EXISTS groups (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    created_by INT NOT NULL,
    avatar_url VARCHAR(255),
    avatar_data LONGBLOB,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_groups_creator FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_groups_name (name)
) ENGINE=InnoDB;

-- ============================================
-- 4. GROUP MEMBERS
-- ============================================
CREATE TABLE IF NOT EXISTS group_members (
    group_id INT NOT NULL,
    user_id INT NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'member',
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_seen_at TIMESTAMP NULL,
    PRIMARY KEY (group_id, user_id),
    CONSTRAINT fk_group_members_group FOREIGN KEY (group_id) REFERENCES groups(id) ON DELETE CASCADE,
    CONSTRAINT fk_group_members_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_group_members_group (group_id),
    INDEX idx_group_members_user (user_id),
    INDEX idx_group_members_role (role)
) ENGINE=InnoDB;

-- ============================================
-- 5. MESSAGES (text + file + reply + pin)
-- ============================================
CREATE TABLE IF NOT EXISTS messages (
    id INT AUTO_INCREMENT PRIMARY KEY,
    sender_id INT NOT NULL,
    receiver_id INT NULL,
    group_id INT NULL,
    content LONGTEXT,
    file_data LONGBLOB,
    file_name VARCHAR(255),
    is_read BOOLEAN DEFAULT FALSE,
    is_deleted BOOLEAN DEFAULT FALSE,
    is_recalled BOOLEAN DEFAULT FALSE,
    is_edited BOOLEAN DEFAULT FALSE,
    edited_content LONGTEXT,
    replied_to_message_id INT NULL,
    replied_to_content TEXT,
    is_pinned BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_messages_sender FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_messages_receiver FOREIGN KEY (receiver_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_messages_group FOREIGN KEY (group_id) REFERENCES groups(id) ON DELETE CASCADE,
    CONSTRAINT fk_messages_reply FOREIGN KEY (replied_to_message_id) REFERENCES messages(id) ON DELETE SET NULL,
    INDEX idx_messages_sender (sender_id),
    INDEX idx_messages_receiver (receiver_id),
    INDEX idx_messages_group (group_id),
    INDEX idx_messages_created_at (created_at),
    INDEX idx_messages_is_read (is_read),
    INDEX idx_messages_is_pinned (is_pinned),
    CHECK (
        (receiver_id IS NULL AND group_id IS NOT NULL) OR
        (receiver_id IS NOT NULL AND group_id IS NULL)
    )
) ENGINE=InnoDB;

-- ============================================
-- 6. VOICE MESSAGES
-- ============================================
CREATE TABLE IF NOT EXISTS voice_messages (
    id INT AUTO_INCREMENT PRIMARY KEY,
    sender_id INT NOT NULL,
    receiver_id INT NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    duration_seconds SMALLINT UNSIGNED NULL,
    is_read BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_voice_sender FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_voice_receiver FOREIGN KEY (receiver_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_voice_sender (sender_id),
    INDEX idx_voice_receiver (receiver_id),
    INDEX idx_voice_created_at (created_at),
    INDEX idx_voice_read (is_read)
) ENGINE=InnoDB;

-- ============================================
-- OPTIONAL: USER ACTIVITY LOG (server dashboard)
-- ============================================
CREATE TABLE IF NOT EXISTS user_activity_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    username VARCHAR(50) NOT NULL,
    activity_type VARCHAR(30) NOT NULL,
    target_user_id INT NULL,
    group_id INT NULL,
    encrypted_content TEXT,
    status VARCHAR(20) DEFAULT 'ONLINE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_activity_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_activity_type (activity_type),
    INDEX idx_activity_target (target_user_id),
    INDEX idx_activity_group (group_id),
    INDEX idx_activity_created_at (created_at)
) ENGINE=InnoDB;

-- ============================================
-- MYSQL CONFIG FOR LARGE FILE TRANSFERS
-- ============================================
-- SET GLOBAL max_allowed_packet = 16777216;
-- or edit my.ini/my.cnf: max_allowed_packet=16M

-- ============================================
-- QUICK RESET (uncomment when needed)
-- ============================================
-- SET FOREIGN_KEY_CHECKS = 0;
-- TRUNCATE TABLE user_activity_logs;
-- TRUNCATE TABLE voice_messages;
-- TRUNCATE TABLE messages;
-- TRUNCATE TABLE group_members;
-- TRUNCATE TABLE groups;
-- TRUNCATE TABLE friends;
-- TRUNCATE TABLE users;
-- SET FOREIGN_KEY_CHECKS = 1;
