INSERT INTO chatbot_app.ecommerce_users (
    user_id,
    username,
    full_name,
    email,
    password_salt,
    password_hash,
    shopping_budget,
    email_verified
)
VALUES
    ('user-1', 'demo.customer', 'Demo Customer', 'demo.customer@example.com', 'customer-demo-salt', 'f3feeab9f8f98e78ad37d0947b9e483a8ace7f05672c2d74d1c4caa60c3ec069', 15000, TRUE),
    ('user-2', 'priya.sharma', 'Priya Sharma', 'priya.sharma@example.com', 'customer-priya-salt', '314d3cea2ffdfaa9f33fc83f0ffc3ddbdd3c16878f931df70a7996e90280ebad', 15000, TRUE),
    ('user-3', 'test.customer', 'Test Customer', 'test.customer@example.com', 'customer-test-salt', 'b53fbb86774f0a9c06d472a1e7eccc66cba28e7444bfa272d2a5f9df84022539', 15000, TRUE);

INSERT INTO chatbot_app.admin_users (
    admin_id,
    full_name,
    password_salt,
    password_hash
)
VALUES
    ('admin-1', 'Support Admin', 'admin-demo-salt', 'ff50ac33dff0cc0f6114b7f5b99598200ce68e9d98231de922d515c94ba084f1');

INSERT INTO chatbot_app.customer_orders (
    order_id,
    user_id,
    total_amount,
    order_time
)
VALUES
    ('ORD-1001', 'user-1', 8000, CURRENT_TIMESTAMP),
    ('ORD-1002', 'user-1', 4500, CURRENT_TIMESTAMP),
    ('ORD-2001', 'user-2', 3500, CURRENT_TIMESTAMP);

INSERT INTO chatbot_app.order_items (
    item_id,
    order_id,
    name,
    price
)
VALUES
    ('ITEM-1', 'ORD-1001', 'Shoes', 2000),
    ('ITEM-2', 'ORD-1001', 'Shirt', 1000),
    ('ITEM-3', 'ORD-1001', 'Watch', 5000),
    ('ITEM-4', 'ORD-1002', 'Backpack', 2500),
    ('ITEM-5', 'ORD-1002', 'Headphones', 2000),
    ('ITEM-6', 'ORD-2001', 'Kurta', 1500),
    ('ITEM-7', 'ORD-2001', 'Sneakers', 2000);
