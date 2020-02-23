package model.auctions

import model.Snowflake
import model.dkp.DkpAmount

case class RollingLimit(
    user: Snowflake,
    askTotal: DkpAmount,
    askAvailable: DkpAmount,
    askBurstable: Boolean,
    bidTotal: DkpAmount,
    bidAvailable: DkpAmount,
    bidBurstable: Boolean
)
