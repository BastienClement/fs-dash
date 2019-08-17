package model

import play.api.libs.functional.syntax._
import play.api.libs.json.{JsPath, Reads}

case class User(
    id: Snowflake,
    username: String,
    discriminator: String,
    avatar: Option[String],
    roles: Set[Snowflake]
) {
  def isAdmin: Boolean   = roles.intersect(Set(User.RoleAdmin, User.RoleGuildMaster)).nonEmpty
  def isOfficer: Boolean = isAdmin || roles.contains(User.RoleOfficer)
}

object User {
  implicit def Reads: Reads[User] =
    ((JsPath \ "id").read[Snowflake] and
      (JsPath \ "username").read[String] and
      (JsPath \ "discriminator").read[String] and
      (JsPath \ "avatar").readNullable[String])(User(_, _, _, _, Set.empty))

  val RoleAdmin       = Snowflake(608756668192260097L)
  val RoleGuildMaster = Snowflake(584304037365678091L)
  val RoleOfficer     = Snowflake(584304138855383041L)
  val RoleRaider      = Snowflake(584304255490457600L)
  val RoleTrial       = Snowflake(607650376036122710L)
}
