-- !Ups

CREATE SEQUENCE snowflake_counter MINVALUE 0 MAXVALUE 1048575 CYCLE;

CREATE FUNCTION SNOWFLAKE() RETURNS SNOWFLAKE
AS
$$
SELECT (((EXTRACT(EPOCH FROM now()) * 1000 - 1514764800000)::BIGINT::BIT(64) & X'000001FFFFFFFFFF') << 21 |
        X'0000000000200000' |
        (nextval('snowflake_counter')::BIT(64) & X'00000000000FFFFF'))::SNOWFLAKE
$$
    LANGUAGE SQL
    SECURITY INVOKER;

CREATE PROCEDURE apply_decay()
    LANGUAGE plpgsql
AS
$$
declare
    account_cursor refcursor;;
    account        record;;
    transaction_id SNOWFLAKE := 0;;
begin
    open account_cursor no scroll for SELECT * FROM dkp_accounts WHERE use_decay = true AND balance > 0 ORDER BY label;;
    loop
        fetch account_cursor into account;;
        exit when not found;;

        if transaction_id = 0 then
            SELECT SNOWFLAKE() INTO transaction_id;;

            INSERT INTO dkp_transactions (id, label, details)
            VALUES (transaction_id, 'Decay: ' || to_char(current_date, 'IYYY-IW'), '');;
        end if;;

        INSERT INTO dkp_movements (id, date, account, transaction, label, amount, balance, details, author, item)
        VALUES (SNOWFLAKE(), NOW(), account.id, transaction_id, 'Decay', -CEIL(account.balance::DOUBLE PRECISION * 0.2)::BIGINT, NULL, '', NULL, NULL);;
    end loop;;

    close account_cursor;;
end;;
$$;

-- !Downs

DROP PROCEDURE apply_decay();
DROP FUNCTION SNOWFLAKE();
DROP SEQUENCE snowflake_counter;
