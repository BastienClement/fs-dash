package db.dkp

import db.api._
import model.Snowflake
import model.dkp.{Account, DkpAmount}

class Accounts(tag: Tag) extends Table[Account](tag, "dkp_accounts_view") {
  def id        = column[Snowflake]("id", O.PrimaryKey)
  def label     = column[String]("label")
  def color     = column[Option[String]]("color")
  def balance   = column[DkpAmount]("balance")
  def archived  = column[Boolean]("archived")
  def useDecay  = column[Boolean]("use_decay")
  def roster    = column[Boolean]("roster")
  def overdraft = column[DkpAmount]("overdraft")
  def holds     = column[DkpAmount]("holds", O.AutoInc)

  override def * =
    (id, label, color, balance, archived, useDecay, roster, overdraft, holds) <>
      ((Account.apply _).tupled, Account.unapply)
}

object Accounts extends TableQuery(new Accounts(_))
