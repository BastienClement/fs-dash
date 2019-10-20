package model.auctions

import java.time.Instant

import model.Snowflake
import model.dkp.{DkpAmount, Movement}

case class Match(
    id: Snowflake,
    bid: Snowflake,
    ask: Snowflake,
    quantity: Int,
    price: DkpAmount,
    matched: Instant,
    ackStatus: Option[Boolean],
    ackDate: Option[Instant]
) {
  def humanMatched: String = Movement.formatter.format(matched)
}
