-- !Ups

INSERT INTO dkp_decay_config (key, value)
VALUES ('rolling_limit_days', 7),
       ('rolling_bid_limit', 10000),
       ('rolling_ask_limit', 150000);

CREATE OR REPLACE VIEW dkp_rolling_limits AS
WITH config AS (
    SELECT (SELECT value FROM dkp_decay_config WHERE key = 'rolling_limit_days') AS rolling_limit_days,
           (SELECT value FROM dkp_decay_config WHERE key = 'rolling_bid_limit')  AS rolling_bid_limit,
           (SELECT value FROM dkp_decay_config WHERE key = 'rolling_ask_limit')  AS rolling_ask_limit
)
SELECT user_id,
       ask_open_total + ask_closed_total                                           AS ask_total,
       GREATEST(0, config.rolling_ask_limit - (ask_open_total + ask_closed_total)) AS ask_available,
       (ask_open_total + ask_closed_total) = 0                                     AS ask_burstable,
       bid_open_total + bid_closed_total                                           AS bid_total,
       GREATEST(0, config.rolling_bid_limit - (bid_open_total + bid_closed_total)) AS bid_available,
       (bid_open_total + bid_closed_total) = 0                                     AS bid_burstable
FROM (SELECT u.id AS user_id,
             (SELECT COALESCE(SUM(remaining * price), 0)
              FROM auctions_orders_view
              WHERE owner = u.id
                AND type = 'ask'
                AND closed IS NULL
             )    AS ask_open_total,
             (SELECT COALESCE(SUM(quantity * price), 0)
              FROM auctions_matches am
              WHERE (SELECT owner FROM auctions_orders ao WHERE ao.id = am.ask) = u.id
                AND matched >= (NOW() - INTERVAL '1 DAY' * config.rolling_limit_days)
                AND (SELECT owner FROM auctions_orders ao WHERE ao.id = am.bid) IS NOT NULL
             )    AS ask_closed_total,
             (SELECT COALESCE(SUM(remaining * price), 0)
              FROM auctions_orders_view
              WHERE owner = u.id
                AND type = 'bid'
                AND closed IS NULL
             )    AS bid_open_total,
             (SELECT COALESCE(SUM(quantity * price), 0)
              FROM auctions_matches am
              WHERE (SELECT owner FROM auctions_orders ao WHERE ao.id = am.bid) = u.id
                AND matched >= (NOW() - INTERVAL '1 DAY' * config.rolling_limit_days)
                AND (SELECT owner FROM auctions_orders ao WHERE ao.id = am.ask) IS NOT NULL
             )    AS bid_closed_total
      FROM users AS u,
           config) a,
     config;

-- !Downs

DROP VIEW dkp_rolling_limits;

DELETE
FROM dkp_decay_config
WHERE key IN ('rolling_limit_days', 'rolling_bid_limit', 'rolling_ask_limit');
