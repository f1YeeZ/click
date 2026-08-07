ALTER TABLE review_support_positions ADD COLUMN IF NOT EXISTS grip_style VARCHAR(20);

ALTER TABLE review_support_positions DROP CONSTRAINT IF EXISTS uk_review_support_position;
ALTER TABLE review_support_positions
    ADD CONSTRAINT uk_review_support_position UNIQUE (review_id, grip_style, position_code);

CREATE INDEX IF NOT EXISTS idx_review_support_grip ON review_support_positions(review_id, grip_style);
