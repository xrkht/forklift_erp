SET @ddl = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'outbound_order'
          AND column_name = 'invoice_stored_file_name'
    ),
    'SELECT 1',
    'ALTER TABLE outbound_order ADD COLUMN invoice_stored_file_name VARCHAR(255) NULL'
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
          AND column_name = 'invoice_original_name'
    ),
    'SELECT 1',
    'ALTER TABLE outbound_order ADD COLUMN invoice_original_name VARCHAR(255) NULL'
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
          AND column_name = 'invoice_content_type'
    ),
    'SELECT 1',
    'ALTER TABLE outbound_order ADD COLUMN invoice_content_type VARCHAR(120) NULL'
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
          AND column_name = 'invoice_file_size'
    ),
    'SELECT 1',
    'ALTER TABLE outbound_order ADD COLUMN invoice_file_size BIGINT NULL'
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
          AND column_name = 'invoice_uploaded_at'
    ),
    'SELECT 1',
    'ALTER TABLE outbound_order ADD COLUMN invoice_uploaded_at DATETIME(6) NULL'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
