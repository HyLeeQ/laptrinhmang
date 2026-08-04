-- ============================================
-- Zalu Chat Application - Migration Script
-- Adds `user_reports` table for reporting feature
-- ============================================
USE laptrinhmang_db;

CREATE TABLE IF NOT EXISTS user_reports (
    id INT AUTO_INCREMENT PRIMARY KEY,
    reporter_id INT NOT NULL,
    reported_user_id INT NOT NULL,
    reason VARCHAR(100) NOT NULL,
    description TEXT,
    status VARCHAR(20) DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_report_reporter FOREIGN KEY (reporter_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_report_reported FOREIGN KEY (reported_user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_report_status (status)
) ENGINE=InnoDB;
