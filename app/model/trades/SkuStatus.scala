package model.trades

import java.time.Instant

import play.twirl.api.Html

case class SkuStatus(
    item: Int,
    currentSupply: Int,
    goldPrice: Option[Int],
    lastUpdate: Option[Instant]
) {
  def formattedGoldPrice: Html = {
    goldPrice.fold(Html("<span style='color: #999'>?</span>")) { price =>
      val golds = price / 100
      val silvers = price % 100 match {
        case s if s < 10 => s"0$s"
        case s           => s"$s"
      }

      Html(s"<span class='dkp'>$golds<span class='fractional'>.$silvers</span><img style='vertical-align: -2px; margin-left: 2px; margin-right: 4px;' src='https://wow.zamimg.com/images/icons/money-gold.gif'></span>")
    }
  }
}
