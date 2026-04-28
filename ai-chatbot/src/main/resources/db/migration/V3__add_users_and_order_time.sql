CREATE TABLE ecommerce_users (
    user_id VARCHAR(255) PRIMARY KEY,
    full_name VARCHAR(255),
    email VARCHAR(255)
);

ALTER TABLE customer_orders
    ADD COLUMN order_time TIMESTAMP WITH TIME ZONE;

UPDATE customer_orders
SET order_time = CURRENT_TIMESTAMP
WHERE order_time IS NULL;

INSERT INTO ecommerce_users (user_id, full_name, email)
VALUES
    ('user-1', 'Demo Customer', 'demo.customer@example.com'),
    ('user-2', 'Priya Sharma', 'priya.sharma@example.com');

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
