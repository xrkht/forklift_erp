CREATE TABLE IF NOT EXISTS modification_work_order (
    id BIGINT NOT NULL AUTO_INCREMENT,
    version BIGINT NOT NULL DEFAULT 0,
    work_order_no VARCHAR(80) NOT NULL,
    machine_id BIGINT NOT NULL,
    customer_name VARCHAR(100),
    sales_order_no VARCHAR(100),
    status VARCHAR(30) NOT NULL DEFAULT 'WAITING_PARTS',
    operator VARCHAR(50),
    remark VARCHAR(500),
    created_at DATETIME(6),
    updated_at DATETIME(6),
    completed_at DATETIME(6),
    canceled_at DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_modification_work_order_no (work_order_no),
    KEY idx_modification_work_order_machine (machine_id),
    KEY idx_modification_work_order_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS modification_work_order_line (
    id BIGINT NOT NULL AUTO_INCREMENT,
    work_order_id BIGINT NOT NULL,
    machine_config_id BIGINT NOT NULL,
    config_item_id BIGINT,
    item_name VARCHAR(100),
    old_value VARCHAR(200),
    new_part_id BIGINT NOT NULL,
    new_part_code VARCHAR(100),
    new_part_name VARCHAR(120),
    new_value VARCHAR(200),
    quantity INT NOT NULL DEFAULT 1,
    old_part_action VARCHAR(30) NOT NULL DEFAULT 'STOCK_IN',
    replace_log_id BIGINT,
    remark VARCHAR(500),
    created_at DATETIME(6),
    updated_at DATETIME(6),
    PRIMARY KEY (id),
    KEY idx_modification_work_order_line_order (work_order_id),
    KEY idx_modification_work_order_line_config (machine_config_id),
    KEY idx_modification_work_order_line_part (new_part_id),
    CONSTRAINT fk_modification_work_order_line_order
        FOREIGN KEY (work_order_id) REFERENCES modification_work_order (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
