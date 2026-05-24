CREATE TABLE IF NOT EXISTS warehouse (
    id BIGINT NOT NULL AUTO_INCREMENT,
    warehouse_code VARCHAR(50) NOT NULL,
    warehouse_name VARCHAR(100) NOT NULL,
    warehouse_type VARCHAR(30) NOT NULL DEFAULT 'MAIN',
    address VARCHAR(255),
    is_default BIT(1) NOT NULL DEFAULT b'0',
    created_at DATETIME(6),
    updated_at DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_warehouse_code (warehouse_code),
    KEY idx_warehouse_default (is_default)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO warehouse (warehouse_code, warehouse_name, warehouse_type, is_default, created_at, updated_at)
SELECT 'DEFAULT', 'Default Warehouse', 'MAIN', b'1', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
WHERE NOT EXISTS (
    SELECT 1 FROM warehouse WHERE warehouse_code = 'DEFAULT'
);

ALTER TABLE machine_inventory
    ADD COLUMN warehouse_id BIGINT NULL,
    ADD COLUMN stock_status VARCHAR(30) NOT NULL DEFAULT 'IN_STOCK',
    ADD KEY idx_machine_inventory_warehouse_status (warehouse_id, stock_status);

ALTER TABLE part_inventory
    ADD COLUMN warehouse_id BIGINT NULL,
    ADD KEY idx_part_inventory_warehouse (warehouse_id);

UPDATE machine_inventory
SET warehouse_id = (SELECT id FROM warehouse WHERE warehouse_code = 'DEFAULT')
WHERE warehouse_id IS NULL;

UPDATE part_inventory
SET warehouse_id = (SELECT id FROM warehouse WHERE warehouse_code = 'DEFAULT')
WHERE warehouse_id IS NULL;

ALTER TABLE machine_inventory
    ADD CONSTRAINT fk_machine_inventory_warehouse
    FOREIGN KEY (warehouse_id) REFERENCES warehouse (id);

ALTER TABLE part_inventory
    ADD CONSTRAINT fk_part_inventory_warehouse
    FOREIGN KEY (warehouse_id) REFERENCES warehouse (id);

CREATE TABLE IF NOT EXISTS stock_balance (
    id BIGINT NOT NULL AUTO_INCREMENT,
    resource_type VARCHAR(30) NOT NULL,
    resource_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    available_quantity INT NOT NULL DEFAULT 0,
    reserved_quantity INT NOT NULL DEFAULT 0,
    locked_quantity INT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6),
    updated_at DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_stock_balance_resource_warehouse (resource_type, resource_id, warehouse_id),
    KEY idx_stock_balance_warehouse (warehouse_id),
    CONSTRAINT fk_stock_balance_warehouse
        FOREIGN KEY (warehouse_id) REFERENCES warehouse (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS stock_movement (
    id BIGINT NOT NULL AUTO_INCREMENT,
    movement_no VARCHAR(60) NOT NULL,
    movement_type VARCHAR(40) NOT NULL,
    resource_type VARCHAR(30) NOT NULL,
    source_type VARCHAR(40),
    source_id BIGINT,
    operator VARCHAR(50),
    remark VARCHAR(255),
    created_at DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_stock_movement_no (movement_no),
    KEY idx_stock_movement_resource (resource_type, source_type, source_id),
    KEY idx_stock_movement_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS stock_movement_line (
    id BIGINT NOT NULL AUTO_INCREMENT,
    movement_id BIGINT NOT NULL,
    resource_type VARCHAR(30) NOT NULL,
    resource_id BIGINT NOT NULL,
    resource_code VARCHAR(100),
    resource_name VARCHAR(120),
    warehouse_id BIGINT NOT NULL,
    quantity_delta INT NOT NULL,
    before_quantity INT NOT NULL,
    after_quantity INT NOT NULL,
    unit_cost DECIMAL(12, 2),
    created_at DATETIME(6),
    PRIMARY KEY (id),
    KEY idx_stock_movement_line_resource (resource_type, resource_id),
    KEY idx_stock_movement_line_warehouse (warehouse_id),
    CONSTRAINT fk_stock_movement_line_movement
        FOREIGN KEY (movement_id) REFERENCES stock_movement (id) ON DELETE CASCADE,
    CONSTRAINT fk_stock_movement_line_warehouse
        FOREIGN KEY (warehouse_id) REFERENCES warehouse (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS repair_part_usage (
    id BIGINT NOT NULL AUTO_INCREMENT,
    repair_id BIGINT NOT NULL,
    part_id BIGINT NOT NULL,
    part_code VARCHAR(100),
    part_name VARCHAR(120),
    quantity INT NOT NULL,
    unit_price DECIMAL(12, 2),
    stock_movement_id BIGINT,
    created_at DATETIME(6),
    PRIMARY KEY (id),
    KEY idx_repair_part_usage_repair (repair_id),
    KEY idx_repair_part_usage_part (part_id),
    CONSTRAINT fk_repair_part_usage_repair
        FOREIGN KEY (repair_id) REFERENCES repair_record (id) ON DELETE CASCADE,
    CONSTRAINT fk_repair_part_usage_part
        FOREIGN KEY (part_id) REFERENCES part_inventory (id),
    CONSTRAINT fk_repair_part_usage_movement
        FOREIGN KEY (stock_movement_id) REFERENCES stock_movement (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO stock_balance (
    resource_type,
    resource_id,
    warehouse_id,
    available_quantity,
    reserved_quantity,
    locked_quantity,
    version,
    created_at,
    updated_at
)
SELECT
    'MACHINE',
    id,
    warehouse_id,
    COALESCE(inventory_count, 0),
    0,
    0,
    0,
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6)
FROM machine_inventory;

INSERT INTO stock_balance (
    resource_type,
    resource_id,
    warehouse_id,
    available_quantity,
    reserved_quantity,
    locked_quantity,
    version,
    created_at,
    updated_at
)
SELECT
    'PART',
    id,
    warehouse_id,
    COALESCE(quantity, 0),
    0,
    0,
    0,
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6)
FROM part_inventory;

INSERT INTO stock_movement (
    movement_no,
    movement_type,
    resource_type,
    source_type,
    operator,
    remark,
    created_at
) VALUES (
    'INIT-V5',
    'INITIAL_BALANCE',
    'MIXED',
    'MIGRATION',
    'system',
    'Initial balances from existing inventory',
    CURRENT_TIMESTAMP(6)
);

INSERT INTO stock_movement_line (
    movement_id,
    resource_type,
    resource_id,
    resource_code,
    resource_name,
    warehouse_id,
    quantity_delta,
    before_quantity,
    after_quantity,
    created_at
)
SELECT
    (SELECT id FROM stock_movement WHERE movement_no = 'INIT-V5'),
    'MACHINE',
    id,
    vehicle_number,
    name,
    warehouse_id,
    COALESCE(inventory_count, 0),
    0,
    COALESCE(inventory_count, 0),
    CURRENT_TIMESTAMP(6)
FROM machine_inventory
WHERE COALESCE(inventory_count, 0) <> 0;

INSERT INTO stock_movement_line (
    movement_id,
    resource_type,
    resource_id,
    resource_code,
    resource_name,
    warehouse_id,
    quantity_delta,
    before_quantity,
    after_quantity,
    created_at
)
SELECT
    (SELECT id FROM stock_movement WHERE movement_no = 'INIT-V5'),
    'PART',
    id,
    part_code,
    part_name,
    warehouse_id,
    COALESCE(quantity, 0),
    0,
    COALESCE(quantity, 0),
    CURRENT_TIMESTAMP(6)
FROM part_inventory
WHERE COALESCE(quantity, 0) <> 0;
