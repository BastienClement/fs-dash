package db.charter

import db.api._
import model.Snowflake
import model.charter.Section

class Sections(tag: Tag) extends Table[Section](tag, "charter_sections") {
  def id     = column[Snowflake]("id", O.PrimaryKey)
  def number = column[Int]("number", O.Unique)
  def title  = column[String]("title")
  def body   = column[String]("body")

  override def * =
    (id, number, title, body) <>
      ((Section.apply _).tupled, Section.unapply)
}

object Sections extends TableQuery(new Sections(_))
