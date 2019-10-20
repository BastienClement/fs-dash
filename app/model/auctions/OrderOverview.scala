package model.auctions

import model.dkp.DkpAmount

case class OrderOverview(
    item: Int,
    bidUnits: Int,
    bidPrice: Option[DkpAmount],
    askUnits: Int,
    askPrice: Option[DkpAmount]
)
