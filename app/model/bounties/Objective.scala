package model.bounties

import model.Snowflake
import model.dkp.DkpAmount
import play.twirl.api.Html

case class Objective(
    id: Snowflake,
    bounty: Snowflake,
    label: String,
    item: Option[Int],
    reward: Option[DkpAmount],
    count: Option[Int],
    progress: Int,
    showPercentage: Boolean
) {
  def progressPercent: String =
    (count.map(progress.toDouble / _).getOrElse(0.0).max(0.0).min(1.0) * 100).round.toString

  def itemLink: Html =
    item.fold(Html(""))(id => Html(s"""<a href="https://classic.wowhead.com/item=$id" class="whlink"></a>"""))

  def completed: Boolean = count.contains(progress)
}
