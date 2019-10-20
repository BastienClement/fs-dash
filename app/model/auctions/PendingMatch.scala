package model.auctions

import java.time.Instant

import model.Snowflake
import model.dkp.DkpAmount

case class PendingMatch(
    ask: Snowflake,
    bid: Snowflake,
    item: Int,
    quantity: Int,
    execution: Instant,
    executionPrice: DkpAmount
)
