CREATE TABLE ecommerce_users (
    user_id VARCHAR(255) PRIMARY KEY,
    username VARCHAR(255) NOT NULL,
    full_name VARCHAR(255),
    email VARCHAR(255),
    password_salt VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL
);

ALTER TABLE customer_orders
    ADD COLUMN order_time TIMESTAMP WITH TIME ZONE;

UPDATE customer_orders
SET order_time = CURRENT_TIMESTAMP
WHERE order_time IS NULL;

INSERT INTO ecommerce_users (user_id, username, full_name, email, password_salt, password_hash)
VALUES
    ('user-1', 'demo.customer', 'Demo Customer', 'demo.customer@example.com', 'customer-demo-salt', 'f3feeab9f8f98e78ad37d0947b9e483a8ace7f05672c2d74d1c4caa60c3ec069'),
    ('user-2', 'priya.sharma', 'Priya Sharma', 'priya.sharma@example.com', 'customer-priya-salt', '314d3cea2ffdfaa9f33fc83f0ffc3ddbdd3c16878f931df70a7996e90280ebad');

CREATE UNIQUE INDEX idx_ecommerce_users_username
    ON ecommerce_users (username);

INSERT INTO customer_orders (order_id, user_id, total_amount, order_time)
VALUES
    ('ORD-1002', 'user-1', 4500, CURRENT_TIMESTAMP),
    ('ORD-2001', 'user-2', 3500, CURRENT_TIMESTAMP);

INSERT INTO order_items (item_id, order_id, name, price)
VALUES
    ('ITEM-4', 'ORD-1002', 'Backpack', 2500),
    ('ITEM-5', 'ORD-1002', 'Headphones', 2000),
    ('ITEM-6', 'ORD-2001', 'Kurta', 1500),
    ('ITEM-7', 'ORD-2001', 'Sneakers', 2000);

CREATE INDEX idx_customer_orders_user_time
    ON customer_orders (user_id, order_time);
