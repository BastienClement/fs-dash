package controllers

import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters.{firstDayOfMonth, firstDayOfNextMonth}
import java.time.{Instant, ZoneId}

import controllers.DkpController.DetailedMovement
import db.api._
import db.{Accounts, Movements, Transactions, Users}
import model._
import play.api.data.Form
import play.api.data.Forms._
import play.api.mvc.{Action, AnyContent, Result}

class DkpController extends DashController {
  def index: Action[AnyContent] = DashAction.async { implicit req =>
    for {
      accounts     <- Accounts.sortBy(a => a.label).result
      total        <- Accounts.map(a => a.balance).sum.getOrElse(DkpAmount(0)).result
      transactions <- Transactions.sortBy(t => t.id.desc).take(10).result
    } yield {
      Ok(views.html.dkp.index(accounts, total, transactions))
    }
  }

  def account(id: Snowflake, date: Option[Long] = None): Action[AnyContent] = DashAction.async { implicit req =>
    val timezone  = ZoneId.of("Europe/Paris")
    val pointDate = date.map(Instant.ofEpochMilli).getOrElse(Instant.now).atZone(timezone).truncatedTo(ChronoUnit.DAYS)
    val dateStart = pointDate.`with`(firstDayOfMonth).toInstant
    val dateEnd   = pointDate.`with`(firstDayOfNextMonth).toInstant

    println(dateStart, dateEnd)

    def movementsForBalance = Movements.filter(m => m.account === id && m.date < dateEnd)
    def balance             = movementsForBalance.sortBy(m => m.id.desc).take(1).map(m => m.balance).sum.result
    def matchingMovements   = movementsForBalance.filter(m => m.date >= dateStart)

    def detailedMovementsData =
      (for {
        (m, t) <- matchingMovements.joinLeft(Transactions).on { case (m, t) => m.transaction === t.id }
        u      <- Users if u.id === m.author
      } yield (m, t, u)).sortBy { case (m, _, _) => m.id }

    for {
      optAccount <- Accounts.filter(a => a.id === id).result.headOption
      balance    <- balance
      movements  <- detailedMovementsData.result.map(ms => ms.map(DetailedMovement.tupled))
    } yield {
      optAccount.fold(NotFound(views.html.error("Erreur", Some("Ce compte n'existe pas."))))(account => {
        val previous = dateStart.atZone(timezone).minusMonths(1).toInstant.toEpochMilli
        val next     = dateStart.atZone(timezone).plusMonths(1).toInstant.toEpochMilli
        Ok(
          views.html.dkp.account(
            account,
            movements,
            balance.getOrElse(DkpAmount(0)),
            previous,
            next,
            DateTimeFormatter.ofPattern("MMM YYYY").withZone(timezone).format(dateStart)
          )
        )
      })
    }
  }

  def transaction(id: Snowflake): Action[AnyContent] = DashAction.async { implicit req =>
    transactionPage(id, DkpController.addMovementForm)
  }

  def transactionPost(id: Snowflake): Action[AnyContent] = DashAction.check(_.isOfficer).async { implicit req =>
    DkpController.addMovementForm
      .bindFromRequest()
      .fold(
        errors => transactionPage(id, errors),
        data => {
          (Movements ++= data.accounts.map(
            account =>
              Movement(
                Snowflake.next,
                Instant.now,
                account,
                Some(id),
                data.label,
                data.amount,
                DkpAmount.dummy,
                data.details,
                req.user.id,
                data.item
              )
          )) andThen
            DBIO.successful(Redirect(routes.DkpController.transaction(id)))
        }
      )
  }

  private def transactionPage[A](id: Snowflake, form: Form[DkpController.AddMovement])(
      implicit req: DashRequest[A]
  ): DBIOAction[Result, NoStream, R] = {
    for {
      optTransaction <- Transactions.filter(t => t.id === id).result.headOption
      movements <- Movements
                    .filter(m => m.transaction === id)
                    .join(Accounts)
                    .on { case (m, a) => m.account === a.id }
                    .sortBy { case (m, _) => m.id }
                    .result
      accounts <- Accounts.map(a => a.id -> a.label).result.map(_.map { case (id, label) => id.toString -> label })
    } yield {
      optTransaction.fold(NotFound(views.html.error("Erreur", Some("Cette transaction n'existe pas.")))) {
        transaction =>
          Ok(views.html.dkp.transaction(transaction, movements, form, accounts))
      }
    }
  }

  def createAccount: Action[AnyContent] = DashAction.check(_.isOfficer) { implicit req =>
    Ok(views.html.dkp.createAccount(DkpController.createAccountForm))
  }

  def createAccountPost: Action[AnyContent] = DashAction.check(_.isOfficer).async { implicit req =>
    DkpController.createAccountForm
      .bindFromRequest()
      .fold(
        errors => DBIO.successful(BadRequest(views.html.dkp.createAccount(errors))),
        create => {
          val id = Snowflake.next
          (Accounts += Account(id, create.label, DkpAmount(0), create.useDecay))
            .map(_ => Redirect(routes.DkpController.account(id)))
        }
      )
  }

  def createTransaction: Action[AnyContent] = DashAction.check(_.isOfficer) { implicit req =>
    Ok(views.html.dkp.createTransaction(DkpController.createTransactionForm))
  }

  def createTransactionPost: Action[AnyContent] = DashAction.check(_.isOfficer).async { implicit req =>
    DkpController.createTransactionForm
      .bindFromRequest()
      .fold(
        errors => DBIO.successful(BadRequest(views.html.dkp.createTransaction(errors))),
        create => {
          val id = Snowflake.next
          (Transactions += Transaction(id, create.label, create.details))
            .map(_ => Redirect(routes.DkpController.transaction(id)))
        }
      )
  }
}

object DkpController {
  case class CreateAccount(label: String, useDecay: Boolean)

  val createAccountForm = Form(
    mapping(
      "label"        -> text(minLength = 3),
      "enable-decay" -> boolean
    )(CreateAccount.apply)(CreateAccount.unapply)
  )

  case class CreateTransaction(label: String, details: String)

  val createTransactionForm = Form(
    mapping(
      "label"   -> text(minLength = 1, maxLength = 20),
      "details" -> text
    )(CreateTransaction.apply)(CreateTransaction.unapply)
  )

  case class AddMovement(
      accounts: Seq[Snowflake],
      transaction: Option[Snowflake],
      label: String,
      details: String,
      amount: DkpAmount,
      item: Option[Int]
  )

  val addMovementForm = Form(
    mapping(
      "accounts"    -> seq(of[Snowflake]),
      "transaction" -> optional(of[Snowflake]),
      "label"       -> text(minLength = 1, maxLength = 20),
      "details"     -> text,
      "amount"      -> of[DkpAmount],
      "item"        -> optional(number(min = 1))
    )(AddMovement.apply)(AddMovement.unapply)
  )

  case class DetailedMovement(
      movement: Movement,
      transaction: Option[Transaction],
      author: User
  )
}
