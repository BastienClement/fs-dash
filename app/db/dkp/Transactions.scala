package db.dkp

import db.api._
import model.Snowflake
import model.dkp.Transaction

class Transactions(tag: Tag) extends Table[Transaction](tag, "dkp_transactions") {
  def id      = column[Snowflake]("id", O.PrimaryKey)
  def label   = column[String]("label")
  def details = column[String]("details")

  override def * = (id, label, details) <> ((Transaction.apply _).tupled, Transaction.unapply)
}

object Transactions extends TableQuery(new Transactions(_))
