-- Update promotion_usage table
ALTER TABLE promotion_usage ALTER COLUMN user_id TYPE UUID USING user_id::UUID;
ALTER TABLE promotion_usage ADD COLUMN user_email VARCHAR(255);

-- Update promotion_categories table
ALTER TABLE promotion_categories ADD COLUMN category_name VARCHAR(255);

-- Add indexes if missing (V12 might have missed them)
CREATE INDEX IF NOT EXISTS idx_promotion_usage_promotion_id ON promotion_usage(promotion_id);
CREATE INDEX IF NOT EXISTS idx_promotion_usage_user_id ON promotion_usage(user_id);
CREATE INDEX IF NOT EXISTS idx_promotion_categories_promotion_id ON promotion_categories(promotion_id);
