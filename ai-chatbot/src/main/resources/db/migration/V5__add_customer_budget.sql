ALTER TABLE ecommerce_users
    ADD COLUMN shopping_budget NUMERIC(38, 2);

UPDATE ecommerce_users
SET shopping_budget = 15000
WHERE shopping_budget IS NULL;

ALTER TABLE ecommerce_users
    ALTER COLUMN shopping_budget SET NOT NULL;

CREATE UNIQUE INDEX idx_ecommerce_users_email
    ON ecommerce_users (email);
