--- !Ups

ALTER TABLE dkp_accounts ADD COLUMN color CHAR(6);

--- !Downs

ALTER TABLE dkp_accounts DROP COLUMN color;
