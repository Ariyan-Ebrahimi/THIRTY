package dev.thirty.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Converters {
    @TypeConverter
    fun dayStatusToString(v: DayStatus): String = v.name

    @TypeConverter
    fun stringToDayStatus(v: String): DayStatus = try {
        DayStatus.valueOf(v)
    } catch (_: Exception) {
        DayStatus.FUTURE
    }
}

@Database(
    entities = [ChallengeEntity::class, DailyProgressEntity::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class ThirtyDatabase : RoomDatabase() {
    abstract fun challengeDao(): ChallengeDao
    abstract fun progressDao(): ProgressDao

    companion object {
        @Volatile
        private var INSTANCE: ThirtyDatabase? = null

        /** v1 → v2: flexible plan lengths. Old plans keep 30 days. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE challenges ADD COLUMN lengthDays INTEGER NOT NULL DEFAULT 30"
                )
            }
        }

        fun get(context: Context): ThirtyDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ThirtyDatabase::class.java,
                    "thirty.db"
                ).addMigrations(MIGRATION_1_2)
                    // Never silently erase user progress when a migration is missing.
                    // Add an explicit Migration before increasing the database version.
                    .build().also { INSTANCE = it }
            }
    }
}
