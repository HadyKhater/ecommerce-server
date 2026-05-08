-- Create inventory table
CREATE TABLE inventory (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  product_id UUID NOT NULL UNIQUE,
  quantity_available INTEGER NOT NULL DEFAULT 0,
  quantity_reserved INTEGER NOT NULL DEFAULT 0,
  low_stock_threshold INTEGER NOT NULL DEFAULT 10,
  created_at TIMESTAMP DEFAULT now(),
  updated_at TIMESTAMP DEFAULT now(),
  CONSTRAINT chk_quantity_available CHECK (quantity_available >= 0),
  CONSTRAINT chk_quantity_reserved CHECK (quantity_reserved >= 0)
);

-- Create stock_movements table
CREATE TABLE stock_movements (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  product_id UUID NOT NULL,
  movement_type VARCHAR(50) NOT NULL,
  quantity INTEGER NOT NULL,
  reason VARCHAR(255),
  created_at TIMESTAMP DEFAULT now()
);

-- Create indexes
CREATE INDEX idx_inventory_product_id ON inventory(product_id);
CREATE INDEX idx_inventory_low_stock ON inventory(quantity_available, low_stock_threshold);
CREATE INDEX idx_stock_movements_product_id ON stock_movements(product_id);
CREATE INDEX idx_stock_movements_created_at ON stock_movements(created_at);

-- Create trigger function for updating updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Create trigger for inventory updated_at
CREATE TRIGGER update_inventory_updated_at
    BEFORE UPDATE ON inventory
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Insert sample data for existing product IDs from product-service
INSERT INTO inventory (product_id, quantity_available, quantity_reserved, low_stock_threshold)
VALUES
  ('d1fe25ab-b259-4c8e-ab13-babb47d5fade', 100, 0, 10),
  ('550e8400-e29b-41d4-a716-446655440201', 50, 5, 10),
  ('550e8400-e29b-41d4-a716-446655440202', 0, 0, 10),
  ('550e8400-e29b-41d4-a716-446655440203', 25, 2, 5),
  ('550e8400-e29b-41d4-a716-446655440204', 200, 10, 20);
