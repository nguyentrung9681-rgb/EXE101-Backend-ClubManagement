-- ============================================================
-- V2: Thêm cột club_id vào google_sheets và google_forms
--     để gắn kết file với CLB cụ thể (phân quyền theo CLB)
-- ============================================================

-- 1. Thêm cột club_id vào bảng google_sheets (nullable trước để không lỗi dữ liệu cũ)
ALTER TABLE google_sheets
    ADD COLUMN IF NOT EXISTS club_id INTEGER;

-- 2. Thêm cột club_id vào bảng google_forms
ALTER TABLE google_forms
    ADD COLUMN IF NOT EXISTS club_id INTEGER;

-- 3. Tạo Foreign Key từ google_sheets.club_id -> club.id
ALTER TABLE google_sheets
    ADD CONSTRAINT fk_google_sheets_club
        FOREIGN KEY (club_id)
            REFERENCES club (id)
            ON DELETE SET NULL;

-- 4. Tạo Foreign Key từ google_forms.club_id -> club.id
ALTER TABLE google_forms
    ADD CONSTRAINT fk_google_forms_club
        FOREIGN KEY (club_id)
            REFERENCES club (id)
            ON DELETE SET NULL;

-- 5. Tạo index để tối ưu query theo club_id
CREATE INDEX IF NOT EXISTS idx_google_sheets_club_id ON google_sheets (club_id);
CREATE INDEX IF NOT EXISTS idx_google_forms_club_id  ON google_forms (club_id);
