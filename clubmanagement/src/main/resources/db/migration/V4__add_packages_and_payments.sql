-- ============================================================
-- V4: Thêm bảng quản lý gói dịch vụ, đơn hàng thanh toán VietQR và đăng ký gói CLB
-- ============================================================

-- 1. Bảng gói dịch vụ (subscription_packages)
CREATE TABLE IF NOT EXISTS subscription_packages (
    id SERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    price NUMERIC(12, 2) NOT NULL,
    duration_days INTEGER NOT NULL,
    description TEXT,
    features TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Seed các gói dịch vụ chính thức
INSERT INTO subscription_packages (code, name, price, duration_days, description, is_active)
VALUES 
('FLASH_SALE', 'Gói Flash Sale', 20000, 14, 'Gói dùng thử trải nghiệm 14 ngày', true),
('MONTHLY', 'Gói Theo Tháng', 99000, 30, 'Dành cho CLB dưới 30 thành viên (99.000đ/tháng)', true),
('YEARLY', 'Gói Theo Năm', 708000, 365, 'Dành cho CLB trên 30 thành viên (Tiết kiệm chỉ 59.000đ/tháng)', true)
ON CONFLICT (code) DO NOTHING;

-- 2. Bảng đơn hàng thanh toán (payment_orders)
CREATE TABLE IF NOT EXISTS payment_orders (
    id SERIAL PRIMARY KEY,
    order_code BIGINT NOT NULL UNIQUE,
    user_id INTEGER REFERENCES Users(user_id) ON DELETE SET NULL,
    club_id INTEGER REFERENCES club(id) ON DELETE SET NULL,
    package_id INTEGER REFERENCES subscription_packages(id) ON DELETE RESTRICT,
    amount NUMERIC(12, 2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    checkout_url TEXT,
    qr_code_url TEXT,
    payment_method VARCHAR(50) DEFAULT 'VIETQR',
    transaction_no VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    paid_at TIMESTAMP,
    cancelled_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_payment_orders_order_code ON payment_orders(order_code);
CREATE INDEX IF NOT EXISTS idx_payment_orders_user_id ON payment_orders(user_id);
CREATE INDEX IF NOT EXISTS idx_payment_orders_club_id ON payment_orders(club_id);

-- 3. Bảng đăng ký gói của CLB (club_subscriptions)
CREATE TABLE IF NOT EXISTS club_subscriptions (
    id SERIAL PRIMARY KEY,
    club_id INTEGER NOT NULL REFERENCES club(id) ON DELETE CASCADE,
    package_id INTEGER NOT NULL REFERENCES subscription_packages(id) ON DELETE RESTRICT,
    order_id INTEGER REFERENCES payment_orders(id) ON DELETE SET NULL,
    start_date TIMESTAMP NOT NULL,
    end_date TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_club_subscriptions_club_id ON club_subscriptions(club_id);
CREATE INDEX IF NOT EXISTS idx_club_subscriptions_status ON club_subscriptions(status);
