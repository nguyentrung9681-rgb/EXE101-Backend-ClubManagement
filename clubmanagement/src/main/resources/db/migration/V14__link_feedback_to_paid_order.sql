-- ============================================================
-- V14: Gắn feedback với đơn thanh toán đã hoàn thành để có "verified review"
-- Feedback cũ (trước V14) có order_id NULL và được tính là chưa xác minh.
-- ============================================================

ALTER TABLE feedback ADD COLUMN IF NOT EXISTS order_id INTEGER;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_feedback_order') THEN
        ALTER TABLE feedback
            ADD CONSTRAINT fk_feedback_order FOREIGN KEY (order_id)
            REFERENCES payment_orders (id) ON DELETE SET NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_feedback_order_id ON feedback (order_id);

-- Backfill: gắn feedback cũ vào đơn PAID gần nhất của chính người gửi.
-- Không suy diễn cho người chưa từng thanh toán -> những feedback đó vẫn là chưa xác minh.
UPDATE feedback f
SET order_id = (
    SELECT p.id
    FROM payment_orders p
    WHERE p.user_id = f.user_id
      AND p.status = 'PAID'
      AND p.paid_at IS NOT NULL
      AND p.paid_at <= f.created_at
    ORDER BY p.paid_at DESC
    LIMIT 1
)
WHERE f.order_id IS NULL;