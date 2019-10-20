package db.dkp

import db.api._
import model.Snowflake
import model.dkp.{DkpAmount, Hold}

class Holds(tag: Tag) extends Table[Hold](tag, "dkp_holds") {
  def id = column[Snowflake]("id", O.PrimaryKey)
  def account = column[Snowflake]("account")
  def amount = column[DkpAmount]("amount")
  def label = column[String]("label")

  override def * =
    (id, account, amount, label) <>
      ((Hold.apply _).tupled, Hold.unapply)
}

object Holds extends TableQuery(new Holds(_))
