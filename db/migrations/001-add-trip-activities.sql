SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE categories
    MODIFY COLUMN category_type ENUM('UNIVERSAL', 'TRANSPORT', 'ACCOMMODATION', 'ACTIVITY')
    COLLATE utf8mb4_unicode_ci NOT NULL;

-- Preserve existing categories with the same names, including their types and links.
INSERT INTO categories (name, category_type)
SELECT seed.name, 'ACTIVITY'
FROM (
    SELECT 'Культурный досуг' AS name
    UNION ALL SELECT 'Городская прогулка'
    UNION ALL SELECT 'Прогулка на природе'
    UNION ALL SELECT 'Пляж'
    UNION ALL SELECT 'Велопрогулка'
    UNION ALL SELECT 'Пеший поход'
) AS seed
WHERE NOT EXISTS (
    SELECT 1 FROM categories c WHERE c.name = seed.name
);
