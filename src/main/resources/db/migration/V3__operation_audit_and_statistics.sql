CREATE TABLE IF NOT EXISTS operation_audit_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    module VARCHAR(50) NOT NULL,
    action VARCHAR(40) NOT NULL,
    target_type VARCHAR(40),
    target_id BIGINT,
    target_code VARCHAR(100),
    target_name VARCHAR(120),
    summary VARCHAR(500),
    operator VARCHAR(50),
    remark VARCHAR(255),
    source_type VARCHAR(40),
    source_id BIGINT,
    created_at DATETIME(6),
    PRIMARY KEY (id),
    KEY idx_operation_audit_created_at (created_at),
    KEY idx_operation_audit_source (source_type, source_id),
    KEY idx_operation_audit_target (target_type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
