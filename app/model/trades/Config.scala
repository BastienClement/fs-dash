package model.trades

import model.dkp.DkpAmount

case class Config(
    dkpPerGold: Double,
    sellMargin: Double,
    maxBuyModifier: Double,
    maxSellModifier: Double,
    defaultBuyLimit: Double,
    defaultSellLimit: Double,
    individualBuyLimit: DkpAmount,
    individualSellLimit: DkpAmount
)
