package model

import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicInteger

import db.api._
import play.api.data.FormError
import play.api.data.format.Formatter
import play.api.libs.json.{Format, Reads, Writes}

import scala.util.Try

case class Snowflake(value: Long) extends AnyVal {
  override def toString: String = java.lang.Long.toString(value)
}

object Snowflake {
  final private val epoch = 1514764800000L

  private lazy val random = new SecureRandom()

  private def workerId  = random.nextInt(0x800)
  private val increment = new AtomicInteger(0)

  val dummy = Snowflake(-1)

  def next: Snowflake =
    Snowflake(
      (((System.currentTimeMillis() - epoch) & 0x1FFFFFFFFFFL) << 21) |
        ((workerId & 0X7FFL) << 10) |
        (increment.getAndIncrement() & 0X3FFL)
    )

  def fromString(string: String): Snowflake =
    Snowflake(java.lang.Long.parseLong(string))

  implicit val format: Format[Snowflake] =
    Format(Reads.of[String].map(fromString), Writes.of[String].contramap[Snowflake](_.toString))

  implicit val tt: BaseColumnType[Snowflake] =
    MappedColumnType.base[Snowflake, Long](_.value, Snowflake.apply)

  implicit val formatter: Formatter[Snowflake] = new Formatter[Snowflake] {
    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Snowflake] =
      data.get(key).toRight(Seq(FormError(key, "error.required", Nil))).flatMap { s =>
        Try(fromString(s)).toEither.left.map(t => Seq(FormError(key, t.getMessage, Nil)))
      }

    override def unbind(key: String, value: Snowflake): Map[String, String] =
      Map(key -> value.toString)
  }
}
