CREATE TABLE customers (
    id BIGSERIAL PRIMARY KEY,
    customer_full_name VARCHAR(255) NOT NULL,
    customer_address VARCHAR(500),
    customer_phone VARCHAR(50),
    CONSTRAINT customers_contact_key UNIQUE NULLS NOT DISTINCT
        (customer_full_name, customer_address, customer_phone)
);

INSERT INTO customers (customer_full_name, customer_address, customer_phone)
SELECT DISTINCT customer_full_name, customer_address, customer_phone FROM orders;

ALTER TABLE orders ADD COLUMN customer_id BIGINT;
UPDATE orders o SET customer_id = c.id
FROM customers c
WHERE o.customer_full_name = c.customer_full_name
  AND o.customer_address IS NOT DISTINCT FROM c.customer_address
  AND o.customer_phone IS NOT DISTINCT FROM c.customer_phone;
ALTER TABLE orders ALTER COLUMN customer_id SET NOT NULL;
ALTER TABLE orders ADD CONSTRAINT orders_customer_fk
    FOREIGN KEY (customer_id) REFERENCES customers(id);
ALTER TABLE orders DROP COLUMN customer_full_name,
    DROP COLUMN customer_address, DROP COLUMN customer_phone;


INSERT INTO products (name, price)
SELECT DISTINCT i.product_name, i.product_price FROM order_items i
WHERE NOT EXISTS (
    SELECT 1 FROM products p
    WHERE p.name = i.product_name AND p.price = i.product_price
);

ALTER TABLE order_items ADD COLUMN product_id BIGINT;
UPDATE order_items i SET product_id = (
    SELECT MIN(p.id) FROM products p
    WHERE p.name = i.product_name AND p.price = i.product_price
);
ALTER TABLE order_items ALTER COLUMN product_id SET NOT NULL;
ALTER TABLE order_items ADD CONSTRAINT order_items_product_fk
    FOREIGN KEY (product_id) REFERENCES products(id);
ALTER TABLE order_items DROP COLUMN product_name, DROP COLUMN product_price;
