package model.trades

import model.Snowflake
import model.dkp.DkpAmount

case class Limit(
    user: Snowflake,
    askTotal: DkpAmount,
    askRemaining: DkpAmount,
    bidTotal: DkpAmount,
    bidRemaining: DkpAmount
) {
  def askLimit: DkpAmount = askTotal + askRemaining
  def bidLimit: DkpAmount = bidTotal + bidRemaining
}
