SET @ddl = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'outbound_order'
          AND index_name = 'idx_outbound_order_todo_payment'
    ),
    'SELECT 1',
    'ALTER TABLE outbound_order ADD INDEX idx_outbound_order_todo_payment (is_locked, payment_due_date, updated_at)'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'outbound_order'
          AND index_name = 'idx_outbound_order_todo_invoice'
    ),
    'SELECT 1',
    'ALTER TABLE outbound_order ADD INDEX idx_outbound_order_todo_invoice (is_locked, invoice_applied, invoice_issued_date, updated_at)'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'outbound_order'
          AND index_name = 'idx_outbound_order_todo_contract'
    ),
    'SELECT 1',
    'ALTER TABLE outbound_order ADD INDEX idx_outbound_order_todo_contract (is_locked, contract_type, updated_at)'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'repair_record'
          AND index_name = 'idx_repair_record_todo_status_updated'
    ),
    'SELECT 1',
    'ALTER TABLE repair_record ADD INDEX idx_repair_record_todo_status_updated (is_locked, status, updated_at)'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'part_inventory'
          AND index_name = 'idx_part_inventory_todo_quantity_updated'
    ),
    'SELECT 1',
    'ALTER TABLE part_inventory ADD INDEX idx_part_inventory_todo_quantity_updated (is_locked, quantity, updated_at)'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'machine_inventory'
          AND index_name = 'idx_machine_inventory_todo_stock'
    ),
    'SELECT 1',
    'ALTER TABLE machine_inventory ADD INDEX idx_machine_inventory_todo_stock (is_locked, model_only, inventory_count)'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
