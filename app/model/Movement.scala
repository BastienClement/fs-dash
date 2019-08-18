package model

import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneId}
import java.util.Locale

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
    item.fold(Html(""))(id => Html(s"""<a href="https://classic.wowhead.com/item=$id" class="whlink"></a>"""))
}

object Movement {
  val formatter: DateTimeFormatter = DateTimeFormatter
    .ofPattern("dd/MM, HH:mm", Locale.FRENCH)
    .withZone(ZoneId.of("Europe/Paris"))
}
