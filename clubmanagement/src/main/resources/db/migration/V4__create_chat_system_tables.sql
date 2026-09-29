-- ============================================================
-- V4: Tạo các bảng cho Hệ Thống Chat Nội Bộ S-Club
-- Đã đơn giản hóa: Tin nhắn gắn trực tiếp vào club_id và department_id
-- ============================================================

-- 1. Bảng tin nhắn chính (Chat Message)
CREATE TABLE IF NOT EXISTS chat_message (
    id BIGSERIAL PRIMARY KEY,
    club_id INTEGER NOT NULL REFERENCES club(id) ON DELETE CASCADE,
    department_id INTEGER REFERENCES department(id) ON DELETE CASCADE,
    sender_id INTEGER NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    content TEXT,
    message_type VARCHAR(50) NOT NULL DEFAULT 'TEXT', -- 'TEXT', 'FILE', 'SYSTEM'
    is_pinned BOOLEAN NOT NULL DEFAULT FALSE,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

-- Index quan trọng cho phân trang tin nhắn phòng ban & CLB
CREATE INDEX IF NOT EXISTS idx_chat_message_dept_created ON chat_message(club_id, department_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_chat_message_club_created ON chat_message(club_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_chat_message_sender ON chat_message(sender_id);
CREATE INDEX IF NOT EXISTS idx_chat_message_retention ON chat_message(created_at, is_pinned, is_deleted);

-- 2. Bảng tin nhắn lưu trữ (Chat Message Archive)
CREATE TABLE IF NOT EXISTS chat_message_archive (
    id BIGINT PRIMARY KEY,
    club_id INTEGER NOT NULL,
    department_id INTEGER,
    sender_id INTEGER NOT NULL,
    content TEXT,
    message_type VARCHAR(50) NOT NULL,
    is_pinned BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    archived_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_chat_message_archive_dept ON chat_message_archive(club_id, department_id, created_at DESC);

-- 3. Bảng tệp đính kèm (Chat Attachment)
CREATE TABLE IF NOT EXISTS chat_attachment (
    id BIGSERIAL PRIMARY KEY,
    message_id BIGINT NOT NULL REFERENCES chat_message(id) ON DELETE CASCADE,
    file_url TEXT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_size BIGINT,
    file_type VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_chat_attachment_message ON chat_attachment(message_id);
