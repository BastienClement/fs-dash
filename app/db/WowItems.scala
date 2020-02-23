package db

import java.time.Instant

import db.api._
import model.WowItem
import play.api.libs.json.JsValue

class WowItems(tag: Tag) extends Table[WowItem](tag, "wow_items") {
  def id      = column[Int]("id", O.PrimaryKey)
  def name    = column[String]("name")
  def quality = column[Int]("quality")
  def icon    = column[String]("icon")
  def fetched = column[Instant]("fetched")
  def stale   = column[Boolean]("stale")
  def data    = column[JsValue]("data")

  override def * =
    (id, name, quality, icon, fetched, stale, data) <>
      ((WowItem.apply _).tupled, WowItem.unapply)
}

object WowItems extends TableQuery(new WowItems(_)) {
  val findById = this.findBy(_.id)
}
