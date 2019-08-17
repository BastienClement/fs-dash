package db

import db.api._
import model.{Account, DkpAmount, Snowflake}

class Accounts(tag: Tag) extends Table[Account](tag, "dkp_accounts") {
  def id       = column[Snowflake]("id", O.PrimaryKey)
  def label    = column[String]("label")
  def balance  = column[DkpAmount]("balance")
  def useDecay = column[Boolean]("use_decay")

  override def * =
    (id, label, balance, useDecay) <>
      ((Account.apply _).tupled, Account.unapply)
}

object Accounts extends TableQuery(new Accounts(_))
