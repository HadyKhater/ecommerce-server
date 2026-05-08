-- Remove stock_quantity column from products table
-- Stock is now managed exclusively by Inventory Service

ALTER TABLE products DROP COLUMN IF EXISTS stock_quantity;
