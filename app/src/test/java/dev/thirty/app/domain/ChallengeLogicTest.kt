package dev.thirty.app.domain

import dev.thirty.app.data.local.DayStatus
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ChallengeLogicTest {

    @Test
    fun dayNumber_firstDay_isOne() {
        val start = LocalDate.of(2026, 10, 4)
        assertEquals(1, ChallengeLogic.dayNumberFor(start, start))
    }

    @Test
    fun dayNumber_clampsTo30() {
        val start = LocalDate.of(2026, 1, 1)
        assertEquals(30, ChallengeLogic.dayNumberFor(start, LocalDate.of(2026, 3, 15)))
    }

    @Test
    fun dayNumber_monthBoundary() {
        val start = LocalDate.of(2026, 1, 31)
        assertEquals(2, ChallengeLogic.dayNumberFor(start, LocalDate.of(2026, 2, 1)))
    }

    @Test
    fun dayNumber_leapYear() {
        val start = LocalDate.of(2024, 2, 28)
        assertEquals(2, ChallengeLogic.dayNumberFor(start, LocalDate.of(2024, 2, 29)))
        assertEquals(3, ChallengeLogic.dayNumberFor(start, LocalDate.of(2024, 3, 1)))
    }

    @Test
    fun dayNumber_yearBoundary() {
        val start = LocalDate.of(2025, 12, 31)
        assertEquals(2, ChallengeLogic.dayNumberFor(start, LocalDate.of(2026, 1, 1)))
    }

    @Test
    fun endDate_is29DaysAfterStart() {
        val start = LocalDate.of(2026, 10, 4)
        assertEquals(LocalDate.of(2026, 11, 2), ChallengeLogic.endDateFor(start))
    }

    @Test
    fun streak_countsConsecutiveCompleted() {
        val statuses = listOf(
            DayStatus.COMPLETED, DayStatus.COMPLETED, DayStatus.COMPLETED,
            DayStatus.FUTURE, DayStatus.FUTURE
        )
        assertEquals(3, ChallengeLogic.currentStreak(statuses, 3))
    }

    @Test
    fun streak_resetsAfterMiss() {
        val statuses = listOf(
            DayStatus.COMPLETED, DayStatus.COMPLETED, DayStatus.MISSED,
            DayStatus.COMPLETED, DayStatus.FUTURE
        )
        assertEquals(1, ChallengeLogic.currentStreak(statuses, 4))
        assertEquals(0, ChallengeLogic.currentStreak(statuses, 3))
    }

    @Test
    fun bestStreak_ignoresFuture() {
        val statuses = listOf(
            DayStatus.COMPLETED, DayStatus.COMPLETED, DayStatus.MISSED,
            DayStatus.COMPLETED, DayStatus.FUTURE
        )
        assertEquals(2, ChallengeLogic.bestStreak(statuses))
    }

    @Test
    fun missedDaysToMark_onlyPastPending() {
        val statuses = listOf(
            DayStatus.COMPLETED, DayStatus.PENDING, DayStatus.FUTURE,
            DayStatus.FUTURE, DayStatus.FUTURE
        )
        assertEquals(listOf(2, 3), ChallengeLogic.missedDaysToMark(statuses, 4))
    }

    @Test
    fun missedDaysToMark_noneWhenDayOne() {
        val statuses = List(30) { DayStatus.FUTURE }
        assertTrue(ChallengeLogic.missedDaysToMark(statuses, 1).isEmpty())
    }

    @Test
    fun progress_completedCount() {
        val statuses = listOf(DayStatus.COMPLETED, DayStatus.MISSED, DayStatus.COMPLETED)
        assertEquals(2, ChallengeLogic.completedCount(statuses))
    }

    @Test
    fun milestones_matchSpec() {
        assertEquals("You started.", ChallengeLogic.milestoneFor(1))
        assertEquals("One week.\nKeep going.", ChallengeLogic.milestoneFor(7))
        assertEquals("YOU DID IT.", ChallengeLogic.milestoneFor(30))
        assertEquals(null, ChallengeLogic.milestoneFor(5))
    }

    @Test
    fun validateTitle_rejectsBlankAndLong() {
        assertNotNull(ChallengeLogic.validateTitle("   "))
        assertNotNull(ChallengeLogic.validateTitle("a".repeat(121)))
        assertEquals(null, ChallengeLogic.validateTitle("Read 20 minutes"))
    }

    @Test
    fun validateTarget_optionalPositive() {
        assertEquals(null, ChallengeLogic.validateTargetValue(""))
        assertNotNull(ChallengeLogic.validateTargetValue("abc"))
        assertNotNull(ChallengeLogic.validateTargetValue("-5"))
        assertNotNull(ChallengeLogic.validateTargetValue("0"))
        assertEquals(null, ChallengeLogic.validateTargetValue("20"))
    }

    @Test
    fun restDay_keepsStreakAlive() {
        val statuses = listOf(
            DayStatus.COMPLETED, DayStatus.REST, DayStatus.COMPLETED
        )
        assertEquals(3, ChallengeLogic.currentStreak(statuses, 3))
        assertEquals(3, ChallengeLogic.bestStreak(statuses))
    }

    @Test
    fun restDay_countsTowardProgress() {
        val statuses = listOf(DayStatus.COMPLETED, DayStatus.REST, DayStatus.MISSED)
        assertEquals(1, ChallengeLogic.completedCount(statuses))
        assertEquals(2, ChallengeLogic.progressCount(statuses))
        assertEquals(1, ChallengeLogic.restCount(statuses))
        assertEquals(true, ChallengeLogic.hasUsedRest(statuses))
        assertEquals(false, ChallengeLogic.hasUsedRest(listOf(DayStatus.COMPLETED)))
    }

    @Test
    fun durations_labelsAndValidation() {
        assertEquals("1 week", ChallengeLogic.durationLabel(7))
        assertEquals("2 weeks", ChallengeLogic.durationLabel(14))
        assertEquals("3 weeks", ChallengeLogic.durationLabel(21))
        assertEquals("30 days", ChallengeLogic.durationLabel(30))
        assertEquals("2 months", ChallengeLogic.durationLabel(60))
        assertEquals("1 year", ChallengeLogic.durationLabel(365))
        assertEquals(true, ChallengeLogic.isValidLength(7))
        assertEquals(true, ChallengeLogic.isValidLength(365))
        assertEquals(false, ChallengeLogic.isValidLength(45))
    }

    @Test
    fun dayNumber_respectsLength() {
        val start = LocalDate.of(2026, 10, 4)
        assertEquals(LocalDate.of(2026, 10, 10), ChallengeLogic.endDateFor(start, 7))
        assertEquals(7, ChallengeLogic.dayNumberFor(start, LocalDate.of(2026, 10, 20), 7))
        assertEquals(1, ChallengeLogic.dayNumberFor(start, start, 365))
    }

    @Test
    fun milestones_dynamicLength() {
        assertEquals("You started.", ChallengeLogic.milestoneFor(1, 7))
        assertEquals("YOU DID IT.", ChallengeLogic.milestoneFor(7, 7))
        assertEquals("Halfway.\nKeep going.", ChallengeLogic.milestoneFor(3, 7))
        assertEquals(null, ChallengeLogic.milestoneFor(2, 7))
        assertEquals("YOU DID IT.", ChallengeLogic.milestoneFor(365, 365))
    }

    @Test
    fun journeyStats_keptPercentWeekdayAndStreak() {
        // Week of Monday 2026-10-05; today is Saturday 2026-10-10 (Sunday not due yet).
        val days = (0 until 7).map { LocalDate.of(2026, 10, 5).plusDays(it.toLong()) }
        val statuses = listOf(
            DayStatus.COMPLETED, // Mon
            DayStatus.COMPLETED, // Tue
            DayStatus.MISSED,    // Wed
            DayStatus.REST,      // Thu
            DayStatus.COMPLETED, // Fri
            DayStatus.MISSED,    // Sat
            DayStatus.FUTURE     // Sun — after today, not counted
        )
        val st = ChallengeLogic.journeyStats(
            statuses = statuses,
            epochDays = days.map { it.toEpochDay() },
            today = LocalDate.of(2026, 10, 10)
        )
        assertEquals(66, st.keptPercent) // 4 kept of 6 due
        assertEquals("Monday", st.bestWeekday) // first weekday with completions
        assertEquals(2, st.averageStreak) // [C,C] and [REST,C]
        assertEquals(3, st.completedDays)
        assertEquals(6, st.elapsedDays)
    }

    @Test
    fun journeyStats_allKept() {
        val days = (0 until 7).map { LocalDate.of(2026, 10, 5).plusDays(it.toLong()) }
        val st = ChallengeLogic.journeyStats(
            statuses = List(7) { DayStatus.COMPLETED },
            epochDays = days.map { it.toEpochDay() },
            today = LocalDate.of(2026, 10, 11)
        )
        assertEquals(100, st.keptPercent)
        assertEquals(7, st.averageStreak)
        assertEquals(7, st.elapsedDays)
    }

    @Test
    fun journeyStats_empty() {
        val st = ChallengeLogic.journeyStats(emptyList(), emptyList())
        assertEquals(0, st.keptPercent)
        assertEquals("", st.bestWeekday)
        assertEquals(0, st.averageStreak)
        assertEquals(0, st.completedDays)
    }
}
