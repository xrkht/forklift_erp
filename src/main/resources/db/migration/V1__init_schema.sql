CREATE TABLE IF NOT EXISTS roles (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(50) NOT NULL,
    description VARCHAR(200),
    PRIMARY KEY (id),
    UNIQUE KEY uk_roles_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL,
    password VARCHAR(255) NOT NULL,
    enabled BIT(1) NOT NULL DEFAULT b'1',
    created_at DATETIME(6),
    updated_at DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    KEY idx_user_roles_role_id (role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS config_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    category VARCHAR(50) NOT NULL,
    sub_category VARCHAR(50),
    item_name VARCHAR(100) NOT NULL,
    item_code VARCHAR(80) NOT NULL,
    input_type VARCHAR(20) NOT NULL DEFAULT 'SELECT',
    unit VARCHAR(20),
    is_required BIT(1) DEFAULT b'1',
    sort_order INT DEFAULT 0,
    created_at DATETIME(6),
    updated_at DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_config_item_code (item_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS config_value (
    id BIGINT NOT NULL AUTO_INCREMENT,
    config_item_id BIGINT NOT NULL,
    value_label VARCHAR(200) NOT NULL,
    value_code VARCHAR(100),
    is_default BIT(1) DEFAULT b'0',
    sort_order INT DEFAULT 0,
    remark VARCHAR(255),
    created_at DATETIME(6),
    PRIMARY KEY (id),
    KEY idx_config_value_item_id (config_item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS machine_inventory (
    id BIGINT NOT NULL AUTO_INCREMENT,
    is_locked BIT(1) DEFAULT b'0',
    manufacturing_date DATE,
    inbound_date DATETIME(6),
    annual_inspection_date DATE,
    sales_date VARCHAR(10),
    sales_report_date DATE,
    application_number VARCHAR(100),
    material_number VARCHAR(100),
    vehicle_number VARCHAR(100),
    frame_number VARCHAR(100),
    warranty_card_number VARCHAR(100),
    name VARCHAR(100),
    specification_model VARCHAR(100),
    machine_type VARCHAR(30),
    configuration VARCHAR(500),
    supplier VARCHAR(50),
    warehouse_name VARCHAR(100),
    purchase_price DECIMAL(12, 2),
    sale_price DECIMAL(12, 2),
    settlement_price DECIMAL(12, 2),
    is_sales_reported VARCHAR(10),
    inventory_count INT,
    destination1 VARCHAR(255),
    destination2 VARCHAR(255),
    destination3 VARCHAR(255),
    destination4 VARCHAR(255),
    destination5 VARCHAR(255),
    is_invoice_applied VARCHAR(50),
    remarks VARCHAR(500),
    created_at DATETIME(6),
    updated_at DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_machine_inventory_vehicle_number (vehicle_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS machine_config (
    id BIGINT NOT NULL AUTO_INCREMENT,
    is_locked BIT(1) DEFAULT b'0',
    machine_id BIGINT NOT NULL,
    config_item_id BIGINT NOT NULL,
    config_value_id BIGINT NOT NULL,
    item_name VARCHAR(100),
    selected_value VARCHAR(200),
    is_standard BIT(1) DEFAULT b'1',
    config_source VARCHAR(30) DEFAULT 'FACTORY',
    installed_date DATETIME(6),
    remark VARCHAR(255),
    created_at DATETIME(6),
    updated_at DATETIME(6),
    PRIMARY KEY (id),
    KEY idx_machine_config_machine_id (machine_id),
    KEY idx_machine_config_item_id (config_item_id),
    KEY idx_machine_config_value_id (config_value_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS part_inventory (
    id BIGINT NOT NULL AUTO_INCREMENT,
    is_locked BIT(1) DEFAULT b'0',
    manufacturing_date DATE,
    inbound_date DATETIME(6),
    sales_date VARCHAR(10),
    sales_report_date DATE,
    part_code VARCHAR(100) NOT NULL,
    part_brand VARCHAR(100),
    part_name VARCHAR(100) NOT NULL,
    specification VARCHAR(100),
    part_category VARCHAR(50),
    applicable_models VARCHAR(255),
    source VARCHAR(30),
    source_machine_id BIGINT,
    quantity INT NOT NULL DEFAULT 0,
    unit VARCHAR(20) DEFAULT '个',
    purchase_price DECIMAL(12, 2),
    sale_price DECIMAL(12, 2),
    settlement_price DECIMAL(12, 2),
    is_sales_reported VARCHAR(10),
    remarks VARCHAR(255),
    created_at DATETIME(6),
    updated_at DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_part_inventory_part_code (part_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS repair_record (
    id BIGINT NOT NULL AUTO_INCREMENT,
    is_locked BIT(1) DEFAULT b'0',
    repair_date DATETIME(6) NOT NULL,
    machine_id BIGINT,
    vehicle_number VARCHAR(100),
    customer_name VARCHAR(100),
    customer_address VARCHAR(255),
    fault_description VARCHAR(500),
    repair_content VARCHAR(1000),
    repair_person VARCHAR(50),
    used_parts VARCHAR(500),
    work_hours DECIMAL(5, 1),
    repair_fee DECIMAL(10, 2),
    parts_fee DECIMAL(10, 2),
    total_fee DECIMAL(10, 2),
    status VARCHAR(20) DEFAULT 'PENDING',
    remarks VARCHAR(500),
    created_at DATETIME(6),
    updated_at DATETIME(6),
    PRIMARY KEY (id),
    KEY idx_repair_record_machine_id (machine_id),
    KEY idx_repair_record_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS config_replace_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    machine_id BIGINT NOT NULL,
    machine_config_id BIGINT,
    item_name VARCHAR(100) NOT NULL,
    old_value VARCHAR(100),
    new_value VARCHAR(100) NOT NULL,
    replace_type VARCHAR(30),
    new_part_id BIGINT,
    operator VARCHAR(50),
    remark VARCHAR(255),
    created_at DATETIME(6),
    PRIMARY KEY (id),
    KEY idx_config_replace_log_machine_id (machine_id),
    KEY idx_config_replace_log_part_id (new_part_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS stock_operation_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    resource_type VARCHAR(30) NOT NULL,
    operation_type VARCHAR(30) NOT NULL,
    resource_id BIGINT,
    resource_code VARCHAR(100),
    resource_name VARCHAR(100),
    quantity INT NOT NULL,
    before_quantity INT,
    after_quantity INT,
    operator VARCHAR(50),
    remark VARCHAR(255),
    created_at DATETIME(6),
    PRIMARY KEY (id),
    KEY idx_stock_operation_log_resource (resource_type, resource_id),
    KEY idx_stock_operation_log_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
