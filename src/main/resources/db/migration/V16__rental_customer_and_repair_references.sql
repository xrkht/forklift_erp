SET @ddl = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'rental_record'
          AND column_name = 'customer_id'
    ),
    'SELECT 1',
    'ALTER TABLE rental_record ADD COLUMN customer_id BIGINT NULL AFTER machine_id'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'rental_record'
          AND column_name = 'customer_name'
    ),
    'SELECT 1',
    'ALTER TABLE rental_record ADD COLUMN customer_name VARCHAR(120) NULL AFTER specification_model'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'rental_record'
          AND column_name = 'customer_address'
    ),
    'SELECT 1',
    'ALTER TABLE rental_record ADD COLUMN customer_address VARCHAR(255) NULL AFTER customer_name'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'rental_record'
          AND column_name = 'monthly_rental_price'
    ),
    'SELECT 1',
    'ALTER TABLE rental_record ADD COLUMN monthly_rental_price DECIMAL(12, 2) NULL AFTER rental_price'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE rental_record
SET monthly_rental_price = rental_price
WHERE monthly_rental_price IS NULL;

SET @ddl = IF(
    EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'rental_record'
          AND index_name = 'idx_rental_record_customer'
    ),
    'SELECT 1',
    'ALTER TABLE rental_record ADD KEY idx_rental_record_customer (customer_id)'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'repair_record'
          AND column_name = 'customer_id'
    ),
    'SELECT 1',
    'ALTER TABLE repair_record ADD COLUMN customer_id BIGINT NULL AFTER vehicle_number'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'repair_record'
          AND column_name = 'used_part_ids'
    ),
    'SELECT 1',
    'ALTER TABLE repair_record ADD COLUMN used_part_ids VARCHAR(500) NULL AFTER used_parts'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'repair_record'
          AND column_name = 'repair_person_user_id'
    ),
    'SELECT 1',
    'ALTER TABLE repair_record ADD COLUMN repair_person_user_id BIGINT NULL AFTER repair_person'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'repair_record'
          AND column_name = 'repair_external'
    ),
    'SELECT 1',
    'ALTER TABLE repair_record ADD COLUMN repair_external BIT(1) NULL AFTER repair_person_user_id'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'repair_record'
          AND index_name = 'idx_repair_record_customer'
    ),
    'SELECT 1',
    'ALTER TABLE repair_record ADD KEY idx_repair_record_customer (customer_id)'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
