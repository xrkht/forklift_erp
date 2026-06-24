SET @ddl = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'outbound_order'
          AND column_name = 'receivable_amount'
    ),
    'SELECT 1',
    'ALTER TABLE outbound_order ADD COLUMN receivable_amount DECIMAL(12, 2) NULL AFTER sale_price'
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
          AND column_name = 'received_amount'
    ),
    'SELECT 1',
    'ALTER TABLE outbound_order ADD COLUMN received_amount DECIMAL(12, 2) NOT NULL DEFAULT 0 AFTER receivable_amount'
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
          AND column_name = 'payment_due_date'
    ),
    'SELECT 1',
    'ALTER TABLE outbound_order ADD COLUMN payment_due_date DATE NULL AFTER received_amount'
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
          AND column_name = 'last_payment_date'
    ),
    'SELECT 1',
    'ALTER TABLE outbound_order ADD COLUMN last_payment_date DATE NULL AFTER payment_due_date'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE outbound_order
SET receivable_amount = COALESCE(receivable_amount, settlement_price),
    received_amount = CASE
        WHEN payment_settled = b'1' THEN COALESCE(received_amount, settlement_price, 0)
        ELSE COALESCE(received_amount, 0)
    END
WHERE receivable_amount IS NULL
   OR received_amount IS NULL;

SET @ddl = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'outbound_order'
          AND index_name = 'idx_outbound_order_payment_due_date'
    ),
    'SELECT 1',
    'ALTER TABLE outbound_order ADD KEY idx_outbound_order_payment_due_date (payment_due_date)'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
