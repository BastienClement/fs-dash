-- !Ups

ALTER TABLE dkp_accounts
    ADD COLUMN roster BOOLEAN DEFAULT TRUE;

DROP VIEW dkp_accounts_view;

CREATE VIEW dkp_accounts_view AS
SELECT *, (SELECT COALESCE(SUM(amount), 0) FROM dkp_holds h WHERE h.account = a.id) AS holds
FROM dkp_accounts a;

-- !Downs

ALTER TABLE dkp_accounts
    DROP COLUMN roster;

DROP VIEW dkp_accounts_view;
CREATE VIEW dkp_accounts_view AS
SELECT *, (SELECT COALESCE(SUM(amount), 0) FROM dkp_holds h WHERE h.account = a.id) AS holds
FROM dkp_accounts a;
