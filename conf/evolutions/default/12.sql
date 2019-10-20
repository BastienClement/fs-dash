-- !Ups

CREATE RULE auctions_orders_view_insert AS ON INSERT TO auctions_orders_view
    DO INSTEAD INSERT INTO auctions_orders (id, type, owner, account, item, quantity, price, hold, posted, validity, closed)
               VALUES (NEW.id, NEW.type, NEW.owner, NEW.account, NEW.item, NEW.quantity, NEW.price, NEW.hold, NEW.posted, NEW.validity, NEW.closed);

CREATE RULE auctions_orders_view_update AS ON UPDATE TO auctions_orders_view
    DO INSTEAD UPDATE auctions_orders SET closed = NEW.closed WHERE id = NEW.id;

CREATE PROCEDURE auctions_perform_matching() AS
$$
DECLARE
    matching RECORD;;
BEGIN
    LOCK auctions_orders, auctions_matches IN EXCLUSIVE MODE;;

    LOOP
        REFRESH MATERIALIZED VIEW auctions_orders_matches_mat;;

        SELECT * INTO matching FROM auctions_orders_matches_mat WHERE execution <= NOW() ORDER BY execution DESC LIMIT 1;;
        EXIT WHEN NOT FOUND;;

        INSERT INTO auctions_matches (bid, ask, quantity, price)
        VALUES (matching.bid, matching.ask, matching.quantity, matching.execution_price);;
    END LOOP;;
END;;
$$ LANGUAGE plpgsql;

-- !Downs

DROP PROCEDURE auctions_perform_matching();

DROP RULE auctions_orders_view_update ON auctions_orders_view;
DROP RULE auctions_orders_view_insert ON auctions_orders_view;
