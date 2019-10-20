package db.driver

import java.sql.{PreparedStatement, ResultSet}

import akka.Done
import com.github.tminglei.slickpg._
import db.driver.PostgresProfile.{AscribedType, ImplicitOps, ShortEffects}
import play.api.mvc.Result
import slick.ast._
import slick.basic.Capability
import slick.dbio.{DBIO, DBIOAction, Effect, NoStream}
import slick.jdbc.{JdbcCapabilities, JdbcType}

import scala.annotation.tailrec
import scala.concurrent.Future
import scala.language.implicitConversions
import scala.reflect.ClassTag
import scala.util.Try

trait PostgresProfile
    extends ExPostgresProfile
    with PgArraySupport
    with PgDate2Support
    with PgRangeSupport
    with PgHStoreSupport
    with PgPlayJsonSupport
    with PgSearchSupport
    with PgNetSupport
    with PgLTreeSupport {

  override def computeCapabilities: Set[Capability] = super.computeCapabilities + JdbcCapabilities.insertOrUpdate

  override val pgjson = "jsonb"

  @tailrec private def ascriptionForType(tpe: Type): Option[String] = tpe match {
    case ot: OptionType      => ascriptionForType(ot.elementType)
    case at: AscribedType[_] => Some(at.ascription)
    case _                   => None
  }

  override def valueToSQLLiteral(v: Any, tpe: Type): String =
    ascriptionForType(tpe)
      .foldLeft(super.valueToSQLLiteral(v, tpe)) { (literal, ascription) =>
        s"$literal::$ascription"
      }

  override def jdbcTypeFor(t: Type): JdbcType[Any] = t match {
    case at: AscribedType[_] =>
      val wrapped = super.jdbcTypeFor(at.wrapped)
      new JdbcType[Any] {
        override def sqlType: Int = java.sql.Types.OTHER

        override def sqlTypeName(size: Option[FieldSymbol]): String         = at.ascription.toUpperCase
        override def setValue(v: Any, p: PreparedStatement, idx: Int): Unit = p.setObject(idx, v, sqlType)
        override def setNull(p: PreparedStatement, idx: Int): Unit          = wrapped.setNull(p, idx)
        override def getValue(r: ResultSet, idx: Int): Any                  = wrapped.getValue(r, idx)
        override def wasNull(r: ResultSet, idx: Int): Boolean               = wrapped.wasNull(r, idx)
        override def updateValue(v: Any, r: ResultSet, idx: Int): Unit      = wrapped.updateValue(v, r, idx)
        override def valueToSQLLiteral(value: Any): String                  = wrapped.valueToSQLLiteral(value)
        override def hasLiteralForm: Boolean                                = wrapped.hasLiteralForm
        override def scalaType                                              = wrapped.scalaType
        override def classTag: ClassTag[_]                                  = wrapped.classTag
      }
    case other =>
      super.jdbcTypeFor(other)
  }

  override val api =
    new API with ArrayImplicits with DateTimeImplicits with PlayJsonImplicits with NetImplicits with LTreeImplicits
    with RangeImplicits with HStoreImplicits with SearchImplicits with SearchAssistants with ImplicitOps
    with ShortEffects
}

object PostgresProfile extends PostgresProfile {
  implicit final class DBIOActionOps[T, E <: Effect](private val action: DBIOAction[T, NoStream, E]) extends AnyVal {
    @inline def run(implicit db: api.Database): Future[T] = db.run(action)
  }

  implicit final class DBIOObjectOps(private val dbio: DBIO.type) extends AnyVal {
    @inline def unit: DBIOAction[Unit, NoStream, Effect] = DBIOObjectOps.singleton_unit
    @inline def done: DBIOAction[Done, NoStream, Effect] = DBIOObjectOps.singleton_done

    @inline def fromTry[T](t: Try[T]): DBIOAction[T, NoStream, Effect] = t.fold(DBIO.failed, DBIO.successful)
  }

  object DBIOObjectOps {
    val singleton_unit = DBIO.successful(())
    val singleton_done = DBIO.successful(Done)
  }

  trait ImplicitOps {
    @inline implicit final def DBIOActionOps[T, E <: Effect](action: DBIOAction[T, NoStream, E]): DBIOActionOps[T, E] =
      new DBIOActionOps(action)

    @inline implicit final def DBIOObjectOps(dbio: DBIO.type): DBIOObjectOps =
      new DBIOObjectOps(dbio)

    @inline implicit final def FutureToDBIO[T](future: Future[T]): DBIOAction[T, NoStream, Effect] =
      DBIO.from(future)

    @inline implicit final def DBIOToFutureResult[S <: NoStream, E <: Effect](
        dbio: DBIOAction[Result, S, E]
    )(implicit db: api.Database): Future[Result] =
      new JdbcActionExtensionMethods(dbio).transactionally.run

    @inline implicit final def ResultToDBIO(result: Result): DBIO[Result] =
      DBIO.successful(result)

    implicit final class AscribedTypeBuilder[T](private val ascription: String) {
      @inline def @::(tt: BaseTypedType[T]) = new AscribedType(tt, ascription)
    }
  }

  class AscribedType[T](val wrapped: BaseTypedType[T], val ascription: String) extends BaseTypedType[T] {
    def scalaType: ScalaType[T] = wrapped.scalaType
    def classTag: ClassTag[_]   = wrapped.classTag
  }

  trait ShortEffects {
    type R   = Effect.Read
    type W   = Effect.Write
    type T   = Effect.Transactional
    type RW  = Effect.Read with Effect.Write
    type RT  = Effect.Read with Effect.Transactional
    type WT  = Effect.Write with Effect.Transactional
    type RWT = Effect.Read with Effect.Write with Effect.Transactional
  }
}
