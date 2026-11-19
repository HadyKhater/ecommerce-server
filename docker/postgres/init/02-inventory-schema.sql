-- inventory-service has Flyway migration SQL in its repo but no flyway-core
-- dependency, so its schema must be created at database initialization.
\connect inventory_db

CREATE TABLE IF NOT EXISTS inventory (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  product_id UUID NOT NULL UNIQUE,
  quantity_available INTEGER NOT NULL DEFAULT 0,
  quantity_reserved INTEGER NOT NULL DEFAULT 0,
  low_stock_threshold INTEGER NOT NULL DEFAULT 10,
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMP DEFAULT now(),
  updated_at TIMESTAMP DEFAULT now(),
  CONSTRAINT chk_quantity_available CHECK (quantity_available >= 0),
  CONSTRAINT chk_quantity_reserved CHECK (quantity_reserved >= 0)
);

CREATE TABLE IF NOT EXISTS stock_movements (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  product_id UUID NOT NULL,
  movement_type VARCHAR(50) NOT NULL,
  quantity INTEGER NOT NULL,
  reason VARCHAR(255),
  created_at TIMESTAMP DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_inventory_product_id ON inventory(product_id);
CREATE INDEX IF NOT EXISTS idx_inventory_low_stock ON inventory(quantity_available, low_stock_threshold);
CREATE INDEX IF NOT EXISTS idx_stock_movements_product_id ON stock_movements(product_id);
CREATE INDEX IF NOT EXISTS idx_stock_movements_created_at ON stock_movements(created_at);

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

DROP TRIGGER IF EXISTS update_inventory_updated_at ON inventory;
CREATE TRIGGER update_inventory_updated_at
    BEFORE UPDATE ON inventory
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Stock for the products seeded by product-service Flyway migration V2.
INSERT INTO inventory (product_id, quantity_available, quantity_reserved, low_stock_threshold) VALUES
  ('550e8400-e29b-41d4-a716-446655440200', 100, 0, 10),
  ('550e8400-e29b-41d4-a716-446655440201', 50,  5, 10),
  ('550e8400-e29b-41d4-a716-446655440202', 25,  2, 10),
  ('550e8400-e29b-41d4-a716-446655440203', 200, 0, 20),
  ('550e8400-e29b-41d4-a716-446655440204', 80,  0, 10),
  ('550e8400-e29b-41d4-a716-446655440205', 60,  0, 10),
  ('550e8400-e29b-41d4-a716-446655440206', 40,  0, 10),
  ('550e8400-e29b-41d4-a716-446655440207', 120, 0, 10),
  ('550e8400-e29b-41d4-a716-446655440208', 70,  0, 10),
  ('550e8400-e29b-41d4-a716-446655440209', 35,  0, 10)
ON CONFLICT (product_id) DO NOTHING;
