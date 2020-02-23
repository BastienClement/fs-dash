package db.trades

import db.api._
import model.Snowflake
import model.trades.Order
import slick.ast.BaseTypedType

class Orders(tag: Tag) extends Table[Order](tag, "trades_orders") {
  def id            = column[Snowflake]("id", O.PrimaryKey)
  def session       = column[Snowflake]("session")
  def owner         = column[Snowflake]("owner")
  def account       = column[Snowflake]("account")
  def kind          = column[String]("kind")(implicitly[BaseTypedType[String]] @:: "order_type")
  def quantity      = column[Int]("quantity")
  def closed        = column[Boolean]("closed")
  def closeQuantity = column[Option[Int]]("close_quantity")
  def ack           = column[Option[Boolean]]("ack")
  def ackBy         = column[Option[Snowflake]]("ack_by")
  def archived      = column[Boolean]("archived")

  def * =
    (
      id,
      session,
      owner,
      account,
      kind,
      quantity,
      closed,
      closeQuantity,
      ack,
      ackBy,
      archived
    ) <> (Order.tupled, Order.unapply)
}

object Orders extends TableQuery(new Orders(_))
