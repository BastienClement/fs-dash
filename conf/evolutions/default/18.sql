--- !Ups

CREATE TABLE trades_config
(
    key   TEXT PRIMARY KEY,
    value DOUBLE PRECISION NOT NULL
);

INSERT INTO trades_config
VALUES ('dkp_per_gold', 1.0),
       ('sell_margin', 0.50),
       ('max_buy_modifier', 1.5),
       ('max_sell_modifier', 1.25),
       ('default_buy_limit', 0.25),
       ('default_sell_limit', 0.125),
       ('individual_buy_limit', 7500),
       ('individual_sell_limit', 10000);

CREATE TABLE trades_skus
(
    item              INTEGER PRIMARY KEY REFERENCES wow_items (id),
    buying            BOOLEAN NOT NULL,
    selling           BOOLEAN NOT NULL,
    target_supply     INTEGER NOT NULL CHECK ( target_supply >= 0 ),
    current_supply    INTEGER NOT NULL DEFAULT 0 CHECK ( current_supply >= 0 ),
    gold_price        INTEGER          DEFAULT NULL CHECK (gold_price >= 0),
    last_update       TIMESTAMP        DEFAULT NULL,
    max_buy_modifier  DOUBLE PRECISION DEFAULT NULL,
    max_sell_modifier DOUBLE PRECISION DEFAULT NULL,
    buy_limit         INTEGER          DEFAULT NULL,
    sell_limit        INTEGER          DEFAULT NULL
);

INSERT INTO trades_skus (item, buying, selling, target_supply, gold_price)
VALUES (0, false, false, 0, 100);

CREATE TABLE trades_sessions
(
    id            SNOWFLAKE PRIMARY KEY DEFAULT snowflake(),
    sku           INTEGER   NOT NULL REFERENCES trades_skus (item),
    open_date     TIMESTAMP NOT NULL,
    close_date    TIMESTAMP             DEFAULT NULL,
    buy_price     INTEGER   NOT NULL CHECK ( buy_price >= 0 ),
    buy_quantity  INTEGER   NOT NULL CHECK ( buy_quantity >= 0 ),
    buy_orders    INTEGER   NOT NULL    DEFAULT 0 CHECK ( buy_orders >= 0 ),
    sell_price    INTEGER   NOT NULL CHECK ( sell_price >= 0 ),
    sell_quantity INTEGER   NOT NULL CHECK ( sell_quantity >= 0 ),
    sell_orders   INTEGER   NOT NULL    DEFAULT 0 CHECK ( sell_orders >= 0 ),
    CHECK ( buy_quantity > 0 OR sell_quantity > 0 )
);

CREATE UNIQUE INDEX trades_sessions_open_sku_key ON trades_sessions (sku) WHERE close_date IS NULL;

CREATE TABLE trades_orders
(
    id             SNOWFLAKE PRIMARY KEY DEFAULT snowflake(),
    session        SNOWFLAKE  NOT NULL REFERENCES trades_sessions (id) ON DELETE CASCADE,
    owner          SNOWFLAKE  NOT NULL REFERENCES users (id),
    account        SNOWFLAKE  NOT NULL REFERENCES dkp_accounts (id),
    kind           ORDER_TYPE NOT NULL,
    quantity       INTEGER    NOT NULL CHECK ( quantity > 0 ),
    hold           SNOWFLAKE  REFERENCES dkp_holds (id) ON DELETE SET NULL,
    closed         BOOLEAN    NOT NULL   DEFAULT FALSE,
    close_quantity INTEGER               DEFAULT NULL CHECK ( close_quantity >= 0 ),
    ack            BOOLEAN               DEFAULT NULL,
    ack_by         SNOWFLAKE             DEFAULT NULL REFERENCES users (id) ON DELETE SET NULL,
    archived       BOOLEAN    NOT NULL   DEFAULT FALSE,
    CHECK ( (close_quantity IS NULL) OR (close_quantity <= quantity) ),
    CHECK ( (ack IS NULL) = (ack_by IS NULL) ),
    UNIQUE (owner, kind, session)
);

CREATE TABLE trades_history
(
    id       SNOWFLAKE PRIMARY KEY DEFAULT snowflake(),
    sku      INTEGER   NOT NULL REFERENCES trades_skus (item) ON DELETE CASCADE,
    "user"   SNOWFLAKE NOT NULL REFERENCES users (id),
    date     TIMESTAMP NOT NULL    DEFAULT NOW(),
    quantity INTEGER   NOT NULL CHECK ( quantity != 0 ),
    session  SNOWFLAKE REFERENCES trades_sessions (id),
    "order"  SNOWFLAKE REFERENCES trades_orders (id),
    label    TEXT      NOT NULL    DEFAULT '',
    CHECK ( (session IS NULL) = ("order" IS NULL) )
);

CREATE INDEX trades_history_date_key ON trades_history (date);

--- !Downs

DROP TABLE trades_history;
DROP TABLE trades_orders;
DROP TABLE trades_sessions;
DROP TABLE trades_skus;
DROP TABLE trades_config;
