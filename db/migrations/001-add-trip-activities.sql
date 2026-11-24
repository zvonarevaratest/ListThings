SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE categories
    MODIFY COLUMN category_type ENUM('UNIVERSAL', 'TRANSPORT', 'ACCOMMODATION', 'ACTIVITY')
    COLLATE utf8mb4_unicode_ci NOT NULL;

INSERT IGNORE INTO categories (name, category_type) VALUES
    ('Культурный досуг', 'ACTIVITY'),
    ('Городская прогулка', 'ACTIVITY'),
    ('Прогулка на природе', 'ACTIVITY'),
    ('Пляж', 'ACTIVITY'),
    ('Велопрогулка', 'ACTIVITY'),
    ('Пеший поход', 'ACTIVITY');
