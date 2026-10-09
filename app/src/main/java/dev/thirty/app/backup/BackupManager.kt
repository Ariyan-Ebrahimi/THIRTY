package dev.thirty.app.backup

import android.content.Context
import android.net.Uri
import dev.thirty.app.data.datastore.AppSettings
import dev.thirty.app.data.datastore.ThemeMode
import dev.thirty.app.data.local.ChallengeEntity
import dev.thirty.app.data.local.DailyProgressEntity
import dev.thirty.app.data.local.DayStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------------------
// Serializable DTOs
// ---------------------------------------------------------------------------

@Serializable
data class SettingsDto(
    val theme: String = "SYSTEM",
    val notificationsEnabled: Boolean = true,
    val reminderHour: Int = 20,
    val reminderMinute: Int = 0,
    val nudgeEnabled: Boolean = true,
    val nudgeHour: Int = 21,
    val nudgeMinute: Int = 30,
    val onboardingSeen: Boolean = true
)

@Serializable
data class ChallengeDto(
    val title: String,
    val category: String = "Personal",
    val targetValue: Double? = null,
    val targetUnit: String? = null,
    val lengthDays: Int = 30,
    val startEpochDay: Long,
    val endEpochDay: Long,
    val reminderHour: Int = 20,
    val reminderMinute: Int = 0,
    val reminderEnabled: Boolean = true,
    val createdAtMillis: Long = 0,
    val status: String = "active"
)

@Serializable
data class DayDto(
    val challengeIndex: Int,
    val dayNumber: Int,
    val epochDay: Long,
    val status: String = "PENDING",
    val completedAtMillis: Long? = null,
    val note: String = ""
)

@Serializable
data class BackupDto(
    val version: Int = 1,
    val exportedAt: String = "",
    val settings: SettingsDto = SettingsDto(),
    val challenges: List<ChallengeDto> = emptyList(),
    val dailyProgress: List<DayDto> = emptyList()
)

sealed interface BackupResult {
    data class Ok(val challenges: Int, val days: Int) : BackupResult
    data class Err(val message: String) : BackupResult
}

object BackupManager {
    const val CURRENT_VERSION = 1
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    fun buildExport(
        challenges: List<ChallengeEntity>,
        daysByChallenge: Map<Long, List<DailyProgressEntity>>,
        settings: AppSettings
    ): String {
        val ordered = challenges.sortedBy { it.createdAtMillis }
        val indexById = ordered.mapIndexed { i, c -> c.id to i }.toMap()
        val dayDtos = ordered.flatMap { c ->
            (daysByChallenge[c.id] ?: emptyList()).sortedBy { it.dayNumber }.map { d ->
                DayDto(
                    challengeIndex = indexById[c.id] ?: 0,
                    dayNumber = d.dayNumber,
                    epochDay = d.epochDay,
                    status = d.status.name,
                    completedAtMillis = d.completedAtMillis,
                    note = d.note
                )
            }
        }
        val dto = BackupDto(
            version = CURRENT_VERSION,
            exportedAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date()),
            settings = SettingsDto(
                theme = settings.themeMode.name,
                notificationsEnabled = settings.notificationsEnabled,
                reminderHour = settings.reminderHour,
                reminderMinute = settings.reminderMinute,
                nudgeEnabled = settings.nudgeEnabled,
                nudgeHour = settings.nudgeHour,
                nudgeMinute = settings.nudgeMinute,
                onboardingSeen = settings.onboardingSeen
            ),
            challenges = ordered.map {
                ChallengeDto(
                    title = it.title, category = it.category,
                    targetValue = it.targetValue, targetUnit = it.targetUnit,
                    lengthDays = it.lengthDays,
                    startEpochDay = it.startEpochDay, endEpochDay = it.endEpochDay,
                    reminderHour = it.reminderHour, reminderMinute = it.reminderMinute,
                    reminderEnabled = it.reminderEnabled,
                    createdAtMillis = it.createdAtMillis, status = it.status
                )
            },
            dailyProgress = dayDtos
        )
        return json.encodeToString(dto)
    }

    fun parseAndValidate(raw: String): BackupResult {
        val dto = try {
            json.decodeFromString<BackupDto>(raw)
        } catch (e: Exception) {
            return BackupResult.Err("Invalid backup file: not valid JSON.")
        }
        if (dto.version != CURRENT_VERSION) {
            return BackupResult.Err("Unsupported backup version (${dto.version}). Expected v$CURRENT_VERSION.")
        }
        if (dto.challenges.size > 500) return BackupResult.Err("Backup has too many challenges.")
        if (dto.dailyProgress.size > 500 * 366) return BackupResult.Err("Backup has too many day records.")
        val lengths = dto.challenges.map { it.lengthDays }
        for ((ci, c) in dto.challenges.withIndex()) {
            if (c.title.isBlank()) return BackupResult.Err("Backup corrupt: a challenge has no title.")
            if (c.title.length > 200) return BackupResult.Err("Backup corrupt: title too long.")
            if (c.lengthDays !in 1..366) return BackupResult.Err("Backup corrupt: bad plan length.")
            if (c.endEpochDay < c.startEpochDay) return BackupResult.Err("Backup corrupt: bad dates.")
            val dateSpan = try {
                Math.subtractExact(c.endEpochDay, c.startEpochDay)
            } catch (_: ArithmeticException) {
                return BackupResult.Err("Backup corrupt: invalid date range.")
            }
            // A plan of N days spans N - 1 days between its first and last epoch day.
            if (dateSpan >= c.lengthDays.toLong()) {
                return BackupResult.Err("Backup corrupt: plan longer than its length.")
            }
        }
        val challengeCount = dto.challenges.size
        if (challengeCount == 0 && dto.dailyProgress.isNotEmpty()) {
            return BackupResult.Err("Backup corrupt: orphan day records.")
        }
        for (d in dto.dailyProgress) {
            if (d.challengeIndex !in lengths.indices) {
                return BackupResult.Err("Backup corrupt: bad challenge reference.")
            }
            if (d.dayNumber !in 1..lengths[d.challengeIndex]) {
                return BackupResult.Err("Backup corrupt: bad day number.")
            }
        }
        return BackupResult.Ok(dto.challenges.size, dto.dailyProgress.size)
    }

    fun toEntities(dto: BackupDto): Triple<List<ChallengeEntity>, List<Pair<Int, DailyProgressEntity>>, AppSettings> {
        val challenges = dto.challenges.map { c ->
            ChallengeEntity(
                title = c.title.trim().take(120),
                category = c.category.ifBlank { "Personal" }.take(40),
                targetValue = c.targetValue?.takeIf { it > 0 },
                targetUnit = c.targetUnit?.take(24),
                lengthDays = c.lengthDays.coerceIn(1, 366),
                startEpochDay = c.startEpochDay,
                endEpochDay = c.endEpochDay,
                reminderHour = c.reminderHour.coerceIn(0, 23),
                reminderMinute = c.reminderMinute.coerceIn(0, 59),
                reminderEnabled = c.reminderEnabled,
                createdAtMillis = c.createdAtMillis,
                status = when (c.status) {
                    "active", "completed", "cancelled" -> c.status
                    else -> "completed"
                }
            )
        }
        val lengths = challenges.map { it.lengthDays }
        val days = dto.dailyProgress.map { d ->
            d.challengeIndex to DailyProgressEntity(
                challengeId = -1, // remapped on import
                dayNumber = d.dayNumber.coerceIn(1, lengths.getOrElse(d.challengeIndex) { 30 }),
                epochDay = d.epochDay,
                status = runCatching { DayStatus.valueOf(d.status) }.getOrDefault(DayStatus.MISSED),
                completedAtMillis = d.completedAtMillis,
                note = d.note.take(1000)
            )
        }
        val s = AppSettings(
            themeMode = runCatching { ThemeMode.valueOf(dto.settings.theme) }.getOrDefault(ThemeMode.SYSTEM),
            notificationsEnabled = dto.settings.notificationsEnabled,
            reminderHour = dto.settings.reminderHour.coerceIn(0, 23),
            reminderMinute = dto.settings.reminderMinute.coerceIn(0, 59),
            nudgeEnabled = dto.settings.nudgeEnabled,
            nudgeHour = dto.settings.nudgeHour.coerceIn(0, 23),
            nudgeMinute = dto.settings.nudgeMinute.coerceIn(0, 59),
            onboardingSeen = true
        )
        return Triple(challenges, days, s)
    }

    suspend fun exportToUri(context: Context, uri: Uri, content: String): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                out.write(content.toByteArray(Charsets.UTF_8))
                out.flush()
            } ?: return@withContext false
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun readFromUri(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { ins ->
                ins.readBytes().toString(Charsets.UTF_8)
            }
        } catch (_: Exception) {
            null
        }
    }
}
