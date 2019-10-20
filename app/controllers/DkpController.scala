package controllers

import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters.{firstDayOfMonth, firstDayOfNextMonth}
import java.time.{Instant, ZoneId}

import controllers.DkpController.DetailedMovement
import db.Users
import db.api._
import db.dkp._
import model.dkp._
import model.{dkp, _}
import play.api.data.Form
import play.api.data.Forms._
import play.api.mvc.{Action, AnyContent, Result}

class DkpController extends DashController {
  def index: Action[AnyContent] = DashAction.authenticated.async { implicit req =>
    for {
      accounts <- Accounts
                   .filter(a => !a.archived && AccountAccesses.filter(aa => aa.account === a.id && aa.main).exists)
                   .sortBy(a => (a.balance.desc, a.label))
                   .result
      count <- Accounts.filter(a => !a.archived && a.useDecay).size.result
      total <- Accounts
                .filter(a => !a.archived && a.useDecay)
                .map(a => a.balance)
                .sum
                .getOrElse(DkpAmount(0))
                .result
      mines <- Accounts
                .join(AccountAccesses)
                .on { case (a, aa) => a.id === aa.account && aa.owner === req.user.id }
                .sortBy { case (a, aa) => (aa.main.desc, a.balance.desc, a.label) }
                .result
      transactions <- Transactions.sortBy(t => t.id.desc).take(10).result
    } yield {
      val groupedAccounts = accounts.groupBy(a => a.color).values.toSeq.sortBy(as => as.head.color)
      Ok(views.html.dkp.index(groupedAccounts, count, total, mines, transactions))
    }
  }

  def manage = DashAction.officers.async { implicit req =>
    for {
      accounts <- Accounts
                   .joinLeft(AccountAccesses.join(Users).on { case (a, u) => a.owner === u.id })
                   .on { case (a, (aa, _)) => a.id === aa.account }
                   .result
                   .map { rows =>
                     rows
                       .groupBy { case (a, _) => a }
                       .view
                       .mapValues { list =>
                         list.flatMap { case (_, aau) => aau }.sortBy { case (_, u) => u.username }
                       }
                       .toSeq
                       .sortBy { case (a, _) => a.label }
                   }
    } yield {
      Ok(views.html.dkp.manage(accounts))
    }
  }

  def manageArchived(id: Snowflake, state: Boolean) = DashAction.officers.async { implicit req =>
    Accounts.filter(a => a.id === id).map(_.archived).update(state) andThen
      DBIO.successful(Redirect(routes.DkpController.manage()))
  }

  def manageUseDecay(id: Snowflake, state: Boolean) = DashAction.officers.async { implicit req =>
    Accounts.filter(a => a.id === id).map(_.useDecay).update(state) andThen
      DBIO.successful(Redirect(routes.DkpController.manage()))
  }

  def manageLinks(id: Snowflake) = DashAction.officers.async { implicit req =>
    for {
      account <- Accounts.filter(a => a.id === id).result.head
      accesses <- AccountAccesses
                   .join(Users)
                   .on { case (a, u) => a.owner === u.id && a.account === id }
                   .sortBy { case (_, u) => u.username }
                   .result
      users <- Users
                .filter(u => !AccountAccesses.filter(a => a.account === id && a.owner === u.id).exists)
                .sortBy(u => u.username)
                .result
    } yield {
      Ok(views.html.dkp.manageLinks(account, accesses, users))
    }
  }

  def manageLinksPost(id: Snowflake) = DashAction.officers.async(parse.formUrlEncoded) { implicit req =>
    val access = req.body.getOrElse("access", Seq.empty).map(Snowflake.fromString)
    val mains  = req.body.getOrElse("main", Seq.empty).map(Snowflake.fromString)
    val adds   = req.body.getOrElse("add", Seq.empty).map(Snowflake.fromString)

    val accounts = AccountAccesses.filter(a => a.account === id)

    for {
      _ <- accounts.filter(a => !(a.owner inSet access)).delete
      _ <- accounts.filter(a => !(a.owner inSet mains)).map(_.main).update(false)
      _ <- accounts.filter(a => a.owner inSet mains).map(_.main).update(true)
      _ <- AccountAccesses ++= adds.map(user => AccountAccess(user, id, main = false))
    } yield {
      Redirect(routes.DkpController.manageLinks(id))
    }
  }

  def manageEdit(id: Snowflake) = DashAction.officers.async { implicit req =>
    ???
  }

  def account(id: Snowflake, date: Option[Long] = None): Action[AnyContent] = DashAction.authenticated.async {
    implicit req =>
      accountPage(id, date, DkpController.addMovementForm)
  }

  def accountPage(id: Snowflake, date: Option[Long] = None, form: Form[DkpController.AddMovement])(
      implicit req: DashRequest[_]
  ): DBIOAction[Result, NoStream, R] = {
    val timezone = ZoneId.of("Europe/Paris")
    val pointDate =
      date.map(Instant.ofEpochMilli).getOrElse(Instant.now).atZone(timezone).truncatedTo(ChronoUnit.DAYS)
    val dateStart = pointDate.`with`(firstDayOfMonth).toInstant
    val dateEnd   = pointDate.`with`(firstDayOfNextMonth).toInstant
    val isFuture  = dateEnd.isAfter(Instant.now)

    def movementsForBalance = Movements.filter(m => m.account === id && m.date < dateEnd)
    def balance             = movementsForBalance.sortBy(m => m.id.desc).take(1).map(m => m.balance).sum.result
    def matchingMovements   = movementsForBalance.filter(m => m.date >= dateStart)

    def detailedMovementsData =
      matchingMovements
        .joinLeft(Transactions)
        .on { case (m, t) => m.transaction === t.id }
        .joinLeft(Users)
        .on { case ((m, _), u) => m.author === u.id }
        .map { case ((m, t), u) => (m, t, u) }
        .sortBy { case (m, _, _) => m.id }

    for {
      optAccount <- Accounts.filter(a => a.id === id).result.headOption
      balance    <- balance
      movements  <- detailedMovementsData.result.map(ms => ms.map(DetailedMovement.tupled))
      holds      <- if (isFuture) Holds.filter(h => h.account === id).sortBy(_.id).result else DBIO.successful(Seq.empty)
    } yield {
      optAccount.fold(NotFound(views.html.error("Erreur", Some("Ce compte n'existe pas."))))(account => {
        val previous = dateStart.atZone(timezone).minusMonths(1).toInstant.toEpochMilli
        val next     = dateStart.atZone(timezone).plusMonths(1).toInstant.toEpochMilli
        Ok(
          views.html.dkp.account(
            account,
            movements,
            balance.getOrElse(DkpAmount(0)),
            holds,
            previous,
            next,
            DateTimeFormatter.ofPattern("MMM YYYY").withZone(timezone).format(dateStart),
            form
          )
        )
      })
    }
  }

  def transaction(id: Snowflake): Action[AnyContent] = DashAction.authenticated.async { implicit req =>
    transactionPage(id, DkpController.addMovementForm)
  }

  def createMovement(id: Option[Snowflake], account: Option[Snowflake]): Action[AnyContent] =
    DashAction.officers.async { implicit req =>
      DkpController.addMovementForm
        .bindFromRequest()
        .fold(
          errors => {
            (id, account) match {
              case (Some(tid), _) => transactionPage(tid, errors)
              case (_, Some(aid)) => accountPage(aid, None, errors)
            }
          },
          data => {
            (Movements ++= (data.accounts ++ account).map(
              account =>
                dkp.Movement(
                  Snowflake.next,
                  Instant.now,
                  account,
                  id,
                  data.label,
                  data.amount,
                  DkpAmount.dummy,
                  data.details,
                  Some(req.user.id),
                  data.item
                )
            )) andThen
              DBIO.successful(Redirect((id, account) match {
                case (Some(tid), _) => routes.DkpController.transaction(tid)
                case (_, Some(aid)) => routes.DkpController.account(aid, None)
              }))
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
      accounts <- Accounts
                   .filter(a => !a.archived)
                   .map(a => a.id -> a.label)
                   .result
                   .map(_.map { case (id, label) => id.toString -> label })
    } yield {
      optTransaction.fold(NotFound(views.html.error("Erreur", Some("Cette transaction n'existe pas.")))) {
        transaction =>
          Ok(views.html.dkp.transaction(transaction, movements, form, accounts))
      }
    }
  }

  def createAccount: Action[AnyContent] = DashAction.officers { implicit req =>
    Ok(views.html.dkp.createAccount(DkpController.createAccountForm))
  }

  def createAccountPost: Action[AnyContent] = DashAction.officers.async { implicit req =>
    DkpController.createAccountForm
      .bindFromRequest()
      .fold(
        errors => DBIO.successful(BadRequest(views.html.dkp.createAccount(errors))),
        create => {
          val id = Snowflake.next
          (Accounts += Account(
            id,
            create.label,
            Some(create.color),
            DkpAmount(0),
            archived = false,
            useDecay = true,
            overdraft = DkpAmount(0)
          )).map(_ => Redirect(routes.DkpController.account(id)))
        }
      )
  }

  def createTransaction: Action[AnyContent] = DashAction.officers { implicit req =>
    Ok(views.html.dkp.createTransaction(DkpController.createTransactionForm))
  }

  def createTransactionPost: Action[AnyContent] = DashAction.officers.async { implicit req =>
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
  case class CreateAccount(label: String, color: String)

  val createAccountForm = Form(
    mapping(
      "label" -> text(minLength = 3),
      "color" -> text(minLength = 6, maxLength = 6)
    )(CreateAccount.apply)(CreateAccount.unapply)
  )

  case class CreateTransaction(label: String, details: String)

  val createTransactionForm = Form(
    mapping(
      "label"   -> text(minLength = 1, maxLength = 30),
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
      "label"       -> text(minLength = 1, maxLength = 30),
      "details"     -> text,
      "amount"      -> of[DkpAmount],
      "item"        -> optional(number(min = 1))
    )(AddMovement.apply)(AddMovement.unapply)
  )

  case class DetailedMovement(
      movement: Movement,
      transaction: Option[Transaction],
      author: Option[User]
  )
}
