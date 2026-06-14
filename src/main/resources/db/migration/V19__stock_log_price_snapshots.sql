SET @ddl = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'stock_operation_log'
          AND column_name = 'unit_cost'
    ),
    'SELECT 1',
    'ALTER TABLE stock_operation_log ADD COLUMN unit_cost DECIMAL(12, 2) NULL AFTER after_quantity'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'stock_operation_log'
          AND column_name = 'unit_revenue'
    ),
    'SELECT 1',
    'ALTER TABLE stock_operation_log ADD COLUMN unit_revenue DECIMAL(12, 2) NULL AFTER unit_cost'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE stock_operation_log sol
LEFT JOIN machine_inventory mi
    ON sol.resource_type = 'MACHINE'
   AND sol.resource_id = mi.id
LEFT JOIN part_inventory pi
    ON sol.resource_type = 'PART'
   AND sol.resource_id = pi.id
SET sol.unit_cost = CASE
    WHEN sol.resource_type = 'MACHINE' THEN COALESCE(mi.settlement_price, mi.purchase_price, 0)
    WHEN sol.resource_type = 'PART' THEN COALESCE(pi.settlement_price, pi.purchase_price, 0)
    ELSE 0
END
WHERE sol.unit_cost IS NULL;

UPDATE stock_operation_log sol
JOIN outbound_order oo
    ON oo.stock_operation_log_id = sol.id
SET sol.unit_revenue = COALESCE(oo.sale_price, oo.settlement_price, 0)
WHERE sol.operation_type = 'OUTBOUND'
  AND sol.unit_revenue IS NULL;

UPDATE stock_movement_line sml
LEFT JOIN machine_inventory mi
    ON sml.resource_type = 'MACHINE'
   AND sml.resource_id = mi.id
LEFT JOIN part_inventory pi
    ON sml.resource_type = 'PART'
   AND sml.resource_id = pi.id
SET sml.unit_cost = CASE
    WHEN sml.resource_type = 'MACHINE' THEN COALESCE(mi.settlement_price, mi.purchase_price, 0)
    WHEN sml.resource_type = 'PART' THEN COALESCE(pi.settlement_price, pi.purchase_price, 0)
    ELSE 0
END
WHERE sml.unit_cost IS NULL;
