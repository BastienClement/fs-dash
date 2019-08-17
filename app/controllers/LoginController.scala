package controllers

import db.api._
import db.{Sessions, Users}
import javax.inject.Inject
import model.{Session, Snowflake}
import play.api.mvc.{Action, AnyContent}
import services.DiscordService

import scala.concurrent.Future

class LoginController @Inject()(discord: DiscordService) extends DashController {

  def logout: Action[AnyContent] = DashAction.authenticated.async { implicit req =>
    Sessions
      .filter(s => s.id === Snowflake.fromString(req.session.get("token").get))
      .delete
      .andThen(DBIO.successful(Redirect(routes.HomeController.index()).withNewSession))
      .run
  }

  def authorize: Action[AnyContent] = DashAction.async { implicit req =>
    req.getQueryString("code") match {
      case Some(code) =>
        discord
          .retrieveToken(code)
          .flatMap {
            case Left(res) =>
              Future.successful(Left(res))
            case Right(session) =>
              implicit val s: Session = session
              discord.fetchUser().map {
                case Left(res)   => Left(res)
                case Right(user) => Right((user, s.copy(owner = user.id)))
              }
          }
          .flatMap {
            case Left(res) =>
              val error       = (res \ "error").as[String]
              val description = (res \ "error_description").asOpt[String]
              Future.successful(Unauthorized(views.html.error(error, description)))
            case Right((user, session)) =>
              (Users.insertOrUpdate(user) andThen Sessions.insertOrUpdate(session)).run.map { _ =>
                Redirect(routes.HomeController.index()).withNewSession
                  .addingToSession("token" -> session.id.toString)
              }
          }

      case None =>
        val error       = req.getQueryString("error").getOrElse("Erreur")
        val description = req.getQueryString("error_description")
        Future.successful(Unauthorized(views.html.error(error, description)))
    }
  }
}
