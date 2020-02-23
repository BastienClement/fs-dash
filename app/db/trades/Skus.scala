package db.trades

import java.time.Instant

import db.api._
import model.trades.{Sku, SkuStatus}

class Skus(tag: Tag) extends Table[Sku](tag, "trades_skus") {
  def item            = column[Int]("item", O.PrimaryKey)
  def buying          = column[Boolean]("buying")
  def selling         = column[Boolean]("selling")
  def targetSupply    = column[Int]("target_supply")
  def currentSupply   = column[Int]("current_supply")
  def goldPrice       = column[Option[Int]]("gold_price")
  def lastUpdate      = column[Option[Instant]]("last_update")
  def maxBuyModifier  = column[Option[Double]]("max_buy_modifier")
  def maxSellModifier = column[Option[Double]]("max_sell_modifier")
  def buyLimit        = column[Option[Int]]("buy_limit")
  def sellLimit       = column[Option[Int]]("sell_limit")

  def * =
    (
      item,
      buying,
      selling,
      targetSupply,
      maxBuyModifier,
      maxSellModifier,
      buyLimit,
      sellLimit
    ) <> ((Sku.apply _).tupled, Sku.unapply)

  def status = (item, currentSupply, goldPrice, lastUpdate) <> (SkuStatus.tupled, SkuStatus.unapply)
}

object Skus extends TableQuery(new Skus(_)) {
  val findByItem = this.findBy(_.item)
}
