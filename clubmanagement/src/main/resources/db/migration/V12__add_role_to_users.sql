-- ============================================================
-- V12: Thêm cột role vào bảng public.users để phân quyền ADMIN / USER
-- ============================================================

ALTER TABLE public.users ADD COLUMN IF NOT EXISTS role VARCHAR(20) DEFAULT 'USER' NOT NULL;

CREATE INDEX IF NOT EXISTS idx_users_role ON public.users(role);
