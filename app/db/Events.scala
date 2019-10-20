package db

import java.time.LocalDateTime

import db.api._
import model.{Event, Snowflake}

class Events(tag: Tag) extends Table[Event](tag, "calendar_events") {
  def id     = column[Snowflake]("id", O.PrimaryKey)
  def date   = column[LocalDateTime]("date")
  def label  = column[String]("label")
  def isNote = column[Boolean]("is_note")

  override def * =
    (id, date, label, isNote) <>
      ((Event.apply _).tupled, Event.unapply)
}

object Events extends TableQuery(new Events(_))
