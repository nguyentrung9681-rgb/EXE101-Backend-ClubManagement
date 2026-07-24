-- ============================================================
-- V3: Thay đổi kiểu dữ liệu cột logo_url trong bảng club thành TEXT
--     để hỗ trợ lưu trữ các đường dẫn hình ảnh siêu dài hoặc base64
-- ============================================================
ALTER TABLE club ALTER COLUMN logo_url TYPE TEXT;
