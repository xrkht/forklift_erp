SET @ddl = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'purchase_order'
          AND column_name = 'config_item_id'
    ),
    'SELECT 1',
    'ALTER TABLE purchase_order ADD COLUMN config_item_id BIGINT AFTER supplier_name'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'purchase_order'
          AND column_name = 'config_value_id'
    ),
    'SELECT 1',
    'ALTER TABLE purchase_order ADD COLUMN config_value_id BIGINT AFTER config_item_id'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'purchase_order'
          AND index_name = 'idx_purchase_order_config'
    ),
    'SELECT 1',
    'ALTER TABLE purchase_order ADD INDEX idx_purchase_order_config (config_item_id, config_value_id)'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
