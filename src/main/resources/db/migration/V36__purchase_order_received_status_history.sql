ALTER TABLE purchase_order
    ADD COLUMN status_before_received VARCHAR(30) NULL AFTER status;
