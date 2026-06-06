package com.example.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {

    val MIGRATION_15_16 = object : Migration(15, 16) {
        override fun migrate(db: SupportSQLiteDatabase) {
            addColumnIfMissing(db, "user_profile", "notificationsEnabled", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "user_profile", "waterAlarmRemindersEnabled", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "user_profile", "waterReminderMode", "TEXT NOT NULL DEFAULT 'times'")
            addColumnIfMissing(db, "user_profile", "waterReminderIntervalMinutes", "INTEGER NOT NULL DEFAULT 60")
            addColumnIfMissing(db, "user_profile", "waterReminderTimesPerDay", "INTEGER NOT NULL DEFAULT 3")
            addColumnIfMissing(db, "user_profile", "waterReminderDailyTimeMinute", "INTEGER NOT NULL DEFAULT 1290")
            addColumnIfMissing(db, "user_profile", "waterReminderWeeklyDay", "INTEGER NOT NULL DEFAULT 1")
            addColumnIfMissing(db, "user_profile", "waterReminderWindowStartMinute", "INTEGER NOT NULL DEFAULT 572")
            addColumnIfMissing(db, "user_profile", "waterReminderWindowEndMinute", "INTEGER NOT NULL DEFAULT 1320")
        }
    }

    val MIGRATION_16_17 = object : Migration(16, 17) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS daily_goal_snapshots (
                    dayStart INTEGER NOT NULL PRIMARY KEY,
                    dailyCalories INTEGER NOT NULL,
                    protein INTEGER NOT NULL,
                    carbs INTEGER NOT NULL,
                    fat INTEGER NOT NULL,
                    fiber INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS app_notifications (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    title TEXT NOT NULL,
                    body TEXT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    category TEXT NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    val MIGRATION_17_18 = object : Migration(17, 18) {
        override fun migrate(db: SupportSQLiteDatabase) {
            addColumnIfMissing(db, "user_profile", "workoutReminderEnabled", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "user_profile", "workoutReminderTimeMinute", "INTEGER NOT NULL DEFAULT 390")
            addColumnIfMissing(db, "user_profile", "workoutReminderRepeat", "INTEGER NOT NULL DEFAULT 1")
        }
    }

    val MIGRATION_18_19 = object : Migration(18, 19) {
        override fun migrate(db: SupportSQLiteDatabase) {
            addColumnIfMissing(db, "user_profile", "aiSplitModels", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "user_profile", "aiTextProvider", "TEXT NOT NULL DEFAULT 'gemini'")
            addColumnIfMissing(db, "user_profile", "aiTextModelId", "TEXT NOT NULL DEFAULT 'gemini-2.5-flash'")
            addColumnIfMissing(db, "user_profile", "aiVisionProvider", "TEXT NOT NULL DEFAULT 'gemini'")
            addColumnIfMissing(db, "user_profile", "aiVisionModelId", "TEXT NOT NULL DEFAULT 'gemini-2.5-flash'")
        }
    }

    val MIGRATION_19_20 = object : Migration(19, 20) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS cached_exercise_guides (
                    lookupKey TEXT NOT NULL PRIMARY KEY,
                    exerciseId TEXT NOT NULL,
                    apiName TEXT NOT NULL,
                    gifUrl TEXT NOT NULL,
                    localGifPath TEXT,
                    instructionsJson TEXT NOT NULL,
                    targetMusclesJson TEXT NOT NULL,
                    equipmentsJson TEXT NOT NULL,
                    bodyPartsJson TEXT NOT NULL,
                    cachedAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS coach_chat_history (
                    id TEXT NOT NULL PRIMARY KEY,
                    startedAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    preview TEXT NOT NULL,
                    messagesJson TEXT NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    val MIGRATION_20_21 = object : Migration(20, 21) {
        override fun migrate(db: SupportSQLiteDatabase) {
            addColumnIfMissing(db, "cached_exercise_guides", "source", "TEXT NOT NULL DEFAULT 'api'")
        }
    }

    val MIGRATION_21_22 = object : Migration(21, 22) {
        override fun migrate(db: SupportSQLiteDatabase) {
            addColumnIfMissing(db, "exercise_sets", "durationSeconds", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "exercise_sets", "caloriesBurned", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "custom_exercises", "isCardio", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "routine_exercises", "isCardio", "INTEGER NOT NULL DEFAULT 0")
        }
    }

    val ALL = arrayOf(
        MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19,
        MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22
    )

    private fun addColumnIfMissing(db: SupportSQLiteDatabase, table: String, column: String, definition: String) {
        db.query("PRAGMA table_info(`$table`)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                if (nameIndex >= 0 && cursor.getString(nameIndex) == column) return
            }
        }
        db.execSQL("ALTER TABLE `$table` ADD COLUMN `$column` $definition")
    }
}
