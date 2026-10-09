package dev.thirty.app.data.repository

import dev.thirty.app.data.datastore.AppSettings
import dev.thirty.app.data.datastore.SettingsStore
import dev.thirty.app.data.local.ChallengeEntity
import dev.thirty.app.data.local.ChallengeStatus
import dev.thirty.app.data.local.DailyProgressEntity
import dev.thirty.app.data.local.DayStatus
import dev.thirty.app.data.local.ThirtyDatabase
import dev.thirty.app.domain.ChallengeLogic
import dev.thirty.app.util.DateUtils
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class ChallengeWithProgress(
    val challenge: ChallengeEntity,
    val days: List<DailyProgressEntity>
) {
    val todayDayNumber: Int
        get() = ChallengeLogic.dayNumberFor(
            DateUtils.fromEpochDay(challenge.startEpochDay),
            DateUtils.today(),
            challenge.lengthDays
        )
    val completedCount: Int get() = ChallengeLogic.completedCount(days.map { it.status })
    val progressCount: Int get() = ChallengeLogic.progressCount(days.map { it.status })
    val restCount: Int get() = ChallengeLogic.restCount(days.map { it.status })
    val restUsed: Boolean get() = ChallengeLogic.hasUsedRest(days.map { it.status })
    val currentStreak: Int get() = ChallengeLogic.currentStreak(days.map { it.status }, todayDayNumber)
    val bestStreak: Int get() = ChallengeLogic.bestStreak(days.map { it.status })
}

class ThirtyRepository(
    private val db: ThirtyDatabase,
    private val settings: SettingsStore,
    private val io: CoroutineDispatcher = Dispatchers.IO
) {
    val activeChallengeFlow: Flow<ChallengeEntity?> = db.challengeDao().activeChallengeFlow()
    val activeListFlow: Flow<List<ChallengeEntity>> = db.challengeDao().activeListFlow()
    val historyFlow: Flow<List<ChallengeEntity>> = db.challengeDao().historyFlow()
    val settingsFlow: Flow<AppSettings> = settings.flow

    fun progressFlow(challengeId: Long): Flow<List<DailyProgressEntity>> =
        db.progressDao().forChallengeFlow(challengeId)

    suspend fun currentSettings(): AppSettings = settings.current()

    /** Create a new plan (7 days to 1 year). Multiple plans can be active at once. */
    suspend fun createChallenge(
        title: String,
        category: String,
        targetValue: Double?,
        targetUnit: String?,
        reminderHour: Int,
        reminderMinute: Int,
        reminderEnabled: Boolean,
        lengthDays: Int = 30,
        startDate: LocalDate = DateUtils.today()
    ): Long = withContext(io) {
        val clean = title.trim()
        require(clean.isNotEmpty() && clean.length <= 120) { "Invalid title" }
        require(lengthDays in 1..366) { "Invalid plan length" }
        val end = ChallengeLogic.endDateFor(startDate, lengthDays)
        val id = db.challengeDao().insert(
            ChallengeEntity(
                title = clean,
                category = category.ifBlank { "Personal" },
                targetValue = targetValue,
                targetUnit = targetUnit?.trim()?.takeIf { it.isNotEmpty() },
                lengthDays = lengthDays,
                startEpochDay = startDate.toEpochDay(),
                endEpochDay = end.toEpochDay(),
                reminderHour = reminderHour,
                reminderMinute = reminderMinute,
                reminderEnabled = reminderEnabled,
                status = ChallengeStatus.ACTIVE
            )
        )
        val rows = (1..lengthDays).map { day ->
            DailyProgressEntity(
                challengeId = id,
                dayNumber = day,
                epochDay = ChallengeLogic.dateForDay(startDate, day).toEpochDay(),
                status = if (day == 1) DayStatus.PENDING else DayStatus.FUTURE
            )
        }
        db.progressDao().upsertAll(rows)
        // Remember the last used reminder time as the default for the next plan.
        settings.setReminderTime(reminderHour, reminderMinute)
        settings.setNotificationsEnabled(reminderEnabled)
        id
    }

    /**
     * Refresh day states for EVERY active plan:
     * past PENDING/FUTURE days become MISSED, today becomes PENDING,
     * and plans whose final day is decided move to history.
     */
    suspend fun refreshDayStates() = withContext(io) {
        val actives = db.challengeDao().activeListOnce()
        actives.forEach { active ->
            val days = db.progressDao().forChallengeOnce(active.id).sortedBy { it.dayNumber }
            if (days.isEmpty()) return@forEach
            val start = DateUtils.fromEpochDay(active.startEpochDay)
            val length = active.lengthDays.coerceAtLeast(1)
            val todayNum = ChallengeLogic.dayNumberFor(start, DateUtils.today(), length)
            ChallengeLogic.missedDaysToMark(days.map { it.status }, todayNum).forEach { day ->
                db.progressDao().dayOnce(active.id, day)?.let {
                    if (it.status == DayStatus.PENDING || it.status == DayStatus.FUTURE) {
                        db.progressDao().update(it.copy(status = DayStatus.MISSED))
                    }
                }
            }
            db.progressDao().dayOnce(active.id, todayNum)?.let {
                if (it.status == DayStatus.FUTURE) db.progressDao().update(it.copy(status = DayStatus.PENDING))
            }
            val last = db.progressDao().dayOnce(active.id, length)
            if (last?.status == DayStatus.COMPLETED || last?.status == DayStatus.MISSED) {
                db.challengeDao().update(active.copy(status = ChallengeStatus.COMPLETED))
            }
        }
    }

    /**
     * Mark today done. Pass [note] to overwrite today's note, or null to keep
     * whatever is already written (used by the widget / notification action).
     */
    suspend fun completeToday(challengeId: Long, note: String? = ""): Boolean = withContext(io) {
        val active = db.challengeDao().byId(challengeId)
            ?.takeIf { it.status == ChallengeStatus.ACTIVE } ?: return@withContext false
        refreshDayStates()
        val start = DateUtils.fromEpochDay(active.startEpochDay)
        val length = active.lengthDays.coerceAtLeast(1)
        val todayNum = ChallengeLogic.dayNumberFor(start, DateUtils.today(), length)
        val row = db.progressDao().dayOnce(active.id, todayNum) ?: return@withContext false
        if (row.status == DayStatus.COMPLETED) return@withContext true
        db.progressDao().update(
            row.copy(
                status = DayStatus.COMPLETED,
                completedAtMillis = System.currentTimeMillis(),
                note = note ?: row.note
            )
        )
        if (todayNum >= length) {
            db.challengeDao().update(active.copy(status = ChallengeStatus.COMPLETED))
        }
        true
    }

    suspend fun saveNote(challengeId: Long, dayNumber: Int, note: String): Boolean = withContext(io) {
        val row = db.progressDao().dayOnce(challengeId, dayNumber) ?: return@withContext false
        db.progressDao().update(row.copy(note = note.take(1000)))
        true
    }

    /**
     * Take the one rest day: marks today REST (only from PENDING, once per plan).
     * Rest keeps the streak alive and counts toward progress.
     */
    suspend fun restToday(challengeId: Long): Boolean = withContext(io) {
        val active = db.challengeDao().byId(challengeId)
            ?.takeIf { it.status == ChallengeStatus.ACTIVE } ?: return@withContext false
        val days = db.progressDao().forChallengeOnce(active.id)
        if (ChallengeLogic.hasUsedRest(days.map { it.status })) return@withContext false
        val start = DateUtils.fromEpochDay(active.startEpochDay)
        val length = active.lengthDays.coerceAtLeast(1)
        val todayNum = ChallengeLogic.dayNumberFor(start, DateUtils.today(), length)
        val row = db.progressDao().dayOnce(active.id, todayNum) ?: return@withContext false
        if (row.status != DayStatus.PENDING) return@withContext false
        db.progressDao().update(row.copy(status = DayStatus.REST))
        if (todayNum >= length) {
            db.challengeDao().update(active.copy(status = ChallengeStatus.COMPLETED))
        }
        true
    }

    suspend fun activeListWithProgress(): List<ChallengeWithProgress> = withContext(io) {
        db.challengeDao().activeListOnce().map { c ->
            ChallengeWithProgress(c, db.progressDao().forChallengeOnce(c.id).sortedBy { it.dayNumber })
        }
    }

    suspend fun hasAnyActive(): Boolean = withContext(io) {
        db.challengeDao().activeListOnce().isNotEmpty()
    }

    suspend fun activeWithProgress(): ChallengeWithProgress? = withContext(io) {
        activeListWithProgress().firstOrNull()
    }

    suspend fun challengeWithProgress(id: Long): ChallengeWithProgress? = withContext(io) {
        val c = db.challengeDao().byId(id) ?: return@withContext null
        ChallengeWithProgress(c, db.progressDao().forChallengeOnce(id).sortedBy { it.dayNumber })
    }

    /** Archive one plan into history (it stays visible in History). */
    suspend fun cancelChallenge(id: Long) = withContext(io) {
        db.challengeDao().byId(id)?.let {
            if (it.status == ChallengeStatus.ACTIVE) {
                db.challengeDao().update(it.copy(status = ChallengeStatus.CANCELLED))
            }
        }
    }

    /**
     * Clone a finished/closed journey into a brand-new run starting today.
     * Returns the new plan id (and remembers it as the selected plan).
     */
    suspend fun rerunChallenge(id: Long): Long? = withContext(io) {
        val src = db.challengeDao().byId(id) ?: return@withContext null
        if (src.status == ChallengeStatus.ACTIVE) return@withContext null
        val start = DateUtils.today()
        val newId = db.challengeDao().insert(
            src.copy(
                id = 0,
                startEpochDay = start.toEpochDay(),
                endEpochDay = ChallengeLogic.endDateFor(start, src.lengthDays).toEpochDay(),
                status = ChallengeStatus.ACTIVE,
                createdAtMillis = System.currentTimeMillis()
            )
        )
        val rows = (1..src.lengthDays).map { day ->
            DailyProgressEntity(
                challengeId = newId,
                dayNumber = day,
                epochDay = ChallengeLogic.dateForDay(start, day).toEpochDay(),
                status = if (day == 1) DayStatus.PENDING else DayStatus.FUTURE
            )
        }
        db.progressDao().upsertAll(rows)
        setSelectedPlanId(newId)
        newId
    }

    suspend fun clearAll() = withContext(io) {
        db.progressDao().clearAll()
        db.challengeDao().clearAll()
    }

    // -- settings passthrough --
    suspend fun setTheme(mode: dev.thirty.app.data.datastore.ThemeMode) = settings.setTheme(mode)
    suspend fun setNotificationsEnabled(v: Boolean) = settings.setNotificationsEnabled(v)
    suspend fun setNudgeEnabled(v: Boolean) = settings.setNudgeEnabled(v)
    suspend fun setNudgeTime(h: Int, m: Int) = settings.setNudgeTime(h, m)
    suspend fun setLockEnabled(v: Boolean) = settings.setLockEnabled(v)
    suspend fun setLockPassword(pw: String) = settings.setLockPassword(pw)
    suspend fun clearLock() = settings.clearLock()

    // -- remembered plan selection (Today, Journey and the widget share it) --
    private val selectedPlanCache = MutableStateFlow<Long?>(null)

    suspend fun selectedPlanId(): Long? {
        selectedPlanCache.value?.let { return it }
        val stored = runCatching { settings.selectedPlanId() }.getOrNull()
        if (stored != null) selectedPlanCache.value = stored
        return selectedPlanCache.value
    }

    suspend fun setSelectedPlanId(id: Long) {
        selectedPlanCache.value = id
        runCatching { settings.setSelectedPlanId(id) }
    }

    suspend fun setChallengeReminderEnabled(id: Long, enabled: Boolean) = withContext(io) {
        db.challengeDao().byId(id)?.let {
            db.challengeDao().update(it.copy(reminderEnabled = enabled))
        }
    }

    suspend fun setChallengeReminderTime(id: Long, h: Int, m: Int) = withContext(io) {
        require(h in 0..23 && m in 0..59)
        db.challengeDao().byId(id)?.let {
            db.challengeDao().update(it.copy(reminderHour = h, reminderMinute = m))
        }
        settings.setReminderTime(h, m)
    }

    suspend fun replaceAllData(
        challenges: List<ChallengeEntity>,
        days: List<DailyProgressEntity>,
        appSettings: AppSettings?
    ) = withContext(io) {
        db.progressDao().clearAll()
        db.challengeDao().clearAll()
        challenges.forEach { db.challengeDao().insert(it.copy(id = 0)) }
        days.forEach { d ->
            runCatching { db.progressDao().upsert(d) }
        }
        if (appSettings != null) settings.replaceAll(appSettings)
        refreshDayStates()
    }
}
