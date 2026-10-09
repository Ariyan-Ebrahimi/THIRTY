package dev.thirty.app.backup

import dev.thirty.app.data.datastore.AppSettings
import dev.thirty.app.data.datastore.ThemeMode
import dev.thirty.app.data.local.ChallengeEntity
import dev.thirty.app.data.local.DailyProgressEntity
import dev.thirty.app.data.local.DayStatus
import org.junit.Assert.*
import org.junit.Test

class BackupValidationTest {

    private fun sampleChallenge() = ChallengeEntity(
        id = 1,
        title = "Read 20 minutes",
        category = "Learning",
        targetValue = 20.0,
        targetUnit = "minutes",
        startEpochDay = 20334,
        endEpochDay = 20363,
        reminderHour = 20,
        reminderMinute = 0,
        reminderEnabled = true,
        createdAtMillis = 123456789L,
        status = "active"
    )

    private fun sampleDays() = (1..30).map {
        DailyProgressEntity(
            challengeId = 1,
            dayNumber = it,
            epochDay = 20334L + it - 1,
            status = if (it == 1) DayStatus.COMPLETED else DayStatus.FUTURE
        )
    }

    @Test
    fun export_thenValidate_ok() {
        val json = BackupManager.buildExport(
            listOf(sampleChallenge()),
            mapOf(1L to sampleDays()),
            AppSettings(ThemeMode.DARK, true, 20, 0, true)
        )
        val result = BackupManager.parseAndValidate(json)
        assertTrue(result is BackupResult.Ok)
        assertEquals(1, (result as BackupResult.Ok).challenges)
        assertEquals(30, result.days)
    }

    @Test
    fun invalidJson_rejected() {
        val r = BackupManager.parseAndValidate("{not json")
        assertTrue(r is BackupResult.Err)
    }

    @Test
    fun wrongVersion_rejected() {
        val json = """{"version":99,"exportedAt":"x","settings":{"theme":"SYSTEM","notificationsEnabled":true,"reminderHour":20,"reminderMinute":0,"onboardingSeen":true},"challenges":[],"dailyProgress":[]}"""
        val r = BackupManager.parseAndValidate(json)
        assertTrue(r is BackupResult.Err)
    }

    @Test
    fun blankTitle_rejected() {
        val json = """{"version":1,"exportedAt":"x","settings":{"theme":"SYSTEM","notificationsEnabled":true,"reminderHour":20,"reminderMinute":0,"onboardingSeen":true},"challenges":[{"title":"","category":"Personal","targetValue":null,"targetUnit":null,"startEpochDay":1,"endEpochDay":30,"reminderHour":20,"reminderMinute":0,"reminderEnabled":true,"createdAtMillis":0,"status":"active"}],"dailyProgress":[]}"""
        assertTrue(BackupManager.parseAndValidate(json) is BackupResult.Err)
    }

    @Test
    fun badDayNumber_rejected() {
        val json = """{"version":1,"exportedAt":"x","settings":{"theme":"SYSTEM","notificationsEnabled":true,"reminderHour":20,"reminderMinute":0,"onboardingSeen":true},"challenges":[{"title":"Read","category":"Personal","targetValue":null,"targetUnit":null,"startEpochDay":1,"endEpochDay":30,"reminderHour":20,"reminderMinute":0,"reminderEnabled":true,"createdAtMillis":0,"status":"active"}],"dailyProgress":[{"challengeIndex":0,"dayNumber":31,"epochDay":2,"status":"COMPLETED","completedAtMillis":null,"note":""}]}"""
        assertTrue(BackupManager.parseAndValidate(json) is BackupResult.Err)
    }

    @Test
    fun planDateSpanLongerThanLength_rejected() {
        val json = """{"version":1,"exportedAt":"x","settings":{"theme":"SYSTEM","notificationsEnabled":true,"reminderHour":20,"reminderMinute":0,"onboardingSeen":true},"challenges":[{"title":"Read","category":"Personal","targetValue":null,"targetUnit":null,"lengthDays":30,"startEpochDay":1,"endEpochDay":31,"reminderHour":20,"reminderMinute":0,"reminderEnabled":true,"createdAtMillis":0,"status":"active"}],"dailyProgress":[]}"""
        assertTrue(BackupManager.parseAndValidate(json) is BackupResult.Err)
    }

    @Test
    fun overflowingDateSpan_rejected() {
        val json = """{"version":1,"exportedAt":"x","settings":{"theme":"SYSTEM","notificationsEnabled":true,"reminderHour":20,"reminderMinute":0,"onboardingSeen":true},"challenges":[{"title":"Read","category":"Personal","targetValue":null,"targetUnit":null,"lengthDays":30,"startEpochDay":-9223372036854775808,"endEpochDay":9223372036854775807,"reminderHour":20,"reminderMinute":0,"reminderEnabled":true,"createdAtMillis":0,"status":"active"}],"dailyProgress":[]}"""
        assertTrue(BackupManager.parseAndValidate(json) is BackupResult.Err)
    }

    @Test
    fun orphanDays_rejected() {
        val json = """{"version":1,"exportedAt":"x","settings":{"theme":"SYSTEM","notificationsEnabled":true,"reminderHour":20,"reminderMinute":0,"onboardingSeen":true},"challenges":[],"dailyProgress":[{"challengeIndex":0,"dayNumber":1,"epochDay":2,"status":"COMPLETED","completedAtMillis":null,"note":""}]}"""
        assertTrue(BackupManager.parseAndValidate(json) is BackupResult.Err)
    }
}
