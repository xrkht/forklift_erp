SET @ddl = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'repair_record'
          AND column_name = 'parts_cost'
    ),
    'SELECT 1',
    'ALTER TABLE repair_record ADD COLUMN parts_cost DECIMAL(12, 2) NULL AFTER parts_fee'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE repair_record repair
SET repair.parts_cost = COALESCE((
    SELECT SUM(COALESCE(part.settlement_price, part.purchase_price, 0))
    FROM part_inventory part
    WHERE repair.used_part_ids IS NOT NULL
      AND repair.used_part_ids <> ''
      AND FIND_IN_SET(CAST(part.id AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_unicode_ci, repair.used_part_ids) > 0
), 0)
WHERE repair.parts_cost IS NULL;
