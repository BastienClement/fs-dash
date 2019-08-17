package db

import java.time.Instant

import db.api._
import model.{DkpAmount, Movement, Snowflake}

class Movements(tag: Tag) extends Table[Movement](tag, "dkp_movements") {
  def id          = column[Snowflake]("id", O.PrimaryKey)
  def date        = column[Instant]("date")
  def account     = column[Snowflake]("account")
  def transaction = column[Option[Snowflake]]("transaction")
  def label       = column[String]("label")
  def amount      = column[DkpAmount]("amount")
  def balance     = column[DkpAmount]("balance")
  def details     = column[String]("details")
  def author      = column[Snowflake]("author")
  def item        = column[Option[Int]]("item")

  override def * =
    (id, date, account, transaction, label, amount, balance, details, author, item) <>
      ((Movement.apply _).tupled, Movement.unapply)
}

object Movements extends TableQuery(new Movements(_))
