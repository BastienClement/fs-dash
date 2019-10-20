package db

import db.api._
import model.{Snowflake, User}

class Users(tag: Tag) extends Table[User](tag, "users") {
  def id            = column[Snowflake]("id", O.PrimaryKey)
  def username      = column[String]("username")
  def discriminator = column[String]("discriminator")
  def avatar        = column[Option[String]]("avatar")

  def roles =
    column[String]("roles") <> (
      (t: String) => t.split(",").filter(_.nonEmpty).map(Snowflake.fromString).toSet,
      (s: Set[Snowflake]) => Some(s.map(_.toString).mkString(","))
    )

  override def * =
    (id, username, discriminator, avatar, roles) <>
      ((User.apply _).tupled, User.unapply)
}

object Users extends TableQuery(new Users(_)) {
  val findById = this.findBy(u => u.id)
}
