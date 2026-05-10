ALTER TABLE ecommerce_users
    ADD COLUMN email_verified BOOLEAN DEFAULT FALSE;

ALTER TABLE ecommerce_users
    ADD COLUMN email_verification_code_hash VARCHAR(255);

ALTER TABLE ecommerce_users
    ADD COLUMN email_verification_expires_at TIMESTAMP WITH TIME ZONE;

UPDATE ecommerce_users
SET email_verified = TRUE
WHERE email_verified IS NULL
   OR username IN ('demo.customer', 'priya.sharma');

ALTER TABLE ecommerce_users
    ALTER COLUMN email_verified SET NOT NULL;
