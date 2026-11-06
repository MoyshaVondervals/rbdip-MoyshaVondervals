SET LOCAL lock_timeout = '2s';

ALTER TABLE customers ADD COLUMN first_name VARCHAR(255), ADD COLUMN last_name VARCHAR(255);

CREATE FUNCTION sync_customer_name() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    legacy_changed BOOLEAN;
    parts_changed BOOLEAN;
    separator INTEGER;
BEGIN
    IF TG_OP = 'INSERT' THEN
        legacy_changed := NEW.first_name IS NULL;
        parts_changed := NEW.first_name IS NOT NULL;
    ELSE
        legacy_changed := NEW.customer_full_name IS DISTINCT FROM OLD.customer_full_name;
        parts_changed := NEW.first_name IS DISTINCT FROM OLD.first_name
                      OR NEW.last_name IS DISTINCT FROM OLD.last_name;
    END IF;

    IF legacy_changed OR NEW.first_name IS NULL THEN
        IF parts_changed AND NEW.customer_full_name IS DISTINCT FROM
                (NEW.first_name || CASE WHEN NEW.last_name IS NULL THEN '' ELSE ' ' || NEW.last_name END) THEN
            RAISE EXCEPTION 'Conflicting names' USING ERRCODE = '23514';
        END IF;
        separator := strpos(NEW.customer_full_name, ' ');
        NEW.first_name := CASE WHEN separator = 0 THEN NEW.customer_full_name
                              ELSE left(NEW.customer_full_name, separator - 1) END;
        NEW.last_name := CASE WHEN separator = 0 THEN NULL
                             ELSE substr(NEW.customer_full_name, separator + 1) END;
    ELSE
        IF TG_OP = 'INSERT' AND NEW.customer_full_name IS NOT NULL
                AND NEW.customer_full_name IS DISTINCT FROM
                    (NEW.first_name || CASE WHEN NEW.last_name IS NULL THEN '' ELSE ' ' || NEW.last_name END) THEN
            RAISE EXCEPTION 'Conflicting names' USING ERRCODE = '23514';
        END IF;
        NEW.customer_full_name := NEW.first_name
                || CASE WHEN NEW.last_name IS NULL THEN '' ELSE ' ' || NEW.last_name END;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER customers_sync_name BEFORE INSERT OR UPDATE ON customers
FOR EACH ROW EXECUTE FUNCTION sync_customer_name();
