package db.trades

import db.api._
import model.Snowflake
import model.dkp.DkpAmount
import model.trades.Limit

class Limits(tag: Tag) extends Table[Limit](tag, "trades_limits") {
  def user         = column[Snowflake]("user_id", O.PrimaryKey)
  def askTotal     = column[DkpAmount]("ask_total")
  def askAvailable = column[DkpAmount]("ask_remaining")
  def bidTotal     = column[DkpAmount]("bid_total")
  def bidAvailable = column[DkpAmount]("bid_remaining")

  def * = (user, askTotal, askAvailable, bidTotal, bidAvailable) <> (Limit.tupled, Limit.unapply)
}

object Limits extends TableQuery(new Limits(_)) {
  val forUser = this.findBy(l => l.user)
}
