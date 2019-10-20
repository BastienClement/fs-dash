package model.dkp

import model.Snowflake

case class Account(
    id: Snowflake,
    label: String,
    color: Option[String],
    balance: DkpAmount,
    archived: Boolean,
    useDecay: Boolean,
    overdraft: DkpAmount,
    holds: DkpAmount = DkpAmount(0)
) {
  def available: DkpAmount     = balance - holds
  def withdrawLimit: DkpAmount = available + overdraft
}

object Account {
  def colors: Seq[(String, String)] = Seq(
    "F5C635" -> "From Scratch",
    "FF7D0A" -> "Druid",
    "ABD473" -> "Hunter",
    "40C7EB" -> "Mage",
    "F58CBA" -> "Paladin",
    "FFFFFF" -> "Priest",
    "FFF569" -> "Rogue",
    "0070DE" -> "Shaman",
    "8787ED" -> "Warlock",
    "C79C6E" -> "Warrior"
  )
}
