package db.trades

import db.api._
import model.dkp.DkpAmount
import model.trades.Config

import scala.concurrent.ExecutionContext

class Configs(tag: Tag) extends Table[(String, Double)](tag, "trades_config") {
  def key   = column[String]("key")
  def value = column[Double]("value")

  def * = (key, value)
}

object Configs extends TableQuery(new Configs(_)) {
  def fetch(implicit ec: ExecutionContext): DBIOAction[Config, NoStream, R] =
    for (cfg <- Configs.result.map(_.toMap)) yield {
      Config(
        dkpPerGold = cfg("dkp_per_gold"),
        sellMargin = cfg("sell_margin"),
        maxBuyModifier = cfg("max_buy_modifier"),
        maxSellModifier = cfg("max_sell_modifier"),
        defaultBuyLimit = cfg("default_buy_limit"),
        defaultSellLimit = cfg("default_sell_limit"),
        individualBuyLimit = DkpAmount(cfg("individual_buy_limit").toInt),
        individualSellLimit = DkpAmount(cfg("individual_sell_limit").toInt)
      )
    }
}
