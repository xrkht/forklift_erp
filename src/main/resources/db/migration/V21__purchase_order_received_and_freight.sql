SET @ddl = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'purchase_order'
          AND column_name = 'freight_amount'
    ),
    'SELECT 1',
    'ALTER TABLE purchase_order ADD COLUMN freight_amount DECIMAL(12, 2) NOT NULL DEFAULT 0 AFTER total_amount'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE purchase_order
SET freight_amount = COALESCE(freight_amount, 0);
