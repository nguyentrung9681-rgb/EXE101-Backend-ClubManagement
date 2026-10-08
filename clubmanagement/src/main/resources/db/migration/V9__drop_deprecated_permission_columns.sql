-- ============================================================
-- V9: Xóa bỏ triệt để 5 cột phân quyền cũ (can_create, can_delete, can_edit_title, can_edit_data, can_edit_type)
--     để tránh lỗi NOT NULL constraint khi INSERT dữ liệu phân quyền vào PostgreSQL
-- ============================================================

ALTER TABLE club_google_permission DROP COLUMN IF EXISTS can_create CASCADE;
ALTER TABLE club_google_permission DROP COLUMN IF EXISTS can_delete CASCADE;
ALTER TABLE club_google_permission DROP COLUMN IF EXISTS can_edit_title CASCADE;
ALTER TABLE club_google_permission DROP COLUMN IF EXISTS can_edit_data CASCADE;
ALTER TABLE club_google_permission DROP COLUMN IF EXISTS can_edit_type CASCADE;
