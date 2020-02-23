--- !Ups

CREATE VIEW trades_sessions_items AS
SELECT s.id AS session, wi.id AS id, wi.name AS name
FROM trades_sessions s
         JOIN trades_skus ts on s.sku = ts.item
         JOIN wow_items wi on ts.item = wi.id;

CREATE VIEW trades_history_view AS
SELECT sku, "user", date, quantity, label
FROM trades_history
WHERE session IS NULL
UNION
SELECT sku, null, MIN(date), SUM(quantity), 'Session #' || session
FROM trades_history
WHERE session IS NOT NULL
GROUP BY sku, session;

CREATE FUNCTION trades_orders_on_insert_create_hold() RETURNS TRIGGER AS
$$
DECLARE
    item_name TEXT;;
BEGIN
    IF NEW.kind = 'bid' THEN
        SELECT SNOWFLAKE() INTO NEW.hold;;
        SELECT name FROM trades_sessions_items WHERE session = NEW.session INTO item_name;;
        INSERT INTO dkp_holds (id, account, amount, label)
        VALUES (NEW.hold,
                NEW.account,
                NEW.quantity * (SELECT sell_price FROM trades_sessions WHERE id = NEW.session),
                'Offre d''achat: [' || item_name || '] x ' || NEW.quantity);;
    END IF;;
    RETURN NEW;;
END;;
$$ LANGUAGE plpgsql;

-- @formatter:off
CREATE TRIGGER trades_orders_on_insert_create_hold BEFORE INSERT ON trades_orders
    FOR EACH ROW EXECUTE PROCEDURE trades_orders_on_insert_create_hold();
-- @formatter:on

CREATE RULE trades_orders_on_insert_update_buy AS ON INSERT TO trades_orders
    WHERE NEW.kind = 'ask'
    DO ALSO UPDATE trades_sessions
            SET buy_orders = buy_orders + NEW.quantity
            WHERE id = NEW.session;

CREATE RULE trades_orders_on_delete_update_buy AS ON DELETE TO trades_orders
    WHERE OLD.kind = 'ask'
    DO ALSO UPDATE trades_sessions
            SET buy_orders = buy_orders - OLD.quantity
            WHERE id = OLD.session;

CREATE RULE trades_orders_on_insert_update_sell AS ON INSERT TO trades_orders
    WHERE NEW.kind = 'bid'
    DO ALSO UPDATE trades_sessions
            SET sell_orders = sell_orders + NEW.quantity
            WHERE id = NEW.session;

CREATE RULE trades_orders_on_delete_update_sell AS ON DELETE TO trades_orders
    WHERE OLD.kind = 'bid'
    DO ALSO UPDATE trades_sessions
            SET sell_orders = sell_orders - OLD.quantity
            WHERE id = OLD.session;

CREATE RULE trades_sessions_on_close_propagate AS ON UPDATE TO trades_sessions
    WHERE OLD.close_date IS NULL AND NEW.close_date IS NOT NULL
    DO ALSO UPDATE trades_orders
            SET closed = true
            WHERE session = NEW.id;

CREATE RULE trades_orders_sync_history AS ON UPDATE TO trades_orders
    WHERE (OLD.ack IS NULL AND NEW.ack = true) AND NEW.close_quantity > 0
    DO ALSO INSERT INTO trades_history (sku, "user", quantity, session, "order")
            VALUES ((SELECT sku FROM trades_sessions WHERE id = NEW.session),
                    NEW.owner,
                    NEW.close_quantity * CASE NEW.kind WHEN 'ask' THEN 1 ELSE -1 END,
                    NEW.session,
                    NEW.id);

CREATE RULE trades_orders_delete_hold AS ON UPDATE TO trades_orders
    WHERE OLD.hold IS NOT NULL AND NEW.ack IS NOT NULL
    DO ALSO DELETE
            FROM dkp_holds
            WHERE id = OLD.hold;

CREATE RULE trades_orders_delete_hold2 AS ON DELETE TO trades_orders
    WHERE OLD.hold IS NOT NULL
    DO ALSO DELETE
            FROM dkp_holds
            WHERE id = OLD.hold;

CREATE RULE trades_orders_create_movement AS ON UPDATE TO trades_orders
    WHERE OLD.ack IS NULL AND NEW.ack = true AND NEW.close_quantity > 0
    DO ALSO INSERT INTO dkp_movements (id, date, account, transaction, label, amount, details, balance, author, item)
            VALUES (snowflake(),
                    NOW(),
                    NEW.account,
                    null,
                    (CASE NEW.kind WHEN 'ask' THEN 'Vente' ELSE 'Achat' END),
                    NEW.close_quantity *
                    (SELECT CASE NEW.kind WHEN 'ask' THEN buy_price ELSE sell_price END
                     FROM trades_sessions
                     WHERE id = NEW.session) *
                    (CASE NEW.kind WHEN 'ask' THEN 1 ELSE -1 END),
                    (CASE NEW.kind WHEN 'ask' THEN 'Vente' ELSE 'Achat' END)
                        || ' de ['
                        || (SELECT i.name
                            FROM trades_sessions s
                                     JOIN wow_items i ON i.id = s.sku
                            WHERE s.id = NEW.session)
                        || '] x '
                        || NEW.close_quantity,
                    0,
                    null,
                    (SELECT sku FROM trades_sessions WHERE id = NEW.session));

CREATE RULE trades_history_on_insert_sync AS ON INSERT TO trades_history
    DO ALSO UPDATE trades_skus
            SET current_supply = current_supply + NEW.quantity
            WHERE item = NEW.sku;

--
-- Trade limit view
--

CREATE VIEW trades_limits AS
WITH config AS
         (
             SELECT (SELECT value FROM trades_config WHERE key = 'individual_buy_limit')  AS ask_limit,
                    (SELECT value FROM trades_config WHERE key = 'individual_sell_limit') AS bid_limit
         ),
     orders AS
         (
             SELECT o.owner,
                    o.kind,
                    SUM(COALESCE(o.close_quantity, o.quantity) *
                        (CASE o.kind WHEN 'ask' THEN ts.buy_price WHEN 'bid' THEN ts.sell_price END))
                        AS amount,
                    ts.buy_price,
                    ts.sell_price
             FROM trades_orders o
                      JOIN trades_sessions ts ON o.session = ts.id
             WHERE NOT o.archived
               AND (o.ack IS NULL OR o.ack = TRUE)
             GROUP BY (o.owner, o.kind, ts.id)
         )
SELECT a.user_id                                   AS user_id,
       a.ask_total                                 AS ask_total,
       GREATEST(0, config.ask_limit - a.ask_total) AS ask_remaining,
       a.bid_total                                 AS bid_total,
       GREATEST(0, config.bid_limit - a.bid_total) AS bid_remaining
FROM (SELECT u.id                                                                              AS user_id,
             (SELECT COALESCE(SUM(amount), 0) FROM orders WHERE owner = u.id AND kind = 'ask') as ask_total,
             (SELECT COALESCE(SUM(amount), 0) FROM orders WHERE owner = u.id AND kind = 'bid') as bid_total
      FROM users u) a,
     config;

--
-- Closes open trade sessions and performs "fair-trading" (tm)
--

CREATE OR REPLACE PROCEDURE trades_close_session()
    LANGUAGE plpgsql
AS
$$
DECLARE
    session_cursor     REFCURSOR;;
    cur_session        RECORD;;
    session_id         SNOWFLAKE;;
    kind_cursor        REFCURSOR;;
    cur_kind           RECORD;;
    session_quantity   INTEGER;;
    round_quantity     INTEGER;;
    round_participants INTEGER;;
BEGIN
    LOCK TABLE trades_orders;;

    OPEN session_cursor FOR SELECT * FROM trades_sessions WHERE close_date IS NULL ORDER BY id;;
    LOOP
        FETCH session_cursor INTO cur_session;;
        EXIT WHEN NOT FOUND;;

        SELECT cur_session.id INTO session_id;;

        -- Create a view filtering only trades for this session
        CREATE TEMPORARY VIEW session_orders AS
        SELECT * FROM trades_orders;;
        DROP VIEW session_orders;;

        EXECUTE 'CREATE TEMPORARY VIEW session_orders AS
        SELECT *
        FROM trades_orders o
        WHERE o.session = ' || session_id;;

        UPDATE session_orders SET close_quantity = 0;;

        -- Iterates over each kind of orders
        OPEN kind_cursor FOR SELECT DISTINCT kind FROM trades_orders o WHERE o.session = session_id;;
        LOOP
            FETCH kind_cursor INTO cur_kind;;
            EXIT WHEN NOT FOUND;;

            -- Select the correct quantity of items available for the session
            SELECT CASE WHEN cur_kind.kind = 'ask' THEN cur_session.buy_quantity ELSE cur_session.sell_quantity END
            INTO session_quantity;;

            -- Running rounds
            LOOP
                -- We'll trade the greatest quantity of item that every participant have available
                SELECT COALESCE(MIN(quantity - close_quantity), 0), COUNT(*)
                FROM session_orders
                WHERE quantity != close_quantity
                  AND kind = cur_kind.kind
                INTO round_quantity, round_participants;;

                -- In case there is not enough item to trade for everyone (last round), only trade the maximum possible
                IF round_quantity * round_participants > session_quantity THEN
                    SELECT FLOOR(session_quantity / round_participants) INTO round_quantity;;
                END IF;;
                EXIT WHEN round_quantity < 1 OR round_participants < 1;;

                -- Decrease this round from the total quantity available
                SELECT session_quantity - (round_quantity * round_participants) INTO session_quantity;;

                -- Update orders
                UPDATE session_orders
                SET close_quantity = close_quantity + round_quantity
                WHERE kind = cur_kind.kind
                  AND close_quantity != quantity;;
            END LOOP;;
        END LOOP;;
        CLOSE kind_cursor;;

        DROP VIEW session_orders;;
        UPDATE trades_sessions SET close_date = NOW() WHERE id = session_id;;
    END LOOP;;
END;;
$$;

--
-- Creates the next trade session
--

CREATE OR REPLACE PROCEDURE trades_next_session()
    LANGUAGE plpgsql
AS
$$
DECLARE
    dkp_per_gold       DOUBLE PRECISION;;
    sell_margin        DOUBLE PRECISION;;
    max_buy_modifier   DOUBLE PRECISION;;
    max_sell_modifier  DOUBLE PRECISION;;
    default_buy_limit  DOUBLE PRECISION;;
    default_sell_limit DOUBLE PRECISION;;
    sku_cursor         REFCURSOR;;
    cur_sku            RECORD;;
    current            INTEGER;;
    target             INTEGER;;
    buy_price          INTEGER;;
    buy_quantity       INTEGER;;
    sell_price         INTEGER;;
    sell_quantity      INTEGER;;
BEGIN
    CALL trades_close_session();;

    SELECT value FROM trades_config WHERE key = 'dkp_per_gold' INTO dkp_per_gold;;
    SELECT value FROM trades_config WHERE key = 'sell_margin' INTO sell_margin;;
    SELECT value FROM trades_config WHERE key = 'max_buy_modifier' INTO max_buy_modifier;;
    SELECT value FROM trades_config WHERE key = 'max_sell_modifier' INTO max_sell_modifier;;
    SELECT value FROM trades_config WHERE key = 'default_buy_limit' INTO default_buy_limit;;
    SELECT value FROM trades_config WHERE key = 'default_sell_limit' INTO default_sell_limit;;

    OPEN sku_cursor FOR SELECT * FROM trades_skus WHERE (buying OR selling) AND gold_price IS NOT NULL;;
    LOOP
        FETCH sku_cursor INTO cur_sku;;
        EXIT WHEN NOT FOUND;;

        SELECT cur_sku.current_supply, cur_sku.target_supply INTO current,target;;
        SELECT 0, 0, 0, 0 INTO buy_price, buy_quantity, sell_price, sell_quantity;;

        IF cur_sku.buying THEN
            SELECT GREATEST(0, LEAST(target - current,
                                     COALESCE(cur_sku.buy_limit, default_buy_limit * target)))
            INTO buy_quantity;;

            SELECT (cur_sku.gold_price * dkp_per_gold) *
                   (1 + (1 - (current::DOUBLE PRECISION) / target) * (COALESCE(cur_sku.max_buy_modifier, max_buy_modifier) - 1))
            INTO buy_price;;
        END IF;;

        IF cur_sku.selling THEN
            SELECT GREATEST(0, LEAST(current,
                                     COALESCE(cur_sku.sell_limit, default_sell_limit * target)))
            INTO sell_quantity;;

            SELECT (cur_sku.gold_price * dkp_per_gold) *
                   (1 + sell_margin + (1 - (current::double precision) / target) * (COALESCE(cur_sku.max_sell_modifier, max_sell_modifier) - 1))
            INTO sell_price;;
        END IF;;

        IF buy_quantity > 0 OR sell_quantity > 0 THEN
            INSERT INTO trades_sessions (sku, open_date, buy_price, buy_quantity, sell_price, sell_quantity)
            VALUES (cur_sku.item,
                    NOW(),
                    buy_price,
                    buy_quantity,
                    sell_price,
                    sell_quantity);;
        END IF;;
    END LOOP;;
END;;
$$;

--- !Downs

DROP RULE trades_sessions_on_close_propagate ON trades_sessions;
DROP RULE trades_orders_on_insert_update_buy ON trades_orders;
DROP RULE trades_orders_on_delete_update_buy ON trades_orders;
DROP RULE trades_orders_on_insert_update_sell ON trades_orders;
DROP RULE trades_orders_on_delete_update_sell ON trades_orders;
DROP RULE trades_orders_sync_history ON trades_orders;
DROP RULE trades_orders_delete_hold ON trades_orders;
DROP RULE trades_orders_delete_hold2 ON trades_orders;
DROP RULE trades_orders_create_movement ON trades_orders;
DROP RULE trades_history_on_insert_sync ON trades_history;

DROP PROCEDURE trades_next_session;
DROP PROCEDURE trades_close_session;

DROP VIEW trades_limits;
DROP VIEW trades_sessions_items;
DROP VIEW trades_history_view;

DROP TRIGGER trades_orders_on_insert_create_hold ON trades_orders;
DROP FUNCTION trades_orders_on_insert_create_hold();
