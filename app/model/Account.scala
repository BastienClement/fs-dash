package model

case class Account(
    id: Snowflake,
    label: String,
    color: Option[String],
    balance: DkpAmount,
    useDecay: Boolean
)

object Account {
  def colors: Seq[(String, String)] = Seq(
    "64B4FF" -> "From Scratch",
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
