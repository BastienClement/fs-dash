-- !Ups

ALTER TABLE dkp_accounts
    DROP COLUMN use_decay,
    ADD COLUMN archived BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE dkp_decay_config
(
    key   TEXT PRIMARY KEY,
    value DOUBLE PRECISION
);

INSERT INTO dkp_decay_config (key, value)
VALUES ('minimum_decay', 0.10),
       ('maximum_decay', 0.35),
       ('per_account_target', 225.0);

CREATE OR REPLACE PROCEDURE apply_decay()
    LANGUAGE plpgsql
AS
$$
DECLARE
    account_cursor     REFCURSOR;;
    account            RECORD;;
    transaction_id     SNOWFLAKE := 0;;
    active_threshold   DOUBLE PRECISION;;
    per_account_target DOUBLE PRECISION;;
    minimum_decay      DOUBLE PRECISION;;
    maximum_decay      DOUBLE PRECISION;;
    total_supply       INTEGER;;
    active_accounts    INTEGER;;
    decayable_supply   INTEGER;;
    decayable_accounts INTEGER;;
    target_supply      INTEGER;;
    decay_factor       DOUBLE PRECISION;;
BEGIN
    SELECT value FROM dkp_decay_config WHERE key = 'per_account_target' INTO per_account_target;;
    SELECT value FROM dkp_decay_config WHERE key = 'minimum_decay' INTO minimum_decay;;
    SELECT value FROM dkp_decay_config WHERE key = 'maximum_decay' INTO maximum_decay;;

    SELECT SUM(balance) FROM dkp_accounts WHERE archived = FALSE INTO total_supply;;
    SELECT COUNT(*) FROM dkp_accounts WHERE archived = FALSE INTO active_accounts;;

    SELECT SUM(balance) FROM dkp_accounts WHERE archived = FALSE AND balance > 0 INTO decayable_supply;;
    SELECT COUNT(*) FROM dkp_accounts WHERE archived = FALSE AND balance > 0 INTO decayable_accounts;;

    SELECT active_accounts * per_account_target * 100 INTO target_supply;;

    SELECT LEAST(maximum_decay,
                 GREATEST(minimum_decay,
                          (total_supply - target_supply)::double precision / (decayable_supply + 1)))
    INTO decay_factor;;

    OPEN account_cursor NO SCROLL FOR SELECT * FROM dkp_accounts WHERE archived = FALSE AND balance > 0 ORDER BY label;;
    LOOP
        FETCH account_cursor INTO account;;
        EXIT WHEN NOT FOUND;;

        IF transaction_id = 0 THEN
            SELECT SNOWFLAKE() INTO transaction_id;;

            INSERT INTO dkp_transactions (id, label, details)
            VALUES (transaction_id, 'Decay: ' || to_char(current_date, 'IYYY-IW'),
                    'Active accounts: ' || active_accounts || E'\n' ||
                    'Decayable accounts: ' || decayable_accounts || E'\n' ||
                    'Total supply: ' || trim(to_char(total_supply / 100.0, '9999999D99')) || E'\n' ||
                    'Decayable supply: ' || trim(to_char(decayable_supply / 100.0, '9999999D99')) || E'\n' ||
                    'Target supply: ' || trim(to_char(target_supply / 100.0, '9999999D99')) || E'\n' ||
                    'Decay factor: ' || decay_factor);;
        END IF;;

        INSERT INTO dkp_movements (id, date, account, transaction, label, amount, balance, details, author, item)
        VALUES (SNOWFLAKE(), NOW(), account.id, transaction_id, 'Decay',
                -CEIL(account.balance::DOUBLE PRECISION * decay_factor)::BIGINT, NULL, '', NULL, NULL);;
    END LOOP;;

    CLOSE account_cursor;;
END;;
$$;

-- !Downs

DROP TABLE dkp_decay_config;

ALTER TABLE dkp_accounts
    ADD COLUMN use_decay BOOLEAN NOT NULL DEFAULT TRUE,
    DROP COLUMN archived;
