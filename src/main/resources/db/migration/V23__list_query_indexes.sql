SET @ddl = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'users'
          AND index_name = 'idx_users_job_tag_created'
    ),
    'SELECT 1',
    'ALTER TABLE users ADD INDEX idx_users_job_tag_created (job_tag, created_at)'
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
          AND index_name = 'idx_machine_inventory_lock_id'
    ),
    'SELECT 1',
    'ALTER TABLE machine_inventory ADD INDEX idx_machine_inventory_lock_id (is_locked, id)'
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
          AND index_name = 'idx_machine_inventory_created'
    ),
    'SELECT 1',
    'ALTER TABLE machine_inventory ADD INDEX idx_machine_inventory_created (created_at)'
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
          AND index_name = 'idx_part_inventory_lock_id'
    ),
    'SELECT 1',
    'ALTER TABLE part_inventory ADD INDEX idx_part_inventory_lock_id (is_locked, id)'
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
          AND index_name = 'idx_part_inventory_category'
    ),
    'SELECT 1',
    'ALTER TABLE part_inventory ADD INDEX idx_part_inventory_category (part_category)'
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
          AND index_name = 'idx_part_inventory_source_machine'
    ),
    'SELECT 1',
    'ALTER TABLE part_inventory ADD INDEX idx_part_inventory_source_machine (source, source_machine_id)'
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
          AND index_name = 'idx_repair_record_lock_date'
    ),
    'SELECT 1',
    'ALTER TABLE repair_record ADD INDEX idx_repair_record_lock_date (is_locked, repair_date)'
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
          AND index_name = 'idx_repair_record_person_date'
    ),
    'SELECT 1',
    'ALTER TABLE repair_record ADD INDEX idx_repair_record_person_date (repair_person, repair_date)'
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
          AND index_name = 'idx_outbound_order_lock_created'
    ),
    'SELECT 1',
    'ALTER TABLE outbound_order ADD INDEX idx_outbound_order_lock_created (is_locked, created_at)'
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
          AND index_name = 'idx_outbound_order_status_followup'
    ),
    'SELECT 1',
    'ALTER TABLE outbound_order ADD INDEX idx_outbound_order_status_followup (payment_settled, sales_reported, invoice_applied, created_at)'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'rental_record'
          AND index_name = 'idx_rental_record_status_created'
    ),
    'SELECT 1',
    'ALTER TABLE rental_record ADD INDEX idx_rental_record_status_created (status, created_at)'
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
          AND index_name = 'idx_purchase_order_status_created'
    ),
    'SELECT 1',
    'ALTER TABLE purchase_order ADD INDEX idx_purchase_order_status_created (status, created_at)'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'stocktaking_record'
          AND index_name = 'idx_stocktaking_status_created'
    ),
    'SELECT 1',
    'ALTER TABLE stocktaking_record ADD INDEX idx_stocktaking_status_created (status, created_at)'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
