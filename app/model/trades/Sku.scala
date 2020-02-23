package model.trades

case class Sku(
    item: Int,
    buying: Boolean,
    selling: Boolean,
    targetSupply: Int,
    maxBuyModifier: Option[Double],
    maxSellModifier: Option[Double],
    buyLimit: Option[Int],
    sellLimit: Option[Int]
)

object Sku {
  def default(item: Int): Sku = Sku(item, buying = true, selling = true, 0, None, None, None, None)
}
