package model.auctions

import java.time.Instant

import model.Snowflake
import model.dkp.DkpAmount

case class Order(
    id: Snowflake,
    kind: String,
    owner: Option[Snowflake],
    account: Option[Snowflake],
    item: Int,
    quantity: Int,
    remaining: Int,
    price: DkpAmount,
    hold: Option[Snowflake],
    posted: Instant,
    validity: Instant,
    closed: Option[Instant]
)
