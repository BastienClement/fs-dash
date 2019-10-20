package db.bounties

import db.api._
import model.Snowflake
import model.bounties.Bounty

class Bounties(tag: Tag) extends Table[Bounty](tag, "bounties") {
  def id    = column[Snowflake]("id", O.PrimaryKey)
  def title = column[String]("title")
  def body  = column[String]("body")

  override def * =
    (id, title, body) <>
      ((Bounty.apply _).tupled, Bounty.unapply)
}

object Bounties extends TableQuery(new Bounties(_))
