-- !Ups

CREATE OR REPLACE PROCEDURE apply_decay()
    LANGUAGE plpgsql
AS
$$
DECLARE
    account_cursor     REFCURSOR;;
    account            RECORD;;
    transaction_id     SNOWFLAKE := 0;;
    per_account_target DOUBLE PRECISION;;
    minimum_decay      DOUBLE PRECISION;;
    maximum_decay      DOUBLE PRECISION;;
    total_supply       INTEGER;;
    active_accounts    INTEGER;;
    decayable_supply   INTEGER;;
    target_supply      INTEGER;;
    decay_factor       DOUBLE PRECISION;;
BEGIN
    SELECT value FROM dkp_decay_config WHERE key = 'per_account_target' INTO per_account_target;;
    SELECT value FROM dkp_decay_config WHERE key = 'minimum_decay' INTO minimum_decay;;
    SELECT value FROM dkp_decay_config WHERE key = 'maximum_decay' INTO maximum_decay;;

    SELECT COUNT(*) FROM dkp_accounts WHERE archived = FALSE AND use_decay = TRUE INTO active_accounts;;
    SELECT SUM(balance) FROM dkp_accounts WHERE archived = FALSE AND use_decay = TRUE INTO total_supply;;
    SELECT SUM(balance) FROM dkp_accounts WHERE archived = FALSE AND use_decay = TRUE AND balance > 0 INTO decayable_supply;;

    SELECT active_accounts * per_account_target * 100 INTO target_supply;;

    SELECT LEAST(maximum_decay,
                 GREATEST(minimum_decay,
                          (total_supply - target_supply)::double precision / (decayable_supply + 1)))
    INTO decay_factor;;

    OPEN account_cursor NO SCROLL FOR SELECT * FROM dkp_accounts WHERE balance > 0 AND use_decay = TRUE ORDER BY label;;
    LOOP
        FETCH account_cursor INTO account;;
        EXIT WHEN NOT FOUND;;

        IF transaction_id = 0 THEN
            SELECT SNOWFLAKE() INTO transaction_id;;

            INSERT INTO dkp_transactions (id, label, details)
            VALUES (transaction_id, 'Decay: ' || to_char(current_date, 'IYYY-IW'),
                    'Active accounts: ' || active_accounts || E'\n' ||
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
