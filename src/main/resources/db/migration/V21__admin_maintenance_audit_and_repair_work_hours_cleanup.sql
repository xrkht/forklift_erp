CREATE TABLE IF NOT EXISTS admin_maintenance_audit_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_type VARCHAR(60) NOT NULL,
    status VARCHAR(30) NOT NULL,
    operator VARCHAR(50),
    remote_addr VARCHAR(80),
    summary TEXT,
    error_message VARCHAR(1000),
    created_at DATETIME(6),
    updated_at DATETIME(6),
    PRIMARY KEY (id),
    KEY idx_admin_maintenance_audit_created_at (created_at),
    KEY idx_admin_maintenance_audit_event_status (event_type, status),
    KEY idx_admin_maintenance_audit_operator (operator)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @ddl = IF(
    NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'repair_record'
          AND column_name = 'work_hours'
    ),
    'SELECT 1',
    'ALTER TABLE repair_record DROP COLUMN work_hours'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
