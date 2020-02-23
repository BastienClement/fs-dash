package controllers

import controllers.TradesController.{TradesRequestImpl, adjustSkuForm}
import db.api._
import db.dkp.{AccountAccesses, Accounts}
import db.trades._
import db.{Users, WowItems}
import model.dkp.Account
import model.trades.{Config, Order, Sku, SkuStatus}
import model.{Snowflake, User, WowItem}
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json.Json
import play.api.mvc._
import services.Services

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class TradesController extends DashController with TradesRequestImpl {
  def index = TradesAction.async { implicit req =>
    for {
      limit <- Limits.forUser(req.user.id).result.head
      sessions <- Sessions
                   .filter(s => s.closeDate.isEmpty)
                   .join(WowItems)
                   .on { case (s, i) => s.sku === i.id }
                   .join(Skus)
                   .on { case ((s, _), sku) => s.sku === sku.item }
                   .map { case ((s, i), sku) => (s, i, sku, sku.status) }
                   .sortBy { case (_, i, _, _) => i.name.asc }
                   .result
      orders <- Orders
                 .filter(o => o.owner === req.user.id && !o.archived)
                 .join(Sessions)
                 .on { case (o, s) => o.session === s.id }
                 .join(WowItems)
                 .on { case ((_, s), i) => s.sku === i.id }
                 .map { case ((o, s), i) => (o, s, i) }
                 .sortBy { case (o, _, _) => (o.closed.asc, o.id.desc) }
                 .result
    } yield Ok(views.html.trades.index(limit, sessions, orders, req.config))
  }

  def item(id: Int) = TradesAction.async { implicit req =>
    for {
      limit   <- Limits.forUser(req.user.id).result.head
      item    <- WowItems.findById(id).result.head
      session <- Sessions.filter(s => s.closeDate.isEmpty && s.sku === id).result.head
      orders <- Orders
                 .filter(o => !o.closed && o.session === session.id)
                 .join(Users)
                 .on { case (o, u) => o.owner === u.id }
                 .result
    } yield {
      val (asks, bids) = orders.partition { case (o, _) => o.kind == "ask" }
      Ok(views.html.trades.item(item, session, asks, bids, limit, req.account.available))
    }
  }

  def createOrder(item: Int) = TradesAction.async { implicit req =>
    val order = TradesController.createOrderForm
      .bindFromRequest()
      .fold(_ => ???, identity)

    for {
      session <- Sessions.filter(s => s.closeDate.isEmpty && s.sku === item).result.head
      _ <- Orders += Order(
            Snowflake.next,
            session.id,
            req.user.id,
            req.account.id,
            order.kind,
            order.quantity,
            closed = false,
            None,
            None,
            None,
            archived = false
          )
    } yield Redirect(routes.TradesController.item(item))
  }

  def deleteOrder(item: Int, kind: String) = TradesAction.async { implicit req =>
    for {
      session <- Sessions.filter(s => s.closeDate.isEmpty && s.sku === item).result.head
      _       <- Orders.filter(o => o.owner === req.user.id && o.kind === kind && o.session === session.id).delete
    } yield Redirect(routes.TradesController.item(item))
  }

  def catalog = DashAction.officers.async { implicit req =>
    Skus
      .map(sku => (sku, sku.status))
      .join(WowItems)
      .on { case ((sku, _), i) => sku.item === i.id }
      .map { case ((s, ss), i) => (s, ss, i) }
      .sortBy { case (s, _, i) => ((s.buying || s.selling).desc, i.name.asc) }
      .result
      .map { skus =>
        Ok(views.html.trades.catalog(skus))
      }
  }

  def add = DashAction.officers.async { implicit req =>
    Future.successful(Ok(views.html.trades.add()))
  }

  def fetchItem(id: Int) = DashAction.officers.async { implicit req =>
    services.bnetService
      .fetchItem(id)
      .transform {
        case Success(item) => Success(Json.obj("item" -> item))
        case Failure(err)  => Success(Json.obj("err"  -> err.getMessage))
      }
      .map(Ok(_))
  }

  def sku(id: Int) = (DashAction.officers andThen TradesAction).async { implicit req =>
    withSku(id) { (item, sku, status, history) =>
      val configure = TradesController.configureSkuForm.fill(sku)
      Future.successful(Ok(views.html.trades.sku(item, sku, status, history, configure, adjustSkuForm, req.config)))
    }
  }

  def configureSku(id: Int) = (DashAction.officers andThen TradesAction).async { implicit req =>
    withSku(id) { (item, sku, status, history) =>
      TradesController.configureSkuForm
        .bindFromRequest()
        .fold(
          errors =>
            Future
              .successful(
                BadRequest(views.html.trades.sku(item, sku, status, history, errors, adjustSkuForm, req.config))
              ),
          sku => for { _ <- Skus insertOrUpdate sku } yield Redirect(routes.TradesController.catalog())
        )
    }
  }

  def adjustSku(id: Int) = (DashAction.officers andThen TradesAction).async { implicit req =>
    adjustSkuForm
      .bindFromRequest()
      .fold(
        _ => Future.successful(Redirect(routes.TradesController.sku(id))),
        adjust =>
          for { _ <- History += ((id, req.user.id, adjust.quantity, adjust.label)) } yield Redirect(
            routes.TradesController.sku(id)
          )
      )
  }

  private def withSku(
      id: Int
  )(
      fn: (WowItem, Sku, SkuStatus, Seq[(HistoryLine, Option[User])]) => Future[Result]
  )(implicit req: DashRequest[_]): Future[Result] = {
    def unknownObject = NotFound(views.html.error("Objet inconnu", Some("L'objet demandé n'est pas connu.")))
    for {
      optItem <- WowItems.findById(id).result.headOption.run
      sku     <- Skus.filter(s => s.item === id).map(s => (s, s.status)).result.headOption.run
      history <- HistoryView
                  .filter(h => h.sku === id)
                  .joinLeft(Users)
                  .on { case (h, u) => h.user === u.id }
                  .sortBy { case (h, _) => h.date.desc }
                  .result
                  .run
      result <- optItem.fold(Future.successful(unknownObject))(
                 i =>
                   fn(
                     i,
                     sku.map(_._1).getOrElse(Sku.default(i.id)),
                     sku.map(_._2).getOrElse(SkuStatus(id, 0, None, None)),
                     history
                   )
               )
    } yield result
  }
}

object TradesController {
  private val percentage = number(min = 100, max = 200).transform[Double](
    int => int / 100.0,
    double => (double * 100).toInt
  )

  private val configureSkuForm = Form(
    mapping(
      "item"              -> number,
      "buying"            -> boolean,
      "selling"           -> boolean,
      "target_supply"     -> number(min = 0),
      "max_buy_modifier"  -> optional(percentage),
      "max_sell_modifier" -> optional(percentage),
      "buy_limit"         -> optional(number(min = 0)),
      "sell_limit"        -> optional(number(min = 0))
    )(Sku(_, _, _, _, _, _, _, _)) { sku =>
      Some(
        (
          sku.item,
          sku.buying,
          sku.selling,
          sku.targetSupply,
          sku.maxBuyModifier,
          sku.maxSellModifier,
          sku.buyLimit,
          sku.sellLimit
        )
      )
    }
  )

  case class AdjustSku(quantity: Int, label: String)

  private val adjustSkuForm = Form(
    mapping(
      "quantity" -> number,
      "label"    -> text
    )(AdjustSku.apply)(AdjustSku.unapply)
  )

  case class CreateOrder(kind: String, quantity: Int)

  private val createOrderForm = Form(
    mapping(
      "kind"     -> text,
      "quantity" -> number
    )(CreateOrder.apply)(CreateOrder.unapply)
  )

  class TradesRequest[A](val account: Account, val config: Config, request: DashRequest[A])(
      implicit services: Services
  ) extends DashRequest(request.optUser, request)

  private[TradesController] trait TradesRequestImpl { this: TradesController =>
    private[TradesController] val TradesAction: ActionBuilder[TradesRequest, AnyContent] =
      DashAction.authenticated andThen new ActionRefiner[DashRequest, TradesRequest] {
        implicit override protected def executionContext: ExecutionContext = TradesRequestImpl.this.executionContext

        override protected def refine[A](request: DashRequest[A]): Future[Either[Result, TradesRequest[A]]] = {
          (fetchAccount(request) zip Configs.fetch).map {
            case (Some(account), config) =>
              Right(new TradesRequest(account, config, request))
            case _ =>
              Left(
                Forbidden(
                  views.html.error(
                    "Module indisponible",
                    Some(
                      "Vous n'avez aucun compte DKP associé à votre utilisateur.\n" +
                        "Si vous pensez qu'il s'agit d'une erreur, adressez-vous à un officier."
                    )
                  )(request)
                )
              )
          }.run
        }

        private def fetchAccount(request: DashRequest[_]): DBIOAction[Option[Account], NoStream, R] =
          (for {
            access  <- AccountAccesses if access.owner === request.user.id && access.main
            account <- Accounts if account.id === access.account && !account.archived
          } yield (access, account))
            .sortBy { case (access, account) => (access.main.desc, account.label.asc) }
            .map { case (_, a) => a }
            .result
            .headOption
      }
  }
}
