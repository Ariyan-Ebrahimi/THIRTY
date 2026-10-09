package dev.thirty.app.util

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class DateUtilsTest {

    @Test
    fun today_isLocalDate() {
        assertEquals(LocalDate.now(), DateUtils.today())
    }

    @Test
    fun epochDay_roundTrip() {
        val d = LocalDate.of(2026, 2, 29 - 1) // Feb 28 non-leap-safe
        assertEquals(d, DateUtils.fromEpochDay(DateUtils.toEpochDay(d)))
    }

    @Test
    fun daysBetween_monthChange() {
        assertEquals(1, DateUtils.daysBetween(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 1)))
    }

    @Test
    fun daysBetween_yearChange() {
        assertEquals(1, DateUtils.daysBetween(LocalDate.of(2025, 12, 31), LocalDate.of(2026, 1, 1)))
    }

    @Test
    fun formatMedium_notEmpty() {
        assertTrue(DateUtils.formatMedium(LocalDate.of(2026, 10, 4)).isNotBlank())
    }
}
