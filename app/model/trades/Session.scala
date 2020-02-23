package model.trades

import java.time.Instant

import model.Snowflake
import model.dkp.DkpAmount
import play.twirl.api.Html

case class Session(
    id: Snowflake,
    sku: Int,
    openDate: Instant,
    closeDate: Option[Instant],
    buyPrice: DkpAmount,
    buyQuantity: Int,
    buyOrders: Int,
    sellPrice: DkpAmount,
    sellQuantity: Int,
    sellOrders: Int
) {
  def buyBonus(status: SkuStatus, config: Config): Double =
    buyPrice.value / (status.goldPrice.getOrElse(0) * config.dkpPerGold) - 1

  def formatBuyBonus(status: SkuStatus, config: Config): Html =
    formatBonus(buyBonus(status, config))

  private def formatBonus(bonus: Double): Html =
    if (bonus > 0.1) Html(s"+${(bonus * 100 / 5).round * 5}%") else Html("")

  def priceForKind(kind: String): DkpAmount =
    if (kind == "ask") buyPrice else sellPrice
}
