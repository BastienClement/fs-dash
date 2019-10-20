package model

import java.time.Instant

import play.api.libs.json.{Format, JsValue, Json}
import play.twirl.api.Html

case class WowItem(id: Int, name: String, quality: Int, icon: String, fetched: Instant, stale: Boolean, data: JsValue) {
  def link = Html(s"<a href='#' data-wowhead='item=$id&domain=classic' class=''>$name</a>")

  def mediumIconUrl = s"https://wow.zamimg.com/images/wow/icons/medium/$icon.jpg"
  def smallIconUrl  = s"https://wow.zamimg.com/images/wow/icons/small/$icon.jpg"
}

object WowItem {
  implicit val format: Format[WowItem] = Json.format[WowItem]
}
