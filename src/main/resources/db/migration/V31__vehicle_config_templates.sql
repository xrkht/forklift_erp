CREATE TABLE IF NOT EXISTS vehicle_config_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    version BIGINT,
    specification_model VARCHAR(120) NOT NULL,
    sort_order INT DEFAULT 0,
    remark VARCHAR(255),
    created_at DATETIME(6),
    updated_at DATETIME(6),
    last_modified_by VARCHAR(50),
    last_modified_role VARCHAR(30),
    last_modified_priority INT DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_vehicle_config_item_spec (specification_model)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS vehicle_config_value (
    id BIGINT NOT NULL AUTO_INCREMENT,
    version BIGINT,
    vehicle_config_item_id BIGINT NOT NULL,
    config_item_id BIGINT NOT NULL,
    config_value_id BIGINT NOT NULL,
    config_item_name VARCHAR(120),
    config_value_label VARCHAR(200),
    sort_order INT DEFAULT 0,
    remark VARCHAR(255),
    created_at DATETIME(6),
    updated_at DATETIME(6),
    last_modified_by VARCHAR(50),
    last_modified_role VARCHAR(30),
    last_modified_priority INT DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_vehicle_config_value_item (vehicle_config_item_id, config_item_id),
    KEY idx_vehicle_config_value_vehicle_item (vehicle_config_item_id),
    KEY idx_vehicle_config_value_config (config_item_id, config_value_id),
    CONSTRAINT fk_vehicle_config_value_item FOREIGN KEY (vehicle_config_item_id) REFERENCES vehicle_config_item (id) ON DELETE CASCADE,
    CONSTRAINT fk_vehicle_config_value_config_item FOREIGN KEY (config_item_id) REFERENCES config_item (id) ON DELETE RESTRICT,
    CONSTRAINT fk_vehicle_config_value_config_value FOREIGN KEY (config_value_id) REFERENCES config_value (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
