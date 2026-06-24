ALTER TABLE modification_work_order_line
    MODIFY COLUMN new_part_id BIGINT NULL,
    ADD COLUMN new_config_value_id BIGINT NULL AFTER new_value,
    ADD COLUMN price_difference DECIMAL(12, 2) NOT NULL DEFAULT 0 AFTER old_part_action,
    ADD KEY idx_modification_work_order_line_config_value (new_config_value_id);
