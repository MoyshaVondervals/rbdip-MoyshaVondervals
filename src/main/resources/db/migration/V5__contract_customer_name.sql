SET LOCAL lock_timeout = '2s';

ALTER TABLE customers ALTER COLUMN first_name SET NOT NULL;
ALTER TABLE customers DROP CONSTRAINT customers_first_name_required;
ALTER TABLE customers ADD CONSTRAINT customers_contact_parts_key
    UNIQUE USING INDEX customers_contact_parts_key;

DROP TRIGGER customers_sync_name ON customers;
DROP FUNCTION sync_customer_name();
ALTER TABLE customers DROP COLUMN customer_full_name;
