package model

import java.time.{LocalDate, LocalDateTime, LocalTime}

case class Event(
    id: Snowflake,
    date: LocalDateTime,
    label: String,
    isNote: Boolean
) {
  def day: LocalDate  = date.toLocalDate
  def time: LocalTime = date.toLocalTime
}
