package db.auctions

import java.time.Instant

import db.api._
import model.Snowflake
import model.auctions.Match
import model.dkp.DkpAmount

class Matches(tag: Tag) extends Table[Match](tag, "auctions_matches") {
  def id = column[Snowflake]("id", O.PrimaryKey)
  def bid = column[Snowflake]("bid")
  def ask = column[Snowflake]("ask")
  def quantity = column[Int]("quantity")
  def price = column[DkpAmount]("price")
  def matched = column[Instant]("matched")
  def ackStatus = column[Option[Boolean]]("ack_status")
  def ackDate = column[Option[Instant]]("ack_date")

  override def * =
    (id, bid, ask, quantity, price, matched, ackStatus, ackDate) <>
      ((Match.apply _).tupled, Match.unapply)
}

object Matches extends TableQuery(new Matches(_))

