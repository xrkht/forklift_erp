CREATE TABLE IF NOT EXISTS permissions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(80) NOT NULL,
    description VARCHAR(200),
    PRIMARY KEY (id),
    UNIQUE KEY uk_permissions_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS role_permissions (
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    KEY idx_role_permissions_permission_id (permission_id),
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE,
    CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES permissions (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO permissions (code, description) VALUES
('vehicle:write', 'Create, update, delete and lock vehicle inventory'),
('part:write', 'Create, update and delete part inventory'),
('repair:write', 'Create, update and delete repair records'),
('config:write', 'Maintain configuration dictionaries'),
('replace:write', 'Perform vehicle configuration and part replacement'),
('stock:adjust', 'Adjust vehicle and part stock quantities'),
('log:read', 'View operation logs'),
('user:read', 'View managed users'),
('user:write', 'Create users and reset standard user passwords'),
('user:admin', 'Rename, reset and delete privileged users');

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p
WHERE r.name = 'SUPER_ADMIN';

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN (
    'vehicle:write',
    'part:write',
    'repair:write',
    'config:write',
    'replace:write',
    'stock:adjust',
    'log:read',
    'user:read',
    'user:write'
)
WHERE r.name = 'ADMIN';
