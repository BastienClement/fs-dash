-- !Ups

CREATE FUNCTION dkp_movement_before() RETURNS trigger AS
$$
BEGIN
    SELECT a.balance + NEW.amount INTO NEW.balance FROM dkp_accounts a WHERE a.id = NEW.account LIMIT 1 FOR UPDATE;;
    SELECT localtimestamp INTO NEW.date;;
    RETURN NEW;;
END;;
$$ LANGUAGE plpgsql;

CREATE TRIGGER dkp_movement_before
    BEFORE INSERT
    ON dkp_movements
    FOR EACH ROW
EXECUTE PROCEDURE dkp_movement_before();

CREATE FUNCTION dkp_movement_after() RETURNS trigger AS
$$
BEGIN
    UPDATE dkp_accounts SET balance = NEW.balance WHERE id = NEW.account;;
    RETURN NULL;;
END;;
$$ LANGUAGE plpgsql;

CREATE TRIGGER dkp_movement_after
    AFTER INSERT
    ON dkp_movements
    FOR EACH ROW
EXECUTE PROCEDURE dkp_movement_after();

-- !Downs

DROP TRIGGER dkp_movement_before ON dkp_movements;
DROP FUNCTION dkp_movement_before();

DROP TRIGGER dkp_movement_after ON dkp_movements;
DROP FUNCTION dkp_movement_after();
