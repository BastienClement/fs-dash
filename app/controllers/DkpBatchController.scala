package controllers

import db.Users
import db.api._
import db.dkp.{AccountAccesses, Accounts}
import model.User
import model.dkp.{Account, AccountAccess}
import org.apache.commons.lang3.StringUtils
import play.api.libs.json.{JsArray, Json}

import scala.collection.mutable
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success}

class DkpBatchController extends DashController {
  def batch = DashAction.officers { implicit req =>
    Ok(views.html.dkp.batch())
  }

  def batchProcess(url: String) = DashAction.officers.async { implicit req =>
    val accountsQuery =
      Users
        .join(AccountAccesses)
        .on { case (u, aa) => aa.owner === u.id }
        .join(Accounts)
        .on {
          case ((_, aa), a) => !a.archived && aa.account === a.id
        }
        .map { case ((u, aa), a) => (u, aa, a) }
        .result
        .map { rows =>
          rows
            .groupBy { case (u, _, _) => u }
            .view
            .mapValues(seq => seq.map { case (_, aa, a) => (aa, a) })
            .toList
            .filter { case (_, accounts) => accounts.exists { case (aa, _) => aa.main } }
            .map(DkpBatchController.buildMatchUser)
        }
        .run

    val annotationsResult = services.vision
      .annotate(url)
      .map(l => l.filterNot(a => a.getDescription.length > 30))

    val results = for ((accounts, annotations) <- accountsQuery zip annotationsResult) yield {
      val withoutDiacritics = mutable.Map[String, String]().withDefault(StringUtils.stripAccents)

      for (annotation <- annotations;
           text       <- annotation.getDescription.split("\\s+");
           if text.nonEmpty;
           textWithoutDiacritics = withoutDiacritics(text.replaceAll("[^\\p{L}]+", ""));
           if textWithoutDiacritics.length >= 3)
        yield {
          (
            annotation,
            accounts
              .map { account =>
                val (label, score) =
                  account.accountsWithoutDiacritics
                    .map { label =>
                      val length = math.min(label.length, textWithoutDiacritics.length)
                      val distance = DkpBatchController
                        .levenshtein(label.substring(0, length), textWithoutDiacritics.substring(0, length))
                      (label, 1.0 - (distance.toDouble / length))
                    }
                    .maxBy { case (_, score) => score }

                (account, score, label.length)
              }
              .filter { case (_, score, _) => score >= 0.7 }
              .sortBy { case (_, score, length) => (-score, -length) }
              .map { case (a, score, _) => (a, score) }
              .take(3)
          )
        }
    }

    results.transform {
      case Success(res) =>
        val response = JsArray(res.map {
          case (annotation, matches) =>
            Json.obj(
              "text" -> annotation.getDescription,
              "rect" -> (annotation.getBoundingPoly.getVerticesList.asScala.toList match {
                case List(a, _, c, _) =>
                  Json.obj(
                    "x" -> a.getX,
                    "y" -> a.getY,
                    "w" -> (c.getX - a.getX),
                    "h" -> (c.getY - a.getY)
                  )
              }),
              "matches" -> JsArray(matches.map { case (u, score) => u.toJson(score) })
            )
        })
        Success(Ok(response))

      case Failure(e) =>
        Success(UnprocessableEntity(Json.obj("err" -> e.getMessage)))
    }
  }
}

object DkpBatchController {
  def levenshtein(s1: String, s2: String): Int = {
    val memoizedCosts = mutable.Map[(Int, Int), Int]()

    def lev(k1: Int, k2: Int): Int = {
      memoizedCosts.getOrElseUpdate((k1, k2), (k1, k2) match {
        case (i, 0) => i
        case (0, j) => j
        case (i, j) =>
          Seq(
            1 + lev(i - 1, j),
            1 + lev(i, j - 1),
            lev(i - 1, j - 1) + (if (s1(i - 1) != s2(j - 1)) 1 else 0)
          ).min
      })
    }

    lev(s1.length, s2.length)
    memoizedCosts(s1.length, s2.length)
  }

  case class MatchUser(user: User, mainAccount: Account, accounts: Seq[Account]) {
    val accountsWithoutDiacritics: Seq[String] = accounts.map(a => StringUtils.stripAccents(a.label))

    def toJson(score: Double) = Json.obj(
      "user" -> Json.obj(
        "id"       -> user.id,
        "username" -> user.username,
        "color"    -> user.color,
        "isPvP"    -> user.isMember
      ),
      "account" -> Json.obj(
        "id"    -> mainAccount.id,
        "label" -> mainAccount.label,
        "color" -> mainAccount.color
      ),
      "score" -> score
    )
  }

  def buildMatchUser(row: (User, Seq[(AccountAccess, Account)])): MatchUser = row match {
    case (user, accounts) =>
      MatchUser(user, accounts.collectFirst { case (aa, a) if aa.main => a }.get, accounts.map { case (_, a) => a })
  }
}
