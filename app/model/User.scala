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
  def isAdmin: Boolean       = roles.intersect(Set(User.RoleAdmin, User.RoleGuildMaster)).nonEmpty
  def isOfficer: Boolean     = isAdmin || roles.contains(User.RoleOfficer)
  def isMember: Boolean      = isOfficer || roles.intersect(Set(User.RoleClassLeader, User.RoleMember, User.RoleTrial)).nonEmpty
  def isTrial: Boolean       = roles.contains(User.RoleTrial)
  def isFromScratch: Boolean = isMember || roles.intersect(User.roles).nonEmpty

  def color: String =
    roles
      .collectFirst {
        case User.ClassDruid   => "FF7D0A"
        case User.ClassHunter  => "ABD473"
        case User.ClassMage    => "40C7EB"
        case User.ClassPriest  => "FFFFFF"
        case User.ClassRogue   => "FFF569"
        case User.ClassShaman  => "0070DE"
        case User.ClassWarlock => "8787ED"
        case User.ClassWarrior => "C79C6E"
      }
      .getOrElse("F5C635")
}

object User {
  implicit def Reads: Reads[User] =
    ((JsPath \ "id").read[Snowflake] and
      (JsPath \ "username").read[String] and
      (JsPath \ "discriminator").read[String] and
      (JsPath \ "avatar").readNullable[String])(User(_, _, _, _, Set.empty))

  val Anonymous = User(Snowflake.dummy, "Anon", "0000", None, Set.empty)

  val RoleAdmin       = Snowflake(608756668192260097L)
  val RoleGuildMaster = Snowflake(584304037365678091L)
  val RoleOfficer     = Snowflake(584304138855383041L)
  val RoleClassLeader = Snowflake(652626698709499934L)
  val RoleMember      = Snowflake(642351734840360990L)
  val RoleTrial       = Snowflake(607650376036122710L)
  val RoleCasual      = Snowflake(614613591672487946L)

  val roles: Set[Snowflake] =
    Set(RoleAdmin, RoleGuildMaster, RoleOfficer, RoleClassLeader, RoleMember, RoleTrial, RoleCasual)

  val ClassDruid   = Snowflake(584386195769917460L)
  val ClassHunter  = Snowflake(584386420341211146L)
  val ClassMage    = Snowflake(584386238048501771L)
  val ClassPriest  = Snowflake(584386344089026570L)
  val ClassRogue   = Snowflake(584386259795836937L)
  val ClassShaman  = Snowflake(584386318713225244L)
  val ClassWarlock = Snowflake(584386299742650388L)
  val ClassWarrior = Snowflake(584386276338171914L)

  val classes: Set[Snowflake] =
    Set(ClassDruid, ClassHunter, ClassMage, ClassPriest, ClassRogue, ClassShaman, ClassWarlock, ClassWarrior)
}
