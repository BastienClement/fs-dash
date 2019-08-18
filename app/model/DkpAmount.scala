package model

import db.api._
import model.DkpAmount.numeric
import play.api.data.FormError
import play.api.data.format.{Formats, Formatter}
import play.api.libs.json.{Format, Reads, Writes}
import play.twirl.api.Html

import scala.util.Try

case class DkpAmount(value: Int) extends AnyVal {
  override def toString: String =
    s"$minusSign$integer.$fractional"

  private def minusSign: String =
    if (value < 0) "-" else ""

  private def integer: String =
    math.abs(value / 100).toString

  private def fractional: String =
    math.abs(value % 100) match {
      case n if n < 10 => s"0$n"
      case n           => n.toString
    }

  def signed: String =
    s"$sign$integer.$fractional"

  private def sign: String =
    if (value < 0) "-" else "+"

  def abs: String =
    s"$integer.$fractional"

  def +(other: DkpAmount): DkpAmount = numeric.plus(this, other)
  def -(other: DkpAmount): DkpAmount = numeric.minus(this, other)

  def html: Html =
    Html(s"""<span class="dkp${if (value < 0) " negative" else ""}">
            |$minusSign$integer<span class="fractional">.$fractional</span>
            |<span class="unit">DKP</span>
            |</span>""".stripMargin)
}

object DkpAmount {
  val dummy: DkpAmount = DkpAmount(0)

  implicit val format: Format[DkpAmount] =
    Format(
      Reads.of[String].map(s => DkpAmount(s.replace(".", "").toInt)),
      Writes.of[String].contramap[DkpAmount](_.toString)
    )

  implicit val tt: BaseColumnType[DkpAmount] =
    MappedColumnType.base[DkpAmount, Int](_.value, DkpAmount.apply)

  implicit val formatter: Formatter[DkpAmount] = new Formatter[DkpAmount] {
    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], DkpAmount] =
      Formats.bigDecimalFormat
        .bind(key, if (data.contains(key)) data + (key -> data(key).replaceAll("[^0-9.\\-]+", "")) else data)
        .map(a => DkpAmount((a * 100).toInt))

    override def unbind(key: String, value: DkpAmount): Map[String, String] =
      Map(key -> value.toString)
  }

  implicit object numeric extends Numeric[DkpAmount] {
    override def plus(x: DkpAmount, y: DkpAmount): DkpAmount  = DkpAmount(x.value + y.value)
    override def minus(x: DkpAmount, y: DkpAmount): DkpAmount = DkpAmount(x.value - y.value)
    override def times(x: DkpAmount, y: DkpAmount): DkpAmount = DkpAmount(x.value * y.value)
    override def negate(x: DkpAmount): DkpAmount              = DkpAmount(-x.value)

    override def fromInt(x: Int): DkpAmount = DkpAmount(x)

    override def parseString(str: String): Option[DkpAmount] =
      Try(DkpAmount((BigDecimal(str) * 100).toInt)).toOption

    override def toInt(x: DkpAmount): Int       = x.value
    override def toLong(x: DkpAmount): Long     = x.value
    override def toFloat(x: DkpAmount): Float   = x.value.toFloat
    override def toDouble(x: DkpAmount): Double = x.value.toDouble

    override def compare(x: DkpAmount, y: DkpAmount): Int = x.value compare y.value
  }
}
