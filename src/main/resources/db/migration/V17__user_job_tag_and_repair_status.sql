ALTER TABLE users
    ADD COLUMN job_tag VARCHAR(20) NOT NULL DEFAULT 'CLERK' AFTER enabled;

UPDATE users u
JOIN user_roles ur ON ur.user_id = u.id
JOIN roles r ON r.id = ur.role_id
SET u.job_tag = 'MANAGEMENT'
WHERE r.name IN ('ADMIN', 'SUPER_ADMIN');

UPDATE repair_record
SET status = 'PENDING'
WHERE status = 'IN_PROGRESS';
