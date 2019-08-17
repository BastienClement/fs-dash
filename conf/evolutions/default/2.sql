-- !Ups

CREATE TABLE dkp_accounts
(
    id        SNOWFLAKE PRIMARY KEY,
    label     TEXT    NOT NULL,
    balance   INTEGER NOT NULL DEFAULT 0,
    use_decay BOOLEAN NOT NULL DEFAULT 'no'
);

CREATE TABLE dkp_transactions
(
    id      SNOWFLAKE PRIMARY KEY,
    label   TEXT NOT NULL,
    details TEXT NOT NULL
);

CREATE TABLE dkp_movements
(
    id          SNOWFLAKE PRIMARY KEY,
    date        TIMESTAMP NOT NULL,
    account     SNOWFLAKE NOT NULL REFERENCES dkp_accounts (id) ON DELETE CASCADE,
    transaction SNOWFLAKE REFERENCES dkp_transactions (id) ON DELETE RESTRICT,
    label       TEXT      NOT NULL,
    amount      INTEGER   NOT NULL,
    balance     INTEGER   NOT NULL,
    details     TEXT      NOT NULL,
    author      SNOWFLAKE REFERENCES users (id) ON DELETE SET NULL,
    item        INTEGER
);

CREATE INDEX dkp_movements_date_idx ON dkp_movements (date);
CREATE INDEX dkp_movements_account_date_idx ON dkp_movements (account, date);
CREATE INDEX dkp_movements_item_date_idx ON dkp_movements (item, date) WHERE item IS NOT NULL;

CREATE INDEX dkp_movements_transaction_fkey ON dkp_movements (transaction) WHERE transaction IS NOT NULL;
CREATE INDEX dkp_movements_author_fkey ON dkp_movements (author) WHERE author IS NOT NULL;

-- !Downs

DROP TABLE dkp_movements;
DROP TABLE dkp_transactions;
DROP TABLE dkp_accounts;

