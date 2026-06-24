SET @ddl = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'outbound_order'
          AND column_name = 'is_locked'
    ),
    'SELECT 1',
    'ALTER TABLE outbound_order ADD COLUMN is_locked BIT(1) NOT NULL DEFAULT b''0'''
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'outbound_order'
          AND column_name = 'resource_locked_by_order'
    ),
    'SELECT 1',
    'ALTER TABLE outbound_order ADD COLUMN resource_locked_by_order BIT(1) NOT NULL DEFAULT b''0'''
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
