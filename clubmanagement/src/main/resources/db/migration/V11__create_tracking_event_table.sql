-- ============================================================
-- V11: Bang theo doi hanh vi nguoi dung (funnel + UTM attribution) cho G1.2
-- ============================================================

CREATE TABLE IF NOT EXISTS tracking_event (
    id BIGSERIAL PRIMARY KEY,
    event_name VARCHAR(60) NOT NULL,
    session_id VARCHAR(64),
    user_id INTEGER,
    club_id INTEGER,
    order_code BIGINT,
    amount NUMERIC(12, 2),
    page_path VARCHAR(255),
    page_referrer VARCHAR(500),
    utm_source VARCHAR(80),
    utm_medium VARCHAR(80),
    utm_campaign VARCHAR(120),
    utm_content VARCHAR(120),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Funnel loc theo (event_name, thoi gian); channels group by utm_source
CREATE INDEX IF NOT EXISTS idx_tracking_event_name_created ON tracking_event (event_name, created_at);
CREATE INDEX IF NOT EXISTS idx_tracking_event_created_at ON tracking_event (created_at);
CREATE INDEX IF NOT EXISTS idx_tracking_event_utm_source ON tracking_event (utm_source);
CREATE INDEX IF NOT EXISTS idx_tracking_event_user_id ON tracking_event (user_id);
CREATE INDEX IF NOT EXISTS idx_tracking_event_session_id ON tracking_event (session_id);
