CREATE TABLE admin_users (
    admin_id VARCHAR(255) PRIMARY KEY,
    full_name VARCHAR(255),
    password_salt VARCHAR(255),
    password_hash VARCHAR(255)
);

INSERT INTO admin_users (admin_id, full_name, password_salt, password_hash)
VALUES
    ('admin-1', 'Support Admin', 'admin-demo-salt', 'ff50ac33dff0cc0f6114b7f5b99598200ce68e9d98231de922d515c94ba084f1');

CREATE INDEX idx_tickets_status_updated
    ON tickets (status, updated_at);
