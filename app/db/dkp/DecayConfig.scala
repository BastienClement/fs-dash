package db.dkp

import db.api._
import slick.sql.SqlAction

class DecayConfig(tag: Tag) extends Table[Double](tag, "dkp_decay_config") {
  def key   = column[String]("key", O.PrimaryKey)
  def value = column[Double]("value")

  override def * = value
}

object DecayConfig extends TableQuery(new DecayConfig(_)) {
  private def get(key: String): SqlAction[Double, NoStream, R] =
    DecayConfig.filter(c => c.key === key).result.head

  def tradeTax = get("trade_tax")
}
