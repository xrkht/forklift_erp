SET @ddl = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'part_inventory'
          AND index_name = 'idx_part_inventory_lock_category'
    ),
    'SELECT 1',
    'ALTER TABLE part_inventory ADD INDEX idx_part_inventory_lock_category (is_locked, part_category)'
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
          AND index_name = 'idx_part_inventory_lock_source'
    ),
    'SELECT 1',
    'ALTER TABLE part_inventory ADD INDEX idx_part_inventory_lock_source (is_locked, source)'
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
          AND index_name = 'idx_part_inventory_lock_source_machine'
    ),
    'SELECT 1',
    'ALTER TABLE part_inventory ADD INDEX idx_part_inventory_lock_source_machine (is_locked, source_machine_id)'
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
          AND index_name = 'idx_repair_record_lock_machine_date'
    ),
    'SELECT 1',
    'ALTER TABLE repair_record ADD INDEX idx_repair_record_lock_machine_date (is_locked, machine_id, repair_date)'
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
          AND index_name = 'idx_repair_record_lock_person'
    ),
    'SELECT 1',
    'ALTER TABLE repair_record ADD INDEX idx_repair_record_lock_person (is_locked, repair_person)'
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
          AND index_name = 'idx_repair_record_lock_status'
    ),
    'SELECT 1',
    'ALTER TABLE repair_record ADD INDEX idx_repair_record_lock_status (is_locked, status)'
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
          AND index_name = 'idx_outbound_order_legacy_invoice_file'
    ),
    'SELECT 1',
    'ALTER TABLE outbound_order ADD INDEX idx_outbound_order_legacy_invoice_file (invoice_stored_file_name, id)'
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
          AND index_name = 'idx_outbound_order_legacy_contract_file'
    ),
    'SELECT 1',
    'ALTER TABLE outbound_order ADD INDEX idx_outbound_order_legacy_contract_file (contract_stored_file_name, id)'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
