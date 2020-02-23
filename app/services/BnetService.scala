package services

import java.time.Instant

import db.WowItems
import db.api._
import javax.inject.{Inject, Singleton}
import model.WowItem
import play.api.Configuration
import play.api.libs.json.JsArray
import play.api.libs.ws.{WSAuthScheme, WSClient}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, Promise}

@Singleton
class BnetService @Inject()(conf: Configuration, ws: WSClient)(implicit ec: ExecutionContext) {

  private val clientId     = conf.get[String]("dash.bnet.oauth.id")
  private val clientSecret = conf.get[String]("dash.bnet.oauth.secret")

  private val oauthApi = "https://us.battle.net/oauth/token"
  private val wowApi   = "https://us.api.blizzard.com"

  private[this] var tokenPromise: Promise[String] = null
  private[this] var tokenExpire: Deadline         = Deadline.now

  def fetchToken: Future[String] = this.synchronized {
    if (tokenPromise == null || tokenExpire.isOverdue) {
      tokenExpire = Deadline.now + 30.seconds
      tokenPromise = Promise()
      tokenPromise.completeWith {
        ws.url(s"$oauthApi")
          .withAuth(clientId, clientSecret, WSAuthScheme.BASIC)
          .post(Map("grant_type" -> "client_credentials"))
          .flatMap {
            case r if r.status == 200 =>
              this.synchronized { tokenExpire = Deadline.now + (r.json \ "expires_in").as[Int].seconds }
              Future.successful((r.json \ "access_token").as[String])
            case r =>
              this.synchronized { tokenExpire = Deadline.now }
              Future.failed(new Exception(r.body))
          }
      }
    }
    tokenPromise.future
  }

  def parseQuality(code: String): Int = code match {
    case "POOR"      => 0
    case "COMMON"    => 1
    case "UNCOMMON"  => 2
    case "RARE"      => 3
    case "EPIC"      => 4
    case "LEGENDARY" => 5
    case _           => 6
  }

  def queryItem(token: String, id: Int): Future[WowItem] = {
    def query(url: String) =
      ws.url(url)
        .withQueryStringParameters(
          "access_token" -> token,
          "namespace"    -> "static-classic-us",
          "locale"       -> "en_US"
        )
        .get

    val item  = query(s"$wowApi/data/wow/item/$id")
    val media = query(s"$wowApi/data/wow/media/item/$id")

    (item zip media).flatMap {
      case (i, m) if i.status == 200 && m.status == 200 =>
        val icon = (m.json \ "assets")
          .as[JsArray]
          .value
          .collectFirst {
            case o if (o \ "key").as[String] == "icon" => (o \ "value").as[String]
          }
          .map(i => i.split('/').last.split('.').head)

        Future.successful(
          WowItem(
            id,
            (i.json \ "name").as[String],
            parseQuality((i.json \ "quality" \ "type").as[String]),
            icon.get,
            Instant.now,
            stale = false,
            i.json
          )
        )

      case (i, m) if i.status == 404 || m.status == 404 =>
        println(wowApi, i, m, token)
        Future.failed(new Exception("Item not found"))

      case (i, _) if i.status != 200 =>
        Future.failed(new Exception(i.body))

      case (_, m) if m.status != 200 =>
        Future.failed(new Exception(m.body))
    }
  }

  def fetchItem(id: Int)(implicit db: Database): Future[WowItem] = {
    WowItems
      .filter(i => i.id === id && !i.stale)
      .result
      .headOption
      .flatMap {
        case Some(item) =>
          DBIO.successful(item)
        case None =>
          DBIO
            .from(
              for {
                token <- fetchToken
                item  <- queryItem(token, id)
              } yield item
            )
            .flatMap { item =>
              WowItems.insertOrUpdate(item).map(_ => item)
            }
      }
      .run
  }
}
