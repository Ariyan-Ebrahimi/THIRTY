package dev.thirty.app.util

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object DateUtils {
    fun today(): LocalDate = LocalDate.now(ZoneId.systemDefault())

    fun toEpochDay(date: LocalDate): Long = date.toEpochDay()

    fun fromEpochDay(epochDay: Long): LocalDate = LocalDate.ofEpochDay(epochDay)

    fun formatMedium(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))

    fun formatMonth(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("MMMM yyyy"))

    fun isSameDay(a: LocalDate, b: LocalDate): Boolean = a.toEpochDay() == b.toEpochDay()

    /** Days between start (inclusive) and end (exclusive), in local dates. */
    fun daysBetween(start: LocalDate, end: LocalDate): Long =
        end.toEpochDay() - start.toEpochDay()
}
