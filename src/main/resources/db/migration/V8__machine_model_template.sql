ALTER TABLE machine_inventory
    ADD COLUMN model_only BIT(1) NOT NULL DEFAULT b'0' AFTER inventory_count,
    ADD KEY idx_machine_inventory_model_only (model_only);
