ALTER TABLE machine_inventory
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN last_modified_by VARCHAR(50),
    ADD COLUMN last_modified_role VARCHAR(30),
    ADD COLUMN last_modified_priority INT NOT NULL DEFAULT 0;

ALTER TABLE part_inventory
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN last_modified_by VARCHAR(50),
    ADD COLUMN last_modified_role VARCHAR(30),
    ADD COLUMN last_modified_priority INT NOT NULL DEFAULT 0;

ALTER TABLE repair_record
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN last_modified_by VARCHAR(50),
    ADD COLUMN last_modified_role VARCHAR(30),
    ADD COLUMN last_modified_priority INT NOT NULL DEFAULT 0;

ALTER TABLE config_item
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN last_modified_by VARCHAR(50),
    ADD COLUMN last_modified_role VARCHAR(30),
    ADD COLUMN last_modified_priority INT NOT NULL DEFAULT 0;

ALTER TABLE config_value
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN last_modified_by VARCHAR(50),
    ADD COLUMN last_modified_role VARCHAR(30),
    ADD COLUMN last_modified_priority INT NOT NULL DEFAULT 0;

ALTER TABLE machine_config
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN last_modified_by VARCHAR(50),
    ADD COLUMN last_modified_role VARCHAR(30),
    ADD COLUMN last_modified_priority INT NOT NULL DEFAULT 0;

ALTER TABLE users
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN last_modified_by VARCHAR(50),
    ADD COLUMN last_modified_role VARCHAR(30),
    ADD COLUMN last_modified_priority INT NOT NULL DEFAULT 0;
