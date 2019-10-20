package controllers

import java.time.Instant

import db.api.{Database, _}
import db.driver.PostgresProfile
import db.{Sessions, Users}
import javax.inject.Inject
import model.{Session, Snowflake, User}
import play.api.db.slick.DatabaseConfigProvider
import play.api.mvc._
import services.Services

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class DashController extends BaseController with play.api.i18n.I18nSupport {
  @Inject protected var cc: ControllerComponents                    = _
  override protected def controllerComponents: ControllerComponents = cc

  implicit protected def executionContext: ExecutionContext = cc.executionContext

  @Inject protected var _services: Services = _
  implicit protected def services: Services = _services

  @Inject protected var _db: DatabaseConfigProvider = _
  implicit protected def db: Database               = _db.get[PostgresProfile].db

  private def DashRequestRefiner: ActionTransformer[Request, DashRequest] =
    new ActionTransformer[Request, DashRequest] {
      override protected def transform[A](request: Request[A]): Future[DashRequest[A]] = {
        request.session.get("token").map(Snowflake.fromString) match {
          case Some(token) =>
            Sessions
              .filter(s => s.id === token)
              .result
              .headOption
              .flatMap {
                case Some(session) if session.expiresAt isBefore Instant.now =>
                  (refreshToken(request, session) andThen DBIO.successful(session.owner)).asTry.flatMap {
                    case Success(user) => DBIO.successful(Some(user))
                    case Failure(_)    => Sessions.filter(s => s.id === session.id).delete andThen DBIO.successful(None)
                  }
                case Some(session) if session.lastChecked isBefore Instant.now.minusSeconds(5 * 60) =>
                  (refreshUser(session) andThen DBIO.successful(session.owner)).asTry.flatMap {
                    case Success(user) => DBIO.successful(Some(user))
                    case Failure(_)    => Sessions.filter(s => s.id === session.id).delete andThen DBIO.successful(None)
                  }
                case Some(session) =>
                  DBIO.successful(Some(session.owner))
                case None =>
                  DBIO.successful(None)
              }
              .flatMap {
                case Some(userId) =>
                  for {
                    user <- Users.findById(userId).result.head
                  } yield new DashRequest(Some(user), request)

                case None =>
                  DBIO.successful(DashRequest.default(request))
              }
              .run

          case None =>
            Future.successful(DashRequest.default(request))
        }
      }

      private def refreshUser(implicit session: Session): DBIO[_] = {
        DBIO
          .from(services.discordService.fetchUser().collect {
            case Right(user) => user
          })
          .flatMap(Users.insertOrUpdate)
          .andThen(Sessions.filter(s => s.id === session.id).map(s => s.lastChecked).update(Instant.now))
      }

      private def refreshToken(implicit request: Request[_], session: Session): DBIO[_] = {
        DBIO
          .from(services.discordService.refreshToken().collect {
            case Right(session) => session
          })
          .flatMap(s => Sessions.insertOrUpdate(s) andThen refreshUser(s))
      }

      override protected def executionContext: ExecutionContext = cc.executionContext

      implicit private val ec: ExecutionContext = executionContext
    }

  private def AuthenticatedFilter(check: User => Boolean): ActionFilter[DashRequest] = new ActionFilter[DashRequest] {
    override protected def filter[A](request: DashRequest[A]): Future[Option[Result]] =
      Future.successful {
        if (request.authenticated && check(request.user)) None
        else Some(Redirect(routes.HomeController.index()))
      }

    override protected def executionContext: ExecutionContext = cc.executionContext
  }

  class DashAction extends ActionBuilder[DashRequest, AnyContent] {
    override def invokeBlock[A](request: Request[A], block: DashRequest[A] => Future[Result]): Future[Result] =
      (Action andThen DashRequestRefiner).invokeBlock(request, block)

    def authenticated: ActionBuilder[DashRequest, AnyContent] =
      this andThen AuthenticatedFilter(_ => true)

    def officers: ActionBuilder[DashRequest, AnyContent] =
      this andThen AuthenticatedFilter(u => u.isOfficer)

    def check(check: User => Boolean): ActionBuilder[DashRequest, AnyContent] =
      this andThen AuthenticatedFilter(check)

    override protected def executionContext: ExecutionContext = cc.executionContext

    override def parser: BodyParser[AnyContent] = Action.parser
  }

  def DashAction = new DashAction
}
