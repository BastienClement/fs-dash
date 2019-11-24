-- !Ups

INSERT INTO dkp_decay_config (key, value) VALUES ('trade_tax', 0.075);

INSERT INTO wow_items (id, name, quality, icon, fetched, stale, data)
VALUES (0, 'Golds', 1, 'inv_misc_coin_01', NOW(), false, 'null');

CREATE OR REPLACE FUNCTION auctions_matches_on_update() RETURNS TRIGGER AS
$$
DECLARE
    bid_account SNOWFLAKE;;
    bid_name    TEXT;;
    ask_account SNOWFLAKE;;
    ask_name    TEXT;;
    item_id     INTEGER;;
    item_name   TEXT;;
    trade_tax   DOUBLE PRECISION;;
BEGIN
    -- If matching was just ack-ed by an officer
    IF (OLD.ack_status IS NULL) AND (NEW.ack_status IS NOT NULL) THEN
        -- Remove hold for the bidder
        IF NEW.bid_hold IS NOT NULL THEN
            DELETE FROM dkp_holds WHERE id = NEW.bid_hold;;
        END IF;;

        -- This match was successfully executed
        IF NEW.ack_status = true THEN
            SELECT account FROM auctions_orders WHERE id = NEW.bid INTO bid_account;;
            SELECT account FROM auctions_orders WHERE id = NEW.ask INTO ask_account;;

            SELECT item FROM auctions_orders WHERE id = NEW.bid INTO item_id;;
            SELECT name FROM wow_items WHERE id = item_id INTO item_name;;

            IF bid_account IS NOT NULL THEN
                SELECT username FROM users WHERE id = (SELECT owner FROM auctions_orders WHERE id = NEW.ask) INTO ask_name;;
                IF ask_name IS NULL THEN SELECT '(From Scratch)' INTO ask_name;; END IF;;

                INSERT INTO dkp_movements (id, date, account, transaction, label, amount, balance, details, author, item)
                VALUES (SNOWFLAKE(), NOW(), bid_account, NULL, 'Achat', -NEW.price * NEW.quantity, 0,
                        'Achat de [' || item_name || '] x ' || NEW.quantity || ' à ' || ask_name, NULL, item_id);;
            END IF;;

            IF ask_account IS NOT NULL THEN
                SELECT value FROM dkp_decay_config WHERE key = 'trade_tax' INTO trade_tax;;

                SELECT username FROM users WHERE id = (SELECT owner FROM auctions_orders WHERE id = NEW.bid) INTO bid_name;;
                IF bid_name IS NULL THEN
                    SELECT '(From Scratch)' INTO bid_name;;
                    SELECT 0 INTO trade_tax;;
                END IF;;

                INSERT INTO dkp_movements (id, date, account, transaction, label, amount, balance, details, author, item)
                VALUES (SNOWFLAKE(), NOW(), ask_account, NULL, 'Vente', NEW.price * NEW.quantity * (1 - trade_tax), 0,
                        'Vente de [' || item_name || '] x ' || NEW.quantity || ' à ' || bid_name, NULL, item_id);;
            END IF;;
        END IF;;
    END IF;;
    RETURN NULL;;
END;;
$$ LANGUAGE plpgsql;

-- !Downs

DELETE FROM dkp_decay_config WHERE key = 'trade_tax';
DELETE FROM wow_items WHERE id = 0;
