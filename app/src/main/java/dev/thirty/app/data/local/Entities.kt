package dev.thirty.app.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

// ---------------------------------------------------------------------------
// Entities
// ---------------------------------------------------------------------------

@Entity(tableName = "challenges")
data class ChallengeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val category: String,
    val targetValue: Double? = null,
    val targetUnit: String? = null,
    /** plan length in days: 7, 14, 21, 30, 60, 90 or 365 */
    val lengthDays: Int = 30,
    /** epoch-day (device-local date) */
    val startEpochDay: Long,
    val endEpochDay: Long,
    val reminderHour: Int = 20,
    val reminderMinute: Int = 0,
    val reminderEnabled: Boolean = true,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val status: String = ChallengeStatus.ACTIVE
)

object ChallengeStatus {
    const val ACTIVE = "active"
    const val COMPLETED = "completed"
    const val CANCELLED = "cancelled"
}

enum class DayStatus { FUTURE, PENDING, COMPLETED, MISSED, REST }

@Entity(
    tableName = "daily_progress",
    primaryKeys = ["challengeId", "dayNumber"]
)
data class DailyProgressEntity(
    val challengeId: Long,
    val dayNumber: Int, // 1..30
    /** epoch-day (device-local date) */
    val epochDay: Long,
    val status: DayStatus = DayStatus.FUTURE,
    val completedAtMillis: Long? = null,
    val note: String = ""
)

// ---------------------------------------------------------------------------
// DAOs
// ---------------------------------------------------------------------------

@Dao
interface ChallengeDao {
    @Query("SELECT * FROM challenges WHERE status = 'active' ORDER BY id DESC LIMIT 1")
    fun activeChallengeFlow(): Flow<ChallengeEntity?>

    @Query("SELECT * FROM challenges WHERE status = 'active' ORDER BY id DESC LIMIT 1")
    suspend fun activeChallengeOnce(): ChallengeEntity?

    @Query("SELECT * FROM challenges WHERE status = 'active' ORDER BY createdAtMillis ASC")
    fun activeListFlow(): Flow<List<ChallengeEntity>>

    @Query("SELECT * FROM challenges WHERE status = 'active' ORDER BY createdAtMillis ASC")
    suspend fun activeListOnce(): List<ChallengeEntity>

    @Query("SELECT * FROM challenges WHERE id = :id")
    suspend fun byId(id: Long): ChallengeEntity?

    @Query("SELECT * FROM challenges WHERE status != 'active' ORDER BY createdAtMillis DESC")
    fun historyFlow(): Flow<List<ChallengeEntity>>

    @Query("SELECT * FROM challenges WHERE status != 'active' ORDER BY createdAtMillis DESC")
    suspend fun historyOnce(): List<ChallengeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(challenge: ChallengeEntity): Long

    @Update
    suspend fun update(challenge: ChallengeEntity)

    @Query("DELETE FROM challenges")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM challenges WHERE status = 'active'")
    suspend fun activeCount(): Int
}

@Dao
interface ProgressDao {
    @Query("SELECT * FROM daily_progress WHERE challengeId = :challengeId ORDER BY dayNumber ASC")
    fun forChallengeFlow(challengeId: Long): Flow<List<DailyProgressEntity>>

    @Query("SELECT * FROM daily_progress WHERE challengeId = :challengeId ORDER BY dayNumber ASC")
    suspend fun forChallengeOnce(challengeId: Long): List<DailyProgressEntity>

    @Query("SELECT * FROM daily_progress WHERE challengeId = :challengeId AND dayNumber = :day")
    suspend fun dayOnce(challengeId: Long, day: Int): DailyProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<DailyProgressEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: DailyProgressEntity)

    @Update
    suspend fun update(row: DailyProgressEntity)

    @Query("DELETE FROM daily_progress")
    suspend fun clearAll()

    @Query("DELETE FROM daily_progress WHERE challengeId = :challengeId")
    suspend fun clearForChallenge(challengeId: Long)
}
