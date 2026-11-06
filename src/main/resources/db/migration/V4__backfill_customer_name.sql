SET LOCAL lock_timeout = '2s';

UPDATE customers SET first_name = CASE
        WHEN strpos(customer_full_name, ' ') = 0 THEN customer_full_name
        ELSE left(customer_full_name, strpos(customer_full_name, ' ') - 1) END,
    last_name = CASE WHEN strpos(customer_full_name, ' ') = 0 THEN NULL
        ELSE substr(customer_full_name, strpos(customer_full_name, ' ') + 1) END
WHERE first_name IS NULL;

ALTER TABLE customers ADD CONSTRAINT customers_first_name_required
    CHECK (first_name IS NOT NULL) NOT VALID;
ALTER TABLE customers VALIDATE CONSTRAINT customers_first_name_required;

ALTER TABLE customers ADD CONSTRAINT customers_name_length
    CHECK (length(first_name || CASE WHEN last_name IS NULL THEN '' ELSE ' ' || last_name END) <= 255) NOT VALID;
ALTER TABLE customers VALIDATE CONSTRAINT customers_name_length;

CREATE UNIQUE INDEX customers_contact_parts_key
    ON customers (first_name, last_name, customer_address, customer_phone) NULLS NOT DISTINCT;
