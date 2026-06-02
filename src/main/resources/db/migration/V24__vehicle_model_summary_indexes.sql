SET @ddl = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'machine_inventory'
          AND index_name = 'idx_machine_inventory_model_summary'
    ),
    'SELECT 1',
    'ALTER TABLE machine_inventory ADD INDEX idx_machine_inventory_model_summary (is_locked, name, specification_model, machine_type, model_only)'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
