package db.auctions

import db.api._
import model.Snowflake
import model.auctions.RollingLimit
import model.dkp.DkpAmount

class RollingLimits(tag: Tag) extends Table[RollingLimit](tag, "dkp_rolling_limits") {
  def user         = column[Snowflake]("user_id", O.PrimaryKey)
  def askTotal     = column[DkpAmount]("ask_total")
  def askAvailable = column[DkpAmount]("ask_available")
  def askBurstable = column[Boolean]("ask_burstable")
  def bidTotal     = column[DkpAmount]("bid_total")
  def bidAvailable = column[DkpAmount]("bid_available")
  def bidBurstable = column[Boolean]("bid_burstable")

  override def * =
    (user, askTotal, askAvailable, askBurstable, bidTotal, bidAvailable, bidBurstable) <> (RollingLimit.tupled, RollingLimit.unapply)
}

object RollingLimits extends TableQuery(new RollingLimits(_))
