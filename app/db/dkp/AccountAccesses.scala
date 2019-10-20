package db.dkp

import db.api._
import model.Snowflake
import model.dkp.AccountAccess

class AccountAccesses(tag: Tag) extends Table[AccountAccess](tag, "dkp_accounts_accesses") {
  def owner   = column[Snowflake]("owner", O.PrimaryKey)
  def account = column[Snowflake]("account", O.PrimaryKey)
  def main    = column[Boolean]("main")

  override def * =
    (owner, account, main) <>
      ((AccountAccess.apply _).tupled, AccountAccess.unapply)
}

object AccountAccesses extends TableQuery(new AccountAccesses(_))
