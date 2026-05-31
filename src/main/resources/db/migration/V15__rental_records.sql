CREATE TABLE IF NOT EXISTS rental_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    version BIGINT DEFAULT 0,
    rental_no VARCHAR(80) NOT NULL,
    machine_id BIGINT NOT NULL,
    vehicle_number VARCHAR(100),
    machine_name VARCHAR(120),
    specification_model VARCHAR(120),
    destination VARCHAR(255) NOT NULL,
    rental_price DECIMAL(12, 2) NOT NULL,
    start_date DATE,
    end_date DATE,
    status VARCHAR(30) DEFAULT 'ACTIVE',
    operator VARCHAR(50),
    remark VARCHAR(500),
    created_at DATETIME,
    updated_at DATETIME,
    last_modified_by VARCHAR(50),
    last_modified_role VARCHAR(30),
    last_modified_priority INT DEFAULT 0,
    UNIQUE KEY uk_rental_record_no (rental_no),
    KEY idx_rental_record_machine (machine_id),
    KEY idx_rental_record_status (status),
    KEY idx_rental_record_start_date (start_date),
    CONSTRAINT fk_rental_record_machine
        FOREIGN KEY (machine_id) REFERENCES machine_inventory(id)
        ON DELETE RESTRICT
);
