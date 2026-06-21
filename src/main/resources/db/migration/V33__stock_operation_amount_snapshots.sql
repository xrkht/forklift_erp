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

UPDATE stock_operation_log log
LEFT JOIN machine_inventory machine
       ON log.resource_type = 'MACHINE'
      AND log.resource_id = machine.id
LEFT JOIN part_inventory part
       ON log.resource_type = 'PART'
      AND log.resource_id = part.id
SET log.unit_cost = CASE
        WHEN log.resource_type = 'MACHINE' THEN COALESCE(machine.settlement_price, machine.purchase_price, 0)
        WHEN log.resource_type = 'PART' THEN COALESCE(part.settlement_price, part.purchase_price, 0)
        ELSE 0
    END
WHERE log.unit_cost IS NULL;

UPDATE stock_operation_log log
LEFT JOIN outbound_order outbound
       ON outbound.stock_operation_log_id = log.id
SET log.unit_revenue = CASE
        WHEN log.operation_type = 'OUTBOUND' AND outbound.id IS NOT NULL
            THEN COALESCE(outbound.receivable_amount, outbound.settlement_price, outbound.sale_price, 0)
        ELSE 0
    END
WHERE log.unit_revenue IS NULL;
