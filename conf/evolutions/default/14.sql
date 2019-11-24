-- !Ups

CREATE OR REPLACE VIEW auctions_orders_matches AS
SELECT *,
       CASE WHEN ask_posted >= bid_posted THEN ask_price ELSE bid_price END
           AS execution_price
FROM (SELECT ask.id                                 AS ask,
             bid.id                                 AS bid,
             ask.item                               AS item,
             LEAST(ask.remaining, bid.remaining)    AS quantity,
             ask.price                              AS ask_price,
             ask.posted                             AS ask_posted,
             0::BIGINT                              AS ask_count,
             bid.price                              AS bid_price,
             bid.posted                             AS bid_posted,
             0::BIGINT                              as bid_count,
             GREATEST(ask.execution, bid.execution) AS execution
      FROM auctions_orders_view ask,
           auctions_orders_view bid
      WHERE ask.remaining > 0
        AND bid.remaining > 0
        AND ask.id = bid.execution_order
        AND bid.id = ask.execution_order
        AND ask.type = 'ask'
        AND ask.closed IS NULL
        and bid.closed IS NULL) m;

-- !Downs
