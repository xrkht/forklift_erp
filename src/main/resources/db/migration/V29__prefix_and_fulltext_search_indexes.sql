SET @ddl = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'machine_inventory'
          AND index_name = 'idx_machine_search_lock_vehicle'
    ),
    'SELECT 1',
    'ALTER TABLE machine_inventory ADD INDEX idx_machine_search_lock_vehicle (is_locked, vehicle_number)'
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
          AND index_name = 'idx_machine_search_application'
    ),
    'SELECT 1',
    'ALTER TABLE machine_inventory ADD INDEX idx_machine_search_application (application_number)'
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
          AND index_name = 'idx_machine_search_material'
    ),
    'SELECT 1',
    'ALTER TABLE machine_inventory ADD INDEX idx_machine_search_material (material_number)'
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
          AND index_name = 'idx_machine_search_status'
    ),
    'SELECT 1',
    'ALTER TABLE machine_inventory ADD INDEX idx_machine_search_status (stock_status)'
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
          AND index_name = 'ft_machine_inventory_search'
    ),
    'SELECT 1',
    'ALTER TABLE machine_inventory ADD FULLTEXT INDEX ft_machine_inventory_search (name, specification_model, configuration, machine_type, supplier, warehouse_name, destination1, destination2, destination3, destination4, destination5, remarks) WITH PARSER ngram'
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
          AND index_name = 'ft_machine_model_search'
    ),
    'SELECT 1',
    'ALTER TABLE machine_inventory ADD FULLTEXT INDEX ft_machine_model_search (name, specification_model, configuration, machine_type, supplier, warehouse_name) WITH PARSER ngram'
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
          AND index_name = 'idx_part_search_lock_code'
    ),
    'SELECT 1',
    'ALTER TABLE part_inventory ADD INDEX idx_part_search_lock_code (is_locked, part_code)'
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
          AND index_name = 'ft_part_inventory_search'
    ),
    'SELECT 1',
    'ALTER TABLE part_inventory ADD FULLTEXT INDEX ft_part_inventory_search (part_name, part_brand, specification, part_category, applicable_models, source, remarks) WITH PARSER ngram'
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
          AND index_name = 'idx_outbound_search_lock_order'
    ),
    'SELECT 1',
    'ALTER TABLE outbound_order ADD INDEX idx_outbound_search_lock_order (is_locked, order_no)'
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
          AND index_name = 'idx_outbound_search_resource_code'
    ),
    'SELECT 1',
    'ALTER TABLE outbound_order ADD INDEX idx_outbound_search_resource_code (resource_code)'
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
          AND index_name = 'idx_outbound_search_contact_phone'
    ),
    'SELECT 1',
    'ALTER TABLE outbound_order ADD INDEX idx_outbound_search_contact_phone (contact_phone)'
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
          AND index_name = 'idx_outbound_search_invoice_status'
    ),
    'SELECT 1',
    'ALTER TABLE outbound_order ADD INDEX idx_outbound_search_invoice_status (invoice_status)'
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
          AND index_name = 'idx_outbound_search_registration_status'
    ),
    'SELECT 1',
    'ALTER TABLE outbound_order ADD INDEX idx_outbound_search_registration_status (registration_status)'
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
          AND index_name = 'idx_outbound_search_contract_type'
    ),
    'SELECT 1',
    'ALTER TABLE outbound_order ADD INDEX idx_outbound_search_contract_type (contract_type)'
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
          AND index_name = 'ft_outbound_order_search'
    ),
    'SELECT 1',
    'ALTER TABLE outbound_order ADD FULLTEXT INDEX ft_outbound_order_search (resource_name, specification_model, customer_name, customer_address, contact_name, operator, payment_remark, invoice_original_name, contract_original_name, order_remark) WITH PARSER ngram'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
