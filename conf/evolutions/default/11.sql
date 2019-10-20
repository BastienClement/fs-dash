-- !Ups

-- DKP ACCOUNT LINKING
ALTER TABLE dkp_accounts
    ADD COLUMN use_decay BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN overdraft INTEGER NOT NULL DEFAULT 0;

CREATE TABLE dkp_accounts_accesses
(
    owner   SNOWFLAKE NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    account SNOWFLAKE NOT NULL REFERENCES dkp_accounts (id) ON DELETE CASCADE,
    main    BOOLEAN   NOT NULL DEFAULT FALSE,
    PRIMARY KEY (owner, account)
);

CREATE UNIQUE INDEX dkp_accounts_accesses_main_key ON dkp_accounts_accesses (owner) WHERE main = true;

-- DKP ACCOUNT HOLDS
CREATE TABLE dkp_holds
(
    id      SNOWFLAKE PRIMARY KEY DEFAULT SNOWFLAKE(),
    account SNOWFLAKE NOT NULL REFERENCES dkp_accounts (id) ON DELETE CASCADE,
    amount  INTEGER   NOT NULL,
    label   TEXT      NOT NULL
);

CREATE INDEX dkp_holds_account_idx ON dkp_holds (account);

CREATE RULE dkp_holds_on_update_delete_zero AS ON UPDATE TO dkp_holds
    WHERE NEW.amount <= 0
    DO INSTEAD DELETE
               FROM dkp_holds
               WHERE id = NEW.id;

CREATE VIEW dkp_accounts_view AS
SELECT *, (SELECT COALESCE(SUM(amount), 0) FROM dkp_holds h WHERE h.account = a.id) AS holds
FROM dkp_accounts a;

-- WOW ITEM TABLE
CREATE TABLE wow_items
(
    id      INTEGER PRIMARY KEY,
    name    TEXT      NOT NULL,
    quality INTEGER   NOT NULL,
    icon    TEXT      NOT NULL,
    fetched TIMESTAMP NOT NULL DEFAULT NOW(),
    stale   BOOLEAN   NOT NULL DEFAULT FALSE,
    data    JSON      NOT NULL
);

-- AUCTIONS ORDERS
CREATE TYPE ORDER_TYPE AS ENUM ('bid', 'ask');

CREATE FUNCTION auctions_orders_matches_mat_refresh() RETURNS TRIGGER AS
$$
BEGIN
    REFRESH MATERIALIZED VIEW auctions_orders_matches_mat;;
    RETURN NULL;;
END;;
$$ LANGUAGE plpgsql;

CREATE TABLE auctions_orders
(
    id       SNOWFLAKE PRIMARY KEY DEFAULT SNOWFLAKE(),
    type     ORDER_TYPE NOT NULL,
    owner    SNOWFLAKE REFERENCES users (id),
    account  SNOWFLAKE REFERENCES dkp_accounts (id),
    item     INTEGER    NOT NULL REFERENCES wow_items (id),
    quantity INTEGER    NOT NULL CHECK ( quantity > 0 ),
    price    INTEGER    NOT NULL CHECK ( price > 0 ),
    hold     SNOWFLAKE  REFERENCES dkp_holds (id) ON DELETE SET NULL,
    posted   TIMESTAMP  NOT NULL   DEFAULT NOW(),
    validity TIMESTAMP  NOT NULL   DEFAULT NOW(),
    closed   TIMESTAMP,
    CHECK ( (owner IS NULL) = (account IS NULL) )
);

CREATE INDEX auctions_orders_open_type_item_validity_idx ON auctions_orders (type, item, validity) WHERE closed IS NULL;

CREATE FUNCTION auctions_orders_on_insert_create_hold() RETURNS TRIGGER AS
$$
DECLARE
    item_name TEXT;;
BEGIN
    IF NEW.type = 'bid' AND (NEW.account IS NOT NULL) THEN
        SELECT SNOWFLAKE() INTO NEW.hold;;
        SELECT name FROM wow_items WHERE id = NEW.item INTO item_name;;
        INSERT INTO dkp_holds (id, account, amount, label)
        VALUES (NEW.hold, NEW.account, NEW.quantity * NEW.price, 'Offre d''achat: [' || item_name || '] x ' || NEW.quantity);;
    END IF;;
    RETURN NEW;;
END;;
$$ LANGUAGE plpgsql;

-- @formatter:off
CREATE TRIGGER auctions_orders_on_insert_create_hold BEFORE INSERT ON auctions_orders
    FOR EACH ROW EXECUTE PROCEDURE auctions_orders_on_insert_create_hold();

CREATE RULE auctions_orders_on_close_cascade_hold AS ON UPDATE TO auctions_orders
    WHERE (NEW.closed IS NOT NULL) AND (NEW.hold IS NOT NULL)
    DO ALSO DELETE FROM dkp_holds WHERE id = NEW.hold;

CREATE RULE auctions_orders_on_delete_cascade_hold AS ON DELETE TO auctions_orders
    WHERE OLD.hold IS NOT NULL
    DO ALSO DELETE FROM dkp_holds WHERE id = OLD.hold;

CREATE TRIGGER auctions_orders_refresh AFTER INSERT OR UPDATE OR DELETE ON auctions_orders
    FOR EACH STATEMENT EXECUTE PROCEDURE auctions_orders_matches_mat_refresh();
-- @formatter:on

-- AUCTIONS MATCHES
CREATE TABLE auctions_matches
(
    id         SNOWFLAKE PRIMARY KEY DEFAULT SNOWFLAKE(),
    bid        SNOWFLAKE NOT NULL REFERENCES auctions_orders (id) ON DELETE CASCADE,
    ask        SNOWFLAKE NOT NULL REFERENCES auctions_orders (id) ON DELETE CASCADE,
    quantity   INTEGER   NOT NULL CHECK ( quantity > 0 ),
    price      INTEGER   NOT NULL CHECK ( price > 0 ),
    matched    TIMESTAMP NOT NULL    DEFAULT NOW(),
    bid_hold   SNOWFLAKE REFERENCES dkp_holds (id) ON DELETE SET NULL,
    ack_status BOOLEAN,
    ack_date   TIMESTAMP
);

CREATE INDEX auctions_matches_bid_idx ON auctions_matches (bid);
CREATE INDEX auctions_matches_ask_idx ON auctions_matches (ask);

CREATE FUNCTION auctions_matches_on_before_insert() RETURNS TRIGGER AS
$$
DECLARE
    bid_account SNOWFLAKE;;
    item_name   TEXT;;
BEGIN
    -- Create hold for bidder and subtract form offer hold
    SELECT account FROM auctions_orders WHERE id = NEW.bid INTO bid_account;;
    IF bid_account IS NOT NULL THEN
        SELECT snowflake() INTO NEW.bid_hold;;
        SELECT name FROM wow_items WHERE id = (SELECT item FROM auctions_orders WHERE id = NEW.bid) INTO item_name;;

        -- Create hold
        INSERT INTO dkp_holds (id, account, amount, label)
        VALUES (NEW.bid_hold, bid_account, NEW.quantity * NEW.price, 'Achat: [' || item_name || '] x ' || NEW.quantity);;

        -- Decrease offer hold
        UPDATE dkp_holds
        SET amount = amount - (NEW.quantity * NEW.price)
        WHERE id = (SELECT hold FROM auctions_orders WHERE id = NEW.bid);;
    END IF;;
    RETURN NEW;;
END;;
$$ LANGUAGE plpgsql;

CREATE FUNCTION auctions_matches_on_after_insert() RETURNS TRIGGER AS
$$
DECLARE
    bid_remaining INTEGER;;
    ask_remaining INTEGER;;
BEGIN
    -- Close bid if nothing left remains
    SELECT remaining FROM auctions_orders_view WHERE id = NEW.bid INTO bid_remaining;;
    IF bid_remaining <= 0 THEN
        UPDATE auctions_orders SET closed = NOW() WHERE id = NEW.bid;;
    END IF;;

    -- Close ask if nothing left remains
    SELECT remaining FROM auctions_orders_view WHERE id = NEW.ask INTO ask_remaining;;
    IF ask_remaining <= 0 THEN
        UPDATE auctions_orders SET closed = NOW() WHERE id = NEW.ask;;
    END IF;;

    RETURN NULL;;
END;;
$$ LANGUAGE plpgsql;

CREATE FUNCTION auctions_matches_on_update() RETURNS TRIGGER AS
$$
DECLARE
    bid_account SNOWFLAKE;;
    bid_name    TEXT;;
    ask_account SNOWFLAKE;;
    ask_name    TEXT;;
    item_id     INTEGER;;
    item_name   TEXT;;
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
                SELECT username FROM users WHERE id = (SELECT owner FROM auctions_orders WHERE id = NEW.bid) INTO bid_name;;
                IF bid_name IS NULL THEN SELECT '(From Scratch)' INTO bid_name;; END IF;;

                INSERT INTO dkp_movements (id, date, account, transaction, label, amount, balance, details, author, item)
                VALUES (SNOWFLAKE(), NOW(), ask_account, NULL, 'Vente', NEW.price * NEW.quantity, 0,
                        'Vente de [' || item_name || '] x ' || NEW.quantity || ' à ' || bid_name, NULL, item_id);;
            END IF;;
        END IF;;
    END IF;;
    RETURN NULL;;
END;;
$$ LANGUAGE plpgsql;

-- @formatter:off
CREATE TRIGGER auctions_matches_on_before_insert BEFORE INSERT ON auctions_matches
    FOR EACH ROW EXECUTE PROCEDURE auctions_matches_on_before_insert();

CREATE TRIGGER auctions_matches_on_after_insert AFTER INSERT ON auctions_matches
    FOR EACH ROW EXECUTE PROCEDURE auctions_matches_on_after_insert();

CREATE TRIGGER auctions_matches_on_update AFTER UPDATE ON auctions_matches
    FOR EACH ROW EXECUTE PROCEDURE auctions_matches_on_update();

CREATE TRIGGER auctions_matches_refresh AFTER INSERT OR UPDATE OR DELETE ON auctions_matches
    FOR EACH STATEMENT EXECUTE PROCEDURE auctions_orders_matches_mat_refresh();
-- @formatter:on

-- AUCTIONS VIEWS

CREATE VIEW auctions_orders_view AS
SELECT *,
       (CASE WHEN execution_order IS NULL THEN NULL
             ELSE GREATEST(a.validity, (SELECT validity
                                        FROM auctions_orders
                                        WHERE id = execution_order))
                 + INTERVAL '5 minutes'
        END) AS execution
FROM (SELECT *,
             quantity
                 - COALESCE((SELECT SUM(quantity) FROM auctions_matches WHERE bid = o.id), 0)
                 - COALESCE((SELECT SUM(quantity) FROM auctions_matches WHERE ask = o.id), 0)
                 AS remaining,
             (WITH matching_orders AS (
                 SELECT *
                 FROM auctions_orders i
                 WHERE i.closed IS NULL
                   AND ((i.owner IS NULL) != (o.owner IS NULL) OR i.owner != o.owner)
                   AND i.type = (CASE WHEN o.type = 'bid' THEN 'ask' WHEN o.type = 'ask' THEN 'bid' END)::ORDER_TYPE
                   AND i.item = o.item
                   AND i.validity <= GREATEST(o.validity, NOW() + INTERVAL '5 minutes')
                   AND CASE WHEN o.type = 'bid' THEN i.price <= o.price WHEN o.type = 'ask' THEN i.price >= o.price END
             )
              SELECT id
              FROM matching_orders
              WHERE price = (SELECT (CASE WHEN o.type = 'bid' THEN MIN(price) WHEN o.type = 'ask' THEN MAX(price) END)
                             FROM matching_orders)
              ORDER BY posted, id
              LIMIT 1)
                 AS execution_order
      FROM auctions_orders o) a;

CREATE VIEW auctions_orders_matches AS
SELECT *,
       (CASE WHEN ask_count = 1 AND bid_count = 1 THEN
                 CASE WHEN ask_posted < bid_posted THEN ask_price ELSE bid_price END
             WHEN ask_count = 1 AND bid_count > 1 THEN
                 bid_price
             WHEN ask_count > 1 AND bid_count = 1 THEN
                 ask_price
             ELSE
                 CASE WHEN ask_posted < bid_posted THEN bid_price ELSE ask_price END
        END) AS execution_price
FROM (SELECT ask.id                                 AS ask,
             bid.id                                 AS bid,
             ask.item                               AS item,
             LEAST(ask.remaining, bid.remaining)    AS quantity,
             ask.price                              AS ask_price,
             ask.posted                             AS ask_posted,
             (SELECT COUNT(*)
              FROM auctions_orders_view
              WHERE type = 'ask'
                AND item = bid.item
                AND execution_order = bid.id
                AND posted <= ask.posted
                AND closed IS NULL)                 AS ask_count,
             bid.price                              AS bid_price,
             bid.posted                             AS bid_posted,
             (SELECT COUNT(*)
              FROM auctions_orders_view
              WHERE type = 'bid'
                AND item = ask.item
                AND execution_order = ask.id
                AND posted <= bid.posted
                AND closed IS NULL)                 AS bid_count,
             GREATEST(ask.execution, bid.execution) AS execution
      FROM auctions_orders_view ask,
           auctions_orders_view bid
      WHERE ask.remaining > 0
        AND bid.remaining > 0
        AND ask.id = bid.execution_order
        AND bid.id = ask.execution_order
        AND ask.type = 'ask'
        AND ask.closed IS NULL
        and bid.closed IS NULL) m;

CREATE MATERIALIZED VIEW auctions_orders_matches_mat AS
SELECT *
FROM auctions_orders_matches;

CREATE VIEW auctions_orders_overview AS
    WITH open_orders AS (SELECT *,
                                quantity
                                    - COALESCE((SELECT SUM(quantity) FROM auctions_matches WHERE bid = o.id), 0)
                                    - COALESCE((SELECT SUM(quantity) FROM auctions_matches WHERE ask = o.id), 0)
                                    AS remaining
                         FROM auctions_orders o
                         WHERE closed IS NULL)
    SELECT item,
           (SELECT COALESCE(SUM(remaining), 0) FROM open_orders i WHERE type = 'bid' AND i.item = o.item) AS bid_unit,
           (SELECT MAX(price) FROM open_orders i WHERE type = 'bid' AND i.item = o.item)                  AS bid_price,
           (SELECT COALESCE(SUM(remaining), 0) FROM open_orders i WHERE type = 'ask' AND i.item = o.item) AS ask_unit,
           (SELECT MIN(price) FROM open_orders i WHERE type = 'ask' AND i.item = o.item)                  AS ask_price
    FROM open_orders o
    GROUP BY item;

-- !Downs

DROP VIEW auctions_orders_overview;

DROP MATERIALIZED VIEW auctions_orders_matches_mat;

DROP VIEW auctions_orders_matches;
DROP VIEW auctions_orders_view;
DROP VIEW dkp_accounts_view;

DROP TABLE auctions_matches;
DROP FUNCTION auctions_matches_on_before_insert();
DROP FUNCTION auctions_matches_on_after_insert();
DROP FUNCTION auctions_matches_on_update();

DROP TABLE auctions_orders;
DROP FUNCTION auctions_orders_on_insert_create_hold();

DROP TABLE wow_items;

DROP TABLE dkp_holds;

DROP TABLE dkp_accounts_accesses;
ALTER TABLE dkp_accounts
    DROP COLUMN use_decay,
    DROP COLUMN overdraft;

DROP FUNCTION auctions_orders_matches_mat_refresh();
DROP TYPE ORDER_TYPE;
