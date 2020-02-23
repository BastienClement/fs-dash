-- !Ups

DROP MATERIALIZED VIEW IF EXISTS auctions_orders_matches_mat CASCADE;
DROP VIEW IF EXISTS auctions_orders_matches CASCADE;
DROP VIEW IF EXISTS auctions_orders_overview CASCADE;
DROP VIEW IF EXISTS auctions_orders_view CASCADE;
DROP VIEW IF EXISTS dkp_rolling_limits CASCADE;

DROP TABLE IF EXISTS auctions_matches CASCADE;
DROP TABLE IF EXISTS auctions_orders CASCADE;

DROP TABLE IF EXISTS bounties CASCADE;
DROP TABLE IF EXISTS bounties_objectives CASCADE;

DROP TABLE IF EXISTS calendar_events CASCADE;
DROP TABLE IF EXISTS calendar_answers CASCADE;

DELETE FROM dkp_decay_config WHERE key IN ('rolling_limit_days', 'trade_tax', 'rolling_ask_limit', 'rolling_bid_limit');

DROP ROUTINE IF EXISTS auctions_matches_on_after_insert();
DROP ROUTINE IF EXISTS auctions_matches_on_before_insert();
DROP ROUTINE IF EXISTS auctions_matches_on_update();
DROP ROUTINE IF EXISTS auctions_orders_matches_mat_refresh();
DROP ROUTINE IF EXISTS auctions_orders_on_insert_create_hold();
DROP ROUTINE IF EXISTS auctions_perform_matching();

-- !Downs
