package controllers

import java.time.format.DateTimeFormatter
import java.time.{DayOfWeek, LocalDate, ZoneId}

import controllers.CalendarController.{Day, EuropeParis, HumanMonth, ShortMonth}
import db.Events
import db.api._
import javax.inject._
import model.{Event, Snowflake}

import scala.jdk.CollectionConverters._

@Singleton
class CalendarController extends DashController {

  def index(month: String) = DashAction.authenticated.async { implicit req =>
    val thisMonth = Option(month)
      .map(m => s"$m-01")
      .map(LocalDate.parse)
      .getOrElse(LocalDate.now(EuropeParis))
      .withDayOfMonth(1)

    val prevMonth = thisMonth.minusMonths(1)
    val nextMonth = thisMonth.plusMonths(1)

    val firstDay = thisMonth.`with`(DayOfWeek.MONDAY)

    Events
      .filter(e => e.date >= thisMonth.atStartOfDay && e.date < nextMonth.atStartOfDay)
      .sortBy(e => (e.date, e.label))
      .result
      .map(events => events.groupBy(e => e.day.toString))
      .map { eventsByDate =>
        val days = for {
          day <- firstDay.datesUntil(firstDay.plusDays(6 * 7)).iterator.asScala
        } yield {
          Day(thisMonth, day, eventsByDate.getOrElse(day.toString, Nil).sortBy(e => (!e.isNote, e.date)))
        }

        Ok(
          views.html.calendar.index(
            days.toList.grouped(7).toList,
            thisMonth.format(HumanMonth).capitalize,
            prevMonth.format(ShortMonth),
            nextMonth.format(ShortMonth)
          )
        )
      }
  }

  def createNote(date: String) = DashAction { implicit req =>
    ???
  }

  def createEvent(date: String) = DashAction { implicit req =>
    ???
  }

  def event(id: Snowflake) = DashAction { implicit req =>
    ???
  }
}

object CalendarController {
  val EuropeParis: ZoneId           = ZoneId.of("Europe/Paris")
  val ShortMonth: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM")
  val DayOfMonth: DateTimeFormatter = DateTimeFormatter.ofPattern("dd")
  val HumanMonth: DateTimeFormatter = DateTimeFormatter.ofPattern("LLLL yyyy")

  case class Day(month: LocalDate, date: LocalDate, events: Seq[Event]) {
    def dayOfMonth: String   = date.format(DayOfMonth)
    def dateString: String   = date.toString
    def isSameMonth: Boolean = date.format(ShortMonth) == month.format(ShortMonth)
    def isToday: Boolean     = LocalDate.now(EuropeParis) == date
  }
}
