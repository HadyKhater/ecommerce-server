-- Insert sample categories
INSERT INTO categories (id, name, description) VALUES
    ('550e8400-e29b-41d4-a716-446655440100', 'Electronics', 'Electronic devices and accessories'),
    ('550e8400-e29b-41d4-a716-446655440101', 'Clothing', 'Men and women clothing'),
    ('550e8400-e29b-41d4-a716-446655440102', 'Books', 'Physical and digital books'),
    ('550e8400-e29b-41d4-a716-446655440103', 'Home & Garden', 'Home decor and gardening supplies'),
    ('550e8400-e29b-41d4-a716-446655440104', 'Sports', 'Sports equipment and accessories');

-- Insert sample products
INSERT INTO products (id, name, description, price, category_id, image_url, deleted) VALUES
    ('550e8400-e29b-41d4-a716-446655440200', 'Wireless Headphones', 'High-quality wireless headphones with noise cancellation', 199.99, '550e8400-e29b-41d4-a716-446655440100', 'https://example.com/images/headphones.jpg', false),
    ('550e8400-e29b-41d4-a716-446655440201', 'Smartphone', 'Latest generation smartphone with 5G support', 899.99, '550e8400-e29b-41d4-a716-446655440100', 'https://example.com/images/smartphone.jpg', false),
    ('550e8400-e29b-41d4-a716-446655440202', 'Laptop', '15.6 inch laptop with 16GB RAM and 512GB SSD', 1299.99, '550e8400-e29b-41d4-a716-446655440100', 'https://example.com/images/laptop.jpg', false),
    ('550e8400-e29b-41d4-a716-446655440203', 'Cotton T-Shirt', 'Comfortable 100% cotton t-shirt', 29.99, '550e8400-e29b-41d4-a716-446655440101', 'https://example.com/images/tshirt.jpg', false),
    ('550e8400-e29b-41d4-a716-446655440204', 'Running Shoes', 'Lightweight running shoes with cushioning', 89.99, '550e8400-e29b-41d4-a716-446655440101', 'https://example.com/images/shoes.jpg', false),
    ('550e8400-e29b-41d4-a716-446655440205', 'Java Programming Book', 'Comprehensive guide to Java programming', 49.99, '550e8400-e29b-41d4-a716-446655440102', 'https://example.com/images/java-book.jpg', false),
    ('550e8400-e29b-41d4-a716-446655440206', 'Garden Tools Set', 'Complete set of essential garden tools', 79.99, '550e8400-e29b-41d4-a716-446655440103', 'https://example.com/images/garden-tools.jpg', false),
    ('550e8400-e29b-41d4-a716-446655440207', 'Yoga Mat', 'Non-slip yoga mat with carrying strap', 34.99, '550e8400-e29b-41d4-a716-446655440104', 'https://example.com/images/yoga-mat.jpg', false),
    ('550e8400-e29b-41d4-a716-446655440208', 'Basketball', 'Official size basketball', 24.99, '550e8400-e29b-41d4-a716-446655440104', 'https://example.com/images/basketball.jpg', false),
    ('550e8400-e29b-41d4-a716-446655440209', 'Tablet', '10 inch tablet with high resolution display', 449.99, '550e8400-e29b-41d4-a716-446655440100', 'https://example.com/images/tablet.jpg', false);
