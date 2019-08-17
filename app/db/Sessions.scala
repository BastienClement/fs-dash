package db

import java.time.Instant

import db.api._
import model.{Session, Snowflake}

class Sessions(tag: Tag) extends Table[Session](tag, "sessions") {
  def id           = column[Snowflake]("id", O.PrimaryKey)
  def owner        = column[Snowflake]("owner")
  def accessToken  = column[String]("access_token")
  def scope        = column[String]("scope")
  def tokenType    = column[String]("token_type")
  def expiresAt    = column[Instant]("expires_at")
  def lastChecked  = column[Instant]("last_checked")
  def refreshToken = column[String]("refresh_token")

  override def * =
    (id, owner, accessToken, scope, tokenType, expiresAt, lastChecked, refreshToken) <>
      ((Session.apply _).tupled, Session.unapply)
}

object Sessions extends TableQuery(new Sessions(_))
