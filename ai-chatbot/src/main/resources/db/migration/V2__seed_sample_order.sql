INSERT INTO customer_orders (order_id, user_id, total_amount)
VALUES ('ORD-1001', 'user-1', 8000);

INSERT INTO order_items (item_id, order_id, name, price)
VALUES
    ('ITEM-1', 'ORD-1001', 'Shoes', 2000),
    ('ITEM-2', 'ORD-1001', 'Shirt', 1000),
    ('ITEM-3', 'ORD-1001', 'Watch', 5000);
