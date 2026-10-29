-- ============================================================
-- V11: Tạo bảng feedback - đánh giá hệ thống S-Club của người dùng
-- Mỗi người dùng chỉ có 1 feedback (được sửa / xoá), số sao từ 1 đến 5
-- ============================================================

CREATE TABLE IF NOT EXISTS feedback (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES Users(user_id) ON DELETE CASCADE,
    rating SMALLINT NOT NULL,
    comment TEXT,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT uk_feedback_user UNIQUE (user_id),
    CONSTRAINT ck_feedback_rating CHECK (rating BETWEEN 1 AND 5)
);

CREATE INDEX IF NOT EXISTS idx_feedback_rating ON feedback (rating);
CREATE INDEX IF NOT EXISTS idx_feedback_created_at ON feedback (created_at DESC);
