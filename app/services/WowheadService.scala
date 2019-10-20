package services

import java.time.Instant

import db.WowItems
import db.api._
import javax.inject.{Inject, Singleton}
import model.WowItem
import play.api.Configuration
import play.api.libs.json.JsNull
import play.api.libs.ws.WSClient

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class WowheadService @Inject()(conf: Configuration, ws: WSClient)(implicit ec: ExecutionContext) {

  private val wowheadApi = "https://classic.wowhead.com"

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
              ws.url(s"$wowheadApi/item=$id&xml")
                .get()
                .map(r => r.xml)
                .flatMap {
                  case wowhead if (wowhead \ "error").nonEmpty =>
                    Future.failed(new RuntimeException((wowhead \ "error").text))
                  case wowhead =>
                    Future.successful(wowhead \ "item")
                }
                .map { item =>
                  WowItem(
                    id = (item \@ "id").toInt,
                    name = (item \ "name").text,
                    quality = (item \ "quality" \@ "id").toInt,
                    icon = (item \ "icon").text,
                    fetched = Instant.now,
                    stale = false,
                    data = JsNull
                  )
                }
            )
            .flatMap { item =>
              WowItems.insertOrUpdate(item).map(_ => item)
            }
      }
      .run
  }
}
