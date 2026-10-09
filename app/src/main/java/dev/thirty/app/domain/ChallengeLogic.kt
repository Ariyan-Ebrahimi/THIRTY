package dev.thirty.app.domain

import dev.thirty.app.data.local.DayStatus
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Pure, testable challenge logic. All date math uses [LocalDate] (device-local),
 * never UTC instants, so DST / timezone behaviour stays consistent.
 */
object ChallengeLogic {

    const val CHALLENGE_LENGTH = 30

    /** Allowed plan lengths in days. */
    val DURATIONS = listOf(7, 14, 21, 30, 60, 90, 365)

    fun durationLabel(days: Int): String = when (days) {
        7 -> "1 week"
        14 -> "2 weeks"
        21 -> "3 weeks"
        30 -> "30 days"
        60 -> "2 months"
        90 -> "3 months"
        365 -> "1 year"
        else -> "$days days"
    }

    fun isValidLength(days: Int): Boolean = days in DURATIONS

    /** 1-based day number for [today] given challenge [startDate], clamped to 1..[length]. */
    fun dayNumberFor(startDate: LocalDate, today: LocalDate, length: Int = CHALLENGE_LENGTH): Int {
        val diff = (today.toEpochDay() - startDate.toEpochDay()).toInt() + 1
        return diff.coerceIn(1, length.coerceAtLeast(1))
    }

    fun endDateFor(startDate: LocalDate, length: Int = CHALLENGE_LENGTH): LocalDate =
        startDate.plusDays((length.coerceAtLeast(1) - 1).toLong())

    fun dateForDay(startDate: LocalDate, dayNumber: Int): LocalDate =
        startDate.plusDays((dayNumber - 1).toLong())

    /**
     * Given the ordered list of statuses for days 1..N (only past/today matter),
     * compute current streak: consecutive honored days (completed or rest)
     * ending at the latest decided position. Future days are ignored.
     */
    /**
     * A rest day keeps the streak alive without counting as a show-up.
     * It is earned (one per journey) and never punishes progress.
     */
    private fun countsForStreak(s: DayStatus): Boolean =
        s == DayStatus.COMPLETED || s == DayStatus.REST

    fun currentStreak(statusesByDay: List<DayStatus>, todayDayNumber: Int): Int {
        if (statusesByDay.isEmpty()) return 0
        val upto = todayDayNumber.coerceIn(0, statusesByDay.size)
        var streak = 0
        for (i in (upto - 1) downTo 0) {
            if (countsForStreak(statusesByDay[i])) streak++ else break
        }
        return streak
    }

    fun bestStreak(statusesByDay: List<DayStatus>): Int {
        var best = 0
        var run = 0
        for (s in statusesByDay) {
            if (countsForStreak(s)) {
                run++
                if (run > best) best = run
            } else if (s == DayStatus.MISSED) {
                run = 0
            }
            // PENDING/FUTURE do not break a historical best run; they just end iteration.
        }
        return best
    }

    fun completedCount(statusesByDay: List<DayStatus>): Int =
        statusesByDay.count { it == DayStatus.COMPLETED }

    /** Days honored (shown up + rest). Drives the progress bar. */
    fun progressCount(statusesByDay: List<DayStatus>): Int =
        statusesByDay.count { it == DayStatus.COMPLETED || it == DayStatus.REST }

    fun restCount(statusesByDay: List<DayStatus>): Int =
        statusesByDay.count { it == DayStatus.REST }

    fun hasUsedRest(statusesByDay: List<DayStatus>): Boolean =
        statusesByDay.any { it == DayStatus.REST }

    /**
     * Refresh rule applied on app foreground / midnight:
     * any day with number < todayDayNumber that is still PENDING/FUTURE becomes MISSED.
     * Returns the day numbers that transitioned.
     */
    fun missedDaysToMark(
        statusesByDay: List<DayStatus>,
        todayDayNumber: Int
    ): List<Int> {
        val result = mutableListOf<Int>()
        for (day in 1..(todayDayNumber - 1).coerceAtMost(statusesByDay.size)) {
            val s = statusesByDay[day - 1]
            if (s == DayStatus.PENDING || s == DayStatus.FUTURE) result += day
        }
        return result
    }

    fun milestoneFor(dayNumber: Int, length: Int = CHALLENGE_LENGTH): String? {
        if (dayNumber == 1) return "You started."
        if (dayNumber == length) return "YOU DID IT."
        if (length == 30) {
            return when (dayNumber) {
                3 -> "You\u2019re showing up."
                7 -> "One week.\nKeep going."
                14 -> "Two weeks.\nYou\u2019re building a routine."
                21 -> "21 days.\nKeep going."
                else -> null
            }
        }
        val q1 = length / 4
        val half = length / 2
        val q3 = (3 * length) / 4
        return when (dayNumber) {
            q1 -> if (q1 > 1) "You\u2019re showing up." else null
            half -> "Halfway.\nKeep going."
            q3 -> if (q3 != half) "Almost there.\nKeep going." else null
            else -> null
        }
    }

    fun validateTitle(raw: String): String? {
        val t = raw.trim()
        if (t.isEmpty()) return "Please enter your goal."
        if (t.length > 120) return "Keep it under 120 characters."
        return null
    }

    fun validateTargetValue(raw: String): String? {
        val t = raw.trim()
        if (t.isEmpty()) return null // optional
        val v = t.toDoubleOrNull() ?: return "Enter a number."
        if (v <= 0) return "Must be positive."
        if (v > 1_000_000) return "That\u2019s a bit much."
        return null
    }

    /** Consistency metrics for one journey (running or finished). */
    data class JourneyStats(
        /** Share of days already due that were kept (completed or rest). */
        val keptPercent: Int,
        /** Weekday name with the most completions, e.g. "Monday". */
        val bestWeekday: String,
        /** Average length of kept-day runs; a rest day continues the run. */
        val averageStreak: Int,
        val completedDays: Int,
        val elapsedDays: Int
    )

    fun journeyStats(
        statuses: List<DayStatus>,
        epochDays: List<Long>,
        today: LocalDate = LocalDate.now()
    ): JourneyStats {
        val n = statuses.size.coerceAtMost(epochDays.size)
        if (n == 0) return JourneyStats(0, "", 0, 0, 0)

        var kept = 0
        var elapsed = 0
        var completed = 0
        val byWeekday = mutableMapOf<DayOfWeek, Int>()

        for (i in 0 until n) {
            val date = LocalDate.ofEpochDay(epochDays[i])
            if (date.isAfter(today)) continue
            elapsed++
            when (statuses[i]) {
                DayStatus.COMPLETED -> {
                    kept++
                    completed++
                    val dow = date.dayOfWeek
                    byWeekday[dow] = (byWeekday[dow] ?: 0) + 1
                }
                DayStatus.REST -> kept++
                else -> Unit
            }
        }

        var runLen = 0
        var runTotal = 0
        var runCount = 0
        for (i in 0 until n) {
            val s = statuses[i]
            if (s == DayStatus.COMPLETED || s == DayStatus.REST) {
                runLen++
            } else if (runLen > 0) {
                runTotal += runLen
                runCount++
                runLen = 0
            }
        }
        if (runLen > 0) {
            runTotal += runLen
            runCount++
        }

        val best = byWeekday.maxByOrNull { it.value }?.key
        return JourneyStats(
            keptPercent = if (elapsed == 0) 0 else (kept * 100) / elapsed,
            bestWeekday = best
                ?.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                ?.lowercase()
                ?.replaceFirstChar { it.uppercase() }
                .orEmpty(),
            averageStreak = if (runCount == 0) 0 else runTotal / runCount,
            completedDays = completed,
            elapsedDays = elapsed
        )
    }
}
