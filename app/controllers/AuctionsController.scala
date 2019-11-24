package controllers

import java.time.{Instant, ZoneId}

import controllers.AuctionsController.{DetailedMatch, ItemOrder}
import db.api._
import db.auctions.{Matches, OrderOverviews, Orders, PendingMatches}
import db.dkp.{AccountAccesses, Accounts, DecayConfig}
import db.{Users, WowItems}
import javax.inject._
import model.auctions.{Match, Order, OrderOverview, PendingMatch}
import model.dkp.{Account, DkpAmount}
import model.{Snowflake, User, WowItem}
import play.api.libs.json.Json
import play.api.mvc.{ActionBuilder, ActionRefiner, AnyContent, Result}
import play.twirl.api.Html

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

@Singleton
class AuctionsController extends DashController with AuctionsController.AuctionRequestImpl {

  def index = AuctionAction.async { implicit req =>
    for {
      orders <- OrderOverviews
                 .join(WowItems)
                 .on { case (o, i) => o.item === i.id }
                 .sortBy { case (_, i) => ((i.id < 1).desc, i.name) }
                 .result
      mines <- Orders
                .filter(o => o.owner === req.user.id && o.closed.isEmpty)
                .join(WowItems)
                .on { case (o, i) => o.item === i.id }
                .sortBy { case (o, _) => o.validity.asc }
                .result
      matches <- (for {
                  m    <- Matches if matchIsMine(m)
                  bid  <- Orders if bid.id === m.bid
                  ask  <- Orders if ask.id === m.ask
                  item <- WowItems if item.id === bid.item
                } yield (m, bid.owner, ask.owner, item)).sortBy(_._1.matched).result
      pending <- if (req.user.isOfficer) Matches.filter(m => m.ackStatus.isEmpty).size.result
                else DBIO.successful(0)
      gold <- WowItems.filter(i => i.id === 0).result.head
    } yield {
      Ok(views.html.auctions.index(
        if (orders.exists { case (_, i) => i.id == 0 }) orders
        else ((OrderOverview(0, 0, None, 0, None), gold)) +: orders,
        mines,
        matches,
        pending
      ))
    }
  }

  private def matchIsMine(m: Matches)(implicit req: DashRequest[_]): Rep[Boolean] = {
    m.ackStatus.isEmpty && Orders.filter(o => (o.id in Query(m.bid) ++ Query(m.ask)) && o.owner === req.user.id).exists
  }

  def create = DashAction.authenticated { implicit req =>
    Ok(views.html.auctions.create())
  }

  def fetchItem(id: Int) = DashAction.authenticated.async { implicit req =>
    services.bnetService
      .fetchItem(id)
      .transform {
        case Success(item) => Success(Json.obj("item" -> item))
        case Failure(err)  => Success(Json.obj("err"  -> err.getMessage))
      }
      .map(Ok(_))
  }

  def item(id: Int) = AuctionAction.async { implicit req =>
    for {
      optItem <- WowItems.filter(i => i.id === id).result.headOption
      orders <- Orders
                 .filter(o => o.item === id && o.closed.isEmpty)
                 .joinLeft(Users)
                 .on { case (o, u) => o.owner === u.id }
                 .result
      nextMatch <- PendingMatches.filter(m => m.item === id).sortBy(_.execution).result.headOption
      tradeTax  <- DecayConfig.tradeTax
    } yield {
      optItem match {
        case None =>
          NotFound(views.html.error("Item non-trouvé", Some("Cet object n'existe pas dans la base de données.")))
        case Some(item) =>
          Ok(views.html.auctions.item(item, req.accounts, processOrders(orders, nextMatch), tradeTax))
      }
    }
  }

  private def processOrders(
      orderAndUsers: Seq[(Order, Option[User])],
      nextMatch: Option[PendingMatch]
  ): Seq[ItemOrder] = {
    orderAndUsers
      .map {
        case (o, u) => ItemOrder(o, u, nextMatch.filter(m => m.ask == o.id || m.bid == o.id))
      }
      .sortWith {
        case (a, b) if a.order.price != b.order.price => a.order.price > b.order.price
        case (a, b) if a.order.kind != b.order.kind   => a.order.kind == "ask"
        case (a, b) if a.order.kind == "ask"          => a.order.posted.isAfter(b.order.posted)
        case (a, b) if a.order.kind == "bid"          => a.order.posted.isBefore(b.order.posted)
        case _                                        => false
      }
  }

  def itemPost(id: Int) = DashAction.authenticated.async(parse.formUrlEncoded) { implicit req =>
    val form = req.body

    val kind       = form("kind").head
    val account    = form.get("account").map(v => Snowflake.fromString(v.head))
    val guildOrder = form.get("guild-order").isDefined && req.user.isOfficer

    val quantity = form("quantity").head.replaceAll("[^0-9]", "").toInt
    val price    = DkpAmount((form("price").head.replaceAll("[^0-9\\.]", "").toFloat * 100).toInt)

    val delay = form("delay").head.toInt

    assert(Set("ask", "bid").contains(kind))
    assert(!req.user.isTrial || kind == "bid")
    assert(account.isDefined || guildOrder)
    assert(quantity > 0)
    assert(price > DkpAmount(0))
    assert(delay >= 0)

    val canAccessAccount =
      if (guildOrder) DBIO.successful(true)
      else AccountAccesses.filter(a => a.account === account.get && a.owner === req.user.id).exists.result

    val accountHasFunds =
      if (guildOrder) DBIO.successful(true)
      else
        Accounts
          .filter(a => a.id === account.get)
          .result
          .head
          .map(a => kind == "ask" || (a.withdrawLimit >= (price * quantity)))

    val conflictingOffers =
      Orders
        .filter(o => o.item === id && o.kind === kind && o.closed.isEmpty)
        .filter(o => !(o.priceInt === price.value) && ((o.priceInt - price.value).abs < (o.priceInt * 5 / 100)))
        .exists
        .result

    val validity =
      if (delay > 0)
        Instant
          .now()
          .minusSeconds(60 * 60 * 6)
          .atZone(ZoneId.of("Europe/Paris"))
          .toLocalDate
          .plusDays(delay)
          .atTime(20, 0)
          .atZone(ZoneId.of("Europe/Paris"))
          .toInstant
      else
        Instant.now()

    (canAccessAccount zip accountHasFunds zip conflictingOffers).flatMap {
      case ((true, true), false) =>
        val order = Order(
          id = Snowflake.next,
          kind = kind,
          owner = if (guildOrder) None else Some(req.user.id),
          account = if (guildOrder) None else account,
          item = id,
          quantity = quantity,
          remaining = quantity,
          price = price,
          hold = None,
          posted = Instant.now,
          validity = validity,
          closed = None
        )
        (Orders += order) andThen DBIO.successful(Redirect(routes.AuctionsController.item(id)))
      case (_, true) =>
        DBIO.successful(
          BadRequest(views.html.error("Erreur", Some("Votre offre est trop proche d'une offre existante.")))
        )
      case ((false, _), _) =>
        DBIO.successful(BadRequest(views.html.error("Erreur", Some("Vous n'avez pas accès à ce compte"))))
      case ((_, false), _) =>
        DBIO.successful(BadRequest(views.html.error("Erreur", Some("Solde insufisant"))))
    }
  }

  def closeOrder(id: Int, order: Snowflake) = DashAction.authenticated.async { implicit req =>
    def canCloseOrder(o: Orders): Rep[Option[Boolean]] =
      if (req.user.isOfficer) o.owner === req.user.id || o.owner.isEmpty
      else o.owner === req.user.id

    Orders.filter(o => o.id === order && canCloseOrder(o)).map(o => o.closed).update(Some(Instant.now)) andThen
      DBIO.successful(Redirect(routes.AuctionsController.item(id)))
  }

  def pending = DashAction.officers.async { implicit req =>
    for (m <- detailedMatches) yield Ok(views.html.auctions.pending(m))
  }

  def ackMatching(id: Snowflake, status: Boolean) = DashAction.officers.async { implicit req =>
    Matches
      .filter(m => m.id === id)
      .map(m => (m.ackStatus, m.ackDate))
      .update((Some(status), Some(Instant.now)))
      .andThen(DBIO.successful(Redirect(routes.AuctionsController.pending())))
  }

  def history = DashAction.officers.async { implicit req =>
    ???
  }

  private def detailedMatches: DBIOAction[Seq[DetailedMatch], NoStream, R] = {
    (for {
      m <- Matches if m.ackStatus.isEmpty
      a <- Orders if a.id === m.ask
      b <- Orders if b.id === m.bid
      i <- WowItems if i.id === a.item
    } yield (m, a, b, i))
      .joinLeft(Users)
      .on { case ((_, a, _, _), u) => a.owner === u.id }
      .map { case ((m, _, b, i), a) => (m, a, b, i) }
      .joinLeft(Users)
      .on { case ((_, _, b, _), u) => b.owner === u.id }
      .map { case ((m, a, _, i), b) => (m, a, b, i) }
      .result
      .map { rows =>
        import AuctionsController.formatUser
        rows
          .map { case (m, a, b, i) => (m, formatUser(a), formatUser(b), i) }
          .sortBy { case (m, _, _, _) => m.id.value }
      }
  }
}

object AuctionsController {
  type DetailedMatch = (Match, (String, String), (String, String), WowItem)

  def formatUser(user: Option[User]): (String, String) = user match {
    case Some(u) => (u.username, u.color)
    case None    => ("From Scratch", "F5C635")
  }

  def formatTimer(order: Order, nextMatch: Option[PendingMatch]): (Option[Html], Option[String]) = {
    def makeTimer(icon: String, duration: Long): Html =
      Html(s"<div data-timer='$duration'><i class='material-icons'>$icon</i> <span></span></div>")

    (
      order.validity.getEpochSecond - Instant.now.getEpochSecond,
      nextMatch.map(m => m.execution.getEpochSecond - Instant.now.getEpochSecond)
    ) match {
      case (delay, _) if delay > 0 =>
        (Some(makeTimer("hourglass_empty", delay)), Some("delayed"))
      case (_, Some(execution)) =>
        (Some(makeTimer("check", execution)), Some("matched"))
      case _ =>
        (None, None)
    }
  }

  case class ItemOrder(order: Order, user: Option[User], nextMatch: Option[PendingMatch]) {
    def id: Snowflake  = order.id
    def kind: String   = order.kind
    def remaining: Int = order.remaining

    def askPrice: Option[Html] = if (order.kind == "ask") Some(order.price.html) else None
    def bidPrice: Option[Html] = if (order.kind == "bid") Some(order.price.html) else None

    def isMine(implicit req: DashRequest[_]): Boolean = order.owner match {
      case Some(id) => id == req.user.id
      case None     => req.user.isOfficer
    }

    val (userName, userColor) = formatUser(user)
    val (timer, cssClass)     = formatTimer(order, nextMatch)
  }

  case class AuctionRequest[A](accounts: Seq[Account], req: DashRequest[A]) {
    def user = req.user
  }

  private[AuctionsController] trait AuctionRequestImpl { this: AuctionsController =>
    private[AuctionsController] val AuctionAction: ActionBuilder[AuctionRequest, AnyContent] =
      DashAction.authenticated andThen new ActionRefiner[DashRequest, AuctionRequest] {
        implicit override protected def executionContext: ExecutionContext = AuctionRequestImpl.this.executionContext

        override protected def refine[A](request: DashRequest[A]): Future[Either[Result, AuctionRequest[A]]] = {
          (for {
            access  <- AccountAccesses if access.owner === request.user.id
            account <- Accounts if account.id === access.account && !account.archived
          } yield (access, account))
            .sortBy { case (access, account) => (access.main.desc, account.label.asc) }
            .map { case (_, a) => a }
            .result
            .map {
              case accounts if accounts.nonEmpty =>
                Right(AuctionRequest(accounts, request))
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
            }
            .run
        }
      }

    implicit protected def DashRequestFromAuctionRequest[A](implicit ar: AuctionRequest[A]): DashRequest[A] = ar.req
  }
}
