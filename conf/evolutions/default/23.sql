-- !Ups

CREATE OR REPLACE VIEW trades_limits AS
WITH config AS
         (
             SELECT (SELECT value FROM trades_config WHERE key = 'individual_buy_limit')  AS ask_limit,
                    (SELECT value FROM trades_config WHERE key = 'individual_sell_limit') AS bid_limit
         ),
     orders AS
         (
             SELECT o.owner,
                    o.kind,
                    SUM(COALESCE(o.close_quantity, o.quantity) * o.decay_factor *
                        (CASE o.kind WHEN 'ask' THEN ts.buy_price WHEN 'bid' THEN ts.sell_price END))::NUMERIC
                        AS amount,
                    ts.buy_price,
                    ts.sell_price
             FROM trades_orders o
                      JOIN trades_sessions ts ON o.session = ts.id
             WHERE NOT o.archived
               AND (o.ack IS NULL OR o.ack = TRUE)
             GROUP BY (o.owner, o.kind, ts.id)
         )
SELECT a.user_id                                   AS user_id,
       a.ask_total                                 AS ask_total,
       GREATEST(0, config.ask_limit - a.ask_total) AS ask_remaining,
       a.bid_total                                 AS bid_total,
       GREATEST(0, config.bid_limit - a.bid_total) AS bid_remaining
FROM (SELECT u.id                                                                              AS user_id,
             (SELECT COALESCE(SUM(amount), 0) FROM orders WHERE owner = u.id AND kind = 'ask') as ask_total,
             (SELECT COALESCE(SUM(amount), 0) FROM orders WHERE owner = u.id AND kind = 'bid') as bid_total
      FROM users u) a,
     config;


-- !Downs
