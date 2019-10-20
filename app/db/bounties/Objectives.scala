package db.bounties

import db.api._
import model.Snowflake
import model.bounties.Objective
import model.dkp.DkpAmount

class Objectives(tag: Tag) extends Table[Objective](tag, "bounties_objectives") {
  def id             = column[Snowflake]("id", O.PrimaryKey)
  def bounty         = column[Snowflake]("bounty")
  def label          = column[String]("label")
  def item           = column[Option[Int]]("item")
  def reward         = column[Option[DkpAmount]]("reward")
  def count          = column[Option[Int]]("count")
  def progress       = column[Int]("progress")
  def showPercentage = column[Boolean]("show_percentage")

  override def * =
    (id, bounty, label, item, reward, count, progress, showPercentage) <>
      ((Objective.apply _).tupled, Objective.unapply)
}

object Objectives extends TableQuery(new Objectives(_))
