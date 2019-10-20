package db.auctions

import java.time.Instant

import db.api._
import model.Snowflake
import model.auctions.PendingMatch
import model.dkp.DkpAmount

class PendingMatches(tag: Tag) extends Table[PendingMatch](tag, "auctions_orders_matches_mat") {
  def bid            = column[Snowflake]("bid")
  def ask            = column[Snowflake]("ask")
  def item           = column[Int]("item")
  def quantity       = column[Int]("quantity")
  def execution      = column[Instant]("execution")
  def executionPrice = column[DkpAmount]("execution_price")

  override def * =
    (bid, ask, item, quantity, execution, executionPrice) <>
      ((PendingMatch.apply _).tupled, PendingMatch.unapply)
}

object PendingMatches extends TableQuery(new PendingMatches(_))
