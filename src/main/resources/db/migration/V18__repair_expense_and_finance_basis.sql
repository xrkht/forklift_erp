SET @ddl = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'repair_record'
          AND column_name = 'repair_expense'
    ),
    'SELECT 1',
    'ALTER TABLE repair_record ADD COLUMN repair_expense DECIMAL(10, 2) NULL AFTER repair_fee'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE repair_record
SET repair_expense = 0
WHERE repair_expense IS NULL;
