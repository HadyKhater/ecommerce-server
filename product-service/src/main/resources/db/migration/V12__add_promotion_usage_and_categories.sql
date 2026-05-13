CREATE TABLE promotion_usage (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    promotion_id UUID NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    used_at TIMESTAMP DEFAULT now()
);

CREATE TABLE promotion_categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    promotion_id UUID NOT NULL,
    category_id UUID NOT NULL
);

ALTER TABLE promotions ADD COLUMN per_user_limit INTEGER;
