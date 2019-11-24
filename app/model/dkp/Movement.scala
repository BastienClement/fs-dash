package model.dkp

import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneId}
import java.util.Locale

import model.Snowflake
import play.twirl.api.Html

case class Movement(
    id: Snowflake,
    date: Instant,
    account: Snowflake,
    transaction: Option[Snowflake],
    label: String,
    amount: DkpAmount,
    balance: DkpAmount,
    details: String,
    author: Option[Snowflake],
    item: Option[Int]
) {
  def humanDate: String = Movement.formatter.format(date)

  def itemLink: Html =
    item.fold(Html("")) {
      case id if id > 0 =>
        Html(s"""<a href="https://classic.wowhead.com/item=$id" class="whlink"></a>""")
      case id if id == 0 =>
        Html(s"""<a class="whlink icontinyl q1" data-wh-icon-added="true" style="padding-left: 18px !important;
                |background: url(https://wow.zamimg.com/images/wow/icons/tiny/inv_misc_coin_01.gif) left center no-repeat;
                |"><span>Golds</span></a>""".stripMargin)
      case _ =>
        Html("")
    }
}

object Movement {
  val formatter: DateTimeFormatter = DateTimeFormatter
    .ofPattern("dd/MM, HH:mm", Locale.FRENCH)
    .withZone(ZoneId.of("Europe/Paris"))
}
