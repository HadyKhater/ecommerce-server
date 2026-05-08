-- Add slug column to categories table
ALTER TABLE categories ADD COLUMN slug VARCHAR(255);

-- Create index for slug lookups
CREATE INDEX idx_categories_slug ON categories(slug);

-- Generate slug values for existing categories based on their names
-- This uses PostgreSQL's LOWER and REGEXP_REPLACE to create URL-friendly slugs
UPDATE categories
SET slug = LOWER(
    REGEXP_REPLACE(
        REGEXP_REPLACE(
            REGEXP_REPLACE(name, '[^a-zA-Z0-9\s]', '', 'g'),
            '\s+', '-', 'g'
        ),
        '-+', '-', 'g'
    )
);

-- Handle duplicate slugs by appending a number
DO $$
DECLARE
    rec RECORD;
    base_slug VARCHAR(255);
    new_slug VARCHAR(255);
    counter INTEGER;
BEGIN
    FOR rec IN
        SELECT id, slug
        FROM categories c1
        WHERE EXISTS (
            SELECT 1 FROM categories c2
            WHERE c2.slug = c1.slug AND c2.id != c1.id
        )
        ORDER BY created_at
    LOOP
        base_slug := rec.slug;
        new_slug := base_slug;
        counter := 1;

        WHILE EXISTS (SELECT 1 FROM categories WHERE slug = new_slug AND id != rec.id) LOOP
            new_slug := base_slug || '-' || counter;
            counter := counter + 1;
        END LOOP;

        UPDATE categories SET slug = new_slug WHERE id = rec.id;
    END LOOP;
END $$;

-- Set slug as NOT NULL after populating all values
ALTER TABLE categories ALTER COLUMN slug SET NOT NULL;

-- Add unique constraint to slug
ALTER TABLE categories ADD CONSTRAINT uk_categories_slug UNIQUE (slug);
