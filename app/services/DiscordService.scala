package services

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

import controllers.routes
import db.Users
import db.api._
import javax.inject.{Inject, Singleton}
import model.{Session, Snowflake, User}
import play.api.Configuration
import play.api.libs.json.{JsArray, JsValue, Reads}
import play.api.libs.ws.{WSClient, WSRequest, WSResponse}
import play.api.mvc.Request

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DiscordService @Inject()(conf: Configuration, ws: WSClient)(implicit ec: ExecutionContext) {

  private val discordApi   = "https://discord.com/api"
  private val clientId     = conf.get[String]("dash.discord.oauth.id")
  private val clientSecret = conf.get[String]("dash.discord.oauth.secret")
  private val botToken     = conf.get[String]("dash.discord.bot.token")
  private val tokenScope   = "identify"

  private def redirectUrl(implicit req: Request[_]): String =
    s"${if (req.secure) "https" else "http"}://${req.host}" + routes.LoginController.authorize()

  def loginLink(implicit request: Request[_]): String = {
    val endpoint = s"${discordApi}/oauth2/authorize"
    val redirect = URLEncoder.encode(redirectUrl, StandardCharsets.UTF_8)
    val scope    = URLEncoder.encode(tokenScope, StandardCharsets.UTF_8)
    s"${endpoint}?client_id=${clientId}&redirect_uri=${redirect}&response_type=code&scope=${scope}"
  }

  private def mapResult[A: Reads]: WSResponse => Either[JsValue, A] = { res =>
    res.status match {
      case 200 => Right(res.json.as[A])
      case _   => Left(res.json)
    }
  }

  private def api(path: String)(implicit session: Session): WSRequest =
    ws.url(s"${discordApi}${path}")
      .withHttpHeaders("Authorization" -> s"${session.tokenType} ${session.accessToken}")

  private def botApi(path: String): WSRequest =
    ws.url(s"${discordApi}${path}")
      .withHttpHeaders("Authorization" -> s"Bot ${botToken}")

  def retrieveToken(code: String)(implicit request: Request[_]): Future[Either[JsValue, Session]] = {
    val data = Map(
      "client_id"     -> clientId,
      "client_secret" -> clientSecret,
      "grant_type"    -> "authorization_code",
      "code"          -> code,
      "redirect_uri"  -> redirectUrl,
      "scope"         -> tokenScope
    )

    ws.url(s"${discordApi}/oauth2/token").post(data).map(mapResult[Session])
  }

  def refreshToken()(implicit session: Session, request: Request[_]): Future[Either[JsValue, Session]] = {
    val data = Map(
      "client_id"     -> clientId,
      "client_secret" -> clientSecret,
      "grant_type"    -> "refresh_token",
      "refresh_token" -> session.refreshToken,
      "redirect_uri"  -> redirectUrl,
      "scope"         -> tokenScope
    )

    ws.url(s"${discordApi}/oauth2/token").post(data).map(mapResult[Session]).map {
      case Left(res)         => Left(res)
      case Right(newSession) => Right(newSession.copy(id = session.id, owner = session.owner))
    }
  }

  def fetchUsers(): DBIOAction[Either[JsValue, Unit], NoStream, Effect with W with T] =
    DBIO
      .from(
        botApi(s"/guilds/${DiscordService.FsClassicGuild}/members")
          .withQueryStringParameters("limit" -> "1000")
          .get()
          .map(mapResult[JsArray])
      )
      .flatMap {
        case Left(err) =>
          DBIO.successful(Left(err))
        case Right(members) =>
          val users = members.value.map { member =>
            User(
              id = (member \ "user" \ "id").as[Snowflake],
              username = (member \ "nick").asOpt[String].getOrElse((member \ "user" \ "username").as[String]),
              discriminator = (member \ "user" \ "discriminator").as[String],
              avatar = (member \ "user" \ "avatar").asOpt[String],
              roles = (member \ "roles").as[JsArray].value.map(role => Snowflake.fromString(role.as[String])).toSet
            )
          }

          Users
            .insertOrUpdateAll(users)
            .andThen(Users.filter(u => !(u.id inSet users.map(_.id))).map(_.roles).update(Set.empty))
            .map(_ => Right(()))
            .transactionally
      }

  def fetchUser()(implicit session: Session, database: Database): Future[Either[JsValue, User]] =
    DBIO
      .from(api("/users/@me").get().map(mapResult[User]))
      .flatMap {
        case Left(res) =>
          DBIO.successful(Left(res))
        case Right(user) =>
          fetchUsers()
            .flatMap {
              case Left(res) => DBIO.successful(Left(res))
              case Right(_)  => Users.findById(user.id).result.headOption.map(Right.apply)
            }
            .map {
              case Left(res)           => Left(res)
              case Right(Some(member)) => Right(member)
              case Right(None)         => Right(user)
            }
      }
      .run
}

object DiscordService {
  val FsClassicGuild = Snowflake(584302816173096960L)
}
