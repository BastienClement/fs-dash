package db.trades

import db.api._
import model.Snowflake

class History(tag: Tag) extends Table[(Int, Snowflake, Int, String)](tag, "trades_history") {
  def sku      = column[Int]("sku")
  def user     = column[Snowflake]("user")
  def quantity = column[Int]("quantity")
  def label    = column[String]("label")

  def * = (sku, user, quantity, label)
}

object History extends TableQuery(new History(_))
