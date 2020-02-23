package db.trades

import java.time.Instant

import db.api._
import model.Snowflake
import model.dkp.Movement

class HistoryView(tag: Tag) extends Table[HistoryLine](tag, "trades_history_view") {
  def sku      = column[Int]("sku")
  def user     = column[Option[Snowflake]]("user")
  def date     = column[Instant]("date")
  def quantity = column[Int]("quantity")
  def label    = column[String]("label")

  def * = (sku, user, date, quantity, label) <> (HistoryLine.tupled, HistoryLine.unapply)
}

object HistoryView extends TableQuery(new HistoryView(_))

case class HistoryLine(sku: Int, user: Option[Snowflake], date: Instant, quantity: Int, label: String) {
  def humanDate: String = Movement.formatter.format(date)
  def signedQuantity: String = if (quantity > 0) s"+$quantity" else s"$quantity"
}
