SET @ddl = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'outbound_order'
          AND column_name = 'sales_date'
    ),
    'SELECT 1',
    'ALTER TABLE outbound_order ADD COLUMN sales_date DATE NULL'
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
          AND column_name = 'sale_price'
    ),
    'SELECT 1',
    'ALTER TABLE outbound_order ADD COLUMN sale_price DECIMAL(12, 2) NULL'
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
          AND column_name = 'payment_remark'
    ),
    'SELECT 1',
    'ALTER TABLE outbound_order ADD COLUMN payment_remark VARCHAR(500) NULL'
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
          AND column_name = 'invoice_status'
    ),
    'SELECT 1',
    'ALTER TABLE outbound_order ADD COLUMN invoice_status VARCHAR(120) NULL'
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
          AND column_name = 'invoice_issued_date'
    ),
    'SELECT 1',
    'ALTER TABLE outbound_order ADD COLUMN invoice_issued_date DATE NULL'
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
          AND column_name = 'registration_status'
    ),
    'SELECT 1',
    'ALTER TABLE outbound_order ADD COLUMN registration_status VARCHAR(120) NULL'
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
          AND column_name = 'contract_type'
    ),
    'SELECT 1',
    'ALTER TABLE outbound_order ADD COLUMN contract_type VARCHAR(80) NULL'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
