-- Migration: Update carts schema to use email instead of user_id
-- and add unit_price to cart_items for price snapshot support

-- =============================================================================
-- STEP 1: Add new columns
-- =============================================================================

-- Add user_email column to carts table
ALTER TABLE carts ADD COLUMN IF NOT EXISTS user_email VARCHAR(255);

-- Add unit_price column to cart_items table
ALTER TABLE cart_items ADD COLUMN IF NOT EXISTS unit_price NUMERIC(19, 4);

-- =============================================================================
-- STEP 2: Migrate existing data (if any)
-- For existing data, we'll need a mapping from user_id to user_email
-- This is a placeholder - in production, you'd need a proper migration strategy
-- UPDATE carts SET user_email = 'user@example.com' WHERE user_email IS NULL;

-- =============================================================================
-- STEP 3: Make columns NOT NULL after migration
-- =============================================================================

-- Make user_email NOT NULL
ALTER TABLE carts ALTER COLUMN user_email SET NOT NULL;

-- Make unit_price NOT NULL (default to 0 for existing items, should be updated)
UPDATE cart_items SET unit_price = 0.0000 WHERE unit_price IS NULL;
ALTER TABLE cart_items ALTER COLUMN unit_price SET NOT NULL;

-- =============================================================================
-- STEP 4: Update constraints and indexes
-- =============================================================================

-- Drop old unique constraint on user_id
ALTER TABLE carts DROP CONSTRAINT IF EXISTS uk_carts_user_id;

-- Drop old index on user_id
DROP INDEX IF EXISTS idx_carts_user_id;

-- Drop user_id column
ALTER TABLE carts DROP COLUMN user_id;

-- Add new unique constraint on user_email
ALTER TABLE carts DROP CONSTRAINT IF EXISTS uk_carts_user_email;
ALTER TABLE carts ADD CONSTRAINT uk_carts_user_email UNIQUE (user_email);

-- Add new index on user_email
CREATE INDEX IF NOT EXISTS idx_carts_user_email ON carts(user_email);

-- Add constraint for positive quantity
ALTER TABLE cart_items DROP CONSTRAINT IF EXISTS chk_cart_items_quantity_positive;
ALTER TABLE cart_items ADD CONSTRAINT chk_cart_items_quantity_positive
    CHECK (quantity > 0);
