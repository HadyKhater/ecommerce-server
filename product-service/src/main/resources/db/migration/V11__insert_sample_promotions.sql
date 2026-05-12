-- Insert sample promotions data
INSERT INTO promotions (name, code, description, discount_type, discount_value,
  start_date, end_date, active, applicable_to_all)
VALUES
  ('Summer Sale', 'SUMMER20', '20% off everything', 'PERCENTAGE', 20.00,
   NOW(), NOW() + INTERVAL '30 days', true, true),
  ('Welcome Discount', 'WELCOME50', '50 EGP off first order', 'FIXED_AMOUNT', 50.00,
   NOW(), NOW() + INTERVAL '60 days', true, true);
