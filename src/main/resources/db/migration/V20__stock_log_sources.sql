SET @ddl = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'stock_operation_log'
          AND column_name = 'source_type'
    ),
    'SELECT 1',
    'ALTER TABLE stock_operation_log ADD COLUMN source_type VARCHAR(40) NULL AFTER remark'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'stock_operation_log'
          AND column_name = 'source_id'
    ),
    'SELECT 1',
    'ALTER TABLE stock_operation_log ADD COLUMN source_id BIGINT NULL AFTER source_type'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'stock_operation_log'
          AND index_name = 'idx_stock_operation_log_source'
    ),
    'SELECT 1',
    'ALTER TABLE stock_operation_log ADD KEY idx_stock_operation_log_source (source_type, source_id)'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
