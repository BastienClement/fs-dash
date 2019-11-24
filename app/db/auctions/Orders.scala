package db.auctions

import java.time.Instant

import db.api._
import model.Snowflake
import model.auctions.Order
import model.dkp.DkpAmount
import slick.ast.BaseTypedType

class Orders(tag: Tag) extends Table[Order](tag, "auctions_orders_view") {
  def id        = column[Snowflake]("id", O.PrimaryKey)
  def kind      = column[String]("type")(implicitly[BaseTypedType[String]] @:: "order_type")
  def owner     = column[Option[Snowflake]]("owner")
  def account   = column[Option[Snowflake]]("account")
  def item      = column[Int]("item")
  def quantity  = column[Int]("quantity")
  def remaining = column[Int]("remaining", O.AutoInc)
  def price     = column[DkpAmount]("price")
  def hold      = column[Option[Snowflake]]("hold")
  def posted    = column[Instant]("posted")
  def validity  = column[Instant]("validity")
  def closed    = column[Option[Instant]]("closed")

  def priceInt = column[Int]("price")

  override def * =
    (id, kind, owner, account, item, quantity, remaining, price, hold, posted, validity, closed) <>
      ((Order.apply _).tupled, Order.unapply)
}

object Orders extends TableQuery(new Orders(_))
