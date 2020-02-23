package db.trades

import java.time.Instant

import db.api._
import model.Snowflake
import model.dkp.DkpAmount
import model.trades.Session

class Sessions(tag: Tag) extends Table[Session](tag, "trades_sessions") {
  def id           = column[Snowflake]("id", O.PrimaryKey)
  def sku          = column[Int]("sku")
  def openDate     = column[Instant]("open_date")
  def closeDate    = column[Option[Instant]]("close_date")
  def buyPrice     = column[DkpAmount]("buy_price")
  def buyQuantity  = column[Int]("buy_quantity")
  def buyOrders    = column[Int]("buy_orders")
  def sellPrice    = column[DkpAmount]("sell_price")
  def sellQuantity = column[Int]("sell_quantity")
  def sellOrders   = column[Int]("sell_orders")

  def * =
    (
      id,
      sku,
      openDate,
      closeDate,
      buyPrice,
      buyQuantity,
      buyOrders,
      sellPrice,
      sellQuantity,
      sellOrders
    ) <> (Session.tupled, Session.unapply)
}

object Sessions extends TableQuery(new Sessions(_))
