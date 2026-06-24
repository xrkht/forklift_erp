ALTER TABLE warehouse
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER id;

CREATE INDEX idx_warehouse_name ON warehouse (warehouse_name);
CREATE INDEX idx_stock_movement_line_created_at ON stock_movement_line (created_at);
CREATE INDEX idx_stock_movement_line_resource_warehouse ON stock_movement_line (resource_type, resource_id, warehouse_id);
