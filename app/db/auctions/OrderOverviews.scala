package db.auctions

import db.api._
import model.auctions.OrderOverview
import model.dkp.DkpAmount

class OrderOverviews(tag: Tag) extends Table[OrderOverview](tag, "auctions_orders_overview") {
  def item     = column[Int]("item")
  def bidUnits = column[Int]("bid_unit")
  def bidPrice = column[Option[DkpAmount]]("bid_price")
  def askUnits = column[Int]("ask_unit")
  def askPrice = column[Option[DkpAmount]]("ask_price")

  override def * =
    (item, bidUnits, bidPrice, askUnits, askPrice) <>
      ((OrderOverview.apply _).tupled, OrderOverview.unapply)
}

object OrderOverviews extends TableQuery(new OrderOverviews(_))
