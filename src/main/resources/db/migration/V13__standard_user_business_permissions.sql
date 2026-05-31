INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN (
    'vehicle:write',
    'part:write',
    'repair:write',
    'config:write',
    'replace:write',
    'stock:adjust'
)
WHERE r.name = 'USER';

DELETE rp
FROM role_permissions rp
JOIN roles r ON r.id = rp.role_id
JOIN permissions p ON p.id = rp.permission_id
WHERE r.name = 'USER'
  AND p.code IN ('log:read', 'user:read', 'user:write', 'user:admin');
