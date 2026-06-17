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

    val MIGRATION_22_23 = object : Migration(22, 23) {
        override fun migrate(db: SupportSQLiteDatabase) {
            addColumnIfMissing(db, "user_profile", "notificationsLastViewedAt", "INTEGER NOT NULL DEFAULT 0")
        }
    }

    val MIGRATION_23_24 = object : Migration(23, 24) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // placeholder — version bump for intermediate installs
        }
    }

    val MIGRATION_24_25 = object : Migration(24, 25) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // version alignment for existing installs
        }
    }

    val MIGRATION_25_26 = object : Migration(25, 26) {
        override fun migrate(db: SupportSQLiteDatabase) {
            addColumnIfMissing(db, "user_profile", "workoutAlarmSoundUri", "TEXT NOT NULL DEFAULT ''")
        }
    }

    val MIGRATION_26_27 = object : Migration(26, 27) {
        override fun migrate(db: SupportSQLiteDatabase) {
            addColumnIfMissing(db, "meals", "servingLabel", "TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "meals", "servingQuantity", "REAL NOT NULL DEFAULT 0")
        }
    }

    val MIGRATION_27_28 = object : Migration(27, 28) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS cached_food_products")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS cached_food_products (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    searchKey TEXT NOT NULL,
                    brand TEXT NOT NULL,
                    caloriesPer100g INTEGER NOT NULL,
                    proteinPer100g REAL NOT NULL,
                    carbsPer100g REAL NOT NULL,
                    fatPer100g REAL NOT NULL,
                    fiberPer100g REAL NOT NULL,
                    volumeBased INTEGER NOT NULL,
                    source TEXT NOT NULL,
                    externalId TEXT NOT NULL,
                    createdAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_cached_food_products_searchKey ON cached_food_products(searchKey)"
            )
        }
    }

    val MIGRATION_28_29 = object : Migration(28, 29) {
        override fun migrate(db: SupportSQLiteDatabase) {
            addColumnIfMissing(db, "cached_food_products", "imageUrl", "TEXT NOT NULL DEFAULT ''")
        }
    }

    val MIGRATION_29_30 = object : Migration(29, 30) {
        override fun migrate(db: SupportSQLiteDatabase) {
            addColumnIfMissing(db, "custom_foods", "imageUrl", "TEXT NOT NULL DEFAULT ''")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS food_image_cache (
                    normalizedName TEXT NOT NULL PRIMARY KEY,
                    imageUrl TEXT NOT NULL,
                    source TEXT NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    val MIGRATION_30_31 = object : Migration(30, 31) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Clear stale/incorrect food image cache (e.g. same OFF product matched for all foods).
            db.execSQL("DELETE FROM food_image_cache")
        }
    }

    val MIGRATION_31_32 = object : Migration(31, 32) {
        override fun migrate(db: SupportSQLiteDatabase) {
            addColumnIfMissing(db, "user_profile", "aiHybridInference", "INTEGER NOT NULL DEFAULT 1")
        }
    }

    val MIGRATION_32_33 = object : Migration(32, 33) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("UPDATE user_profile SET aiProvider = 'gemini' WHERE aiProvider = 'firebase'")
            db.execSQL("UPDATE user_profile SET aiTextProvider = 'gemini' WHERE aiTextProvider = 'firebase'")
            db.execSQL("UPDATE user_profile SET aiVisionProvider = 'gemini' WHERE aiVisionProvider = 'firebase'")
        }
    }

    val MIGRATION_33_34 = object : Migration(33, 34) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Fix cached_food_products table schema mismatch from previous failed migration
            db.execSQL("DROP TABLE IF EXISTS cached_food_products")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS cached_food_products (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    searchKey TEXT NOT NULL,
                    brand TEXT NOT NULL,
                    caloriesPer100g INTEGER NOT NULL,
                    proteinPer100g REAL NOT NULL,
                    carbsPer100g REAL NOT NULL,
                    fatPer100g REAL NOT NULL,
                    fiberPer100g REAL NOT NULL,
                    volumeBased INTEGER NOT NULL,
                    source TEXT NOT NULL,
                    externalId TEXT NOT NULL,
                    imageUrl TEXT NOT NULL,
                    createdAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_cached_food_products_searchKey ON cached_food_products(searchKey)"
            )
        }
    }

    val MIGRATION_34_35 = object : Migration(34, 35) {
        override fun migrate(db: SupportSQLiteDatabase) {
            addColumnIfMissing(db, "user_profile", "aiHybridInference", "INTEGER NOT NULL DEFAULT 1")
        }
    }

    val MIGRATION_35_36 = object : Migration(35, 36) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Ensure columns exist before we copy them into the rebuilt table.
            addColumnIfMissing(db, "user_profile", "aiHybridInference", "INTEGER NOT NULL DEFAULT 1")
            addColumnIfMissing(db, "user_profile", "themeMode", "TEXT NOT NULL DEFAULT 'system'")
            
            // 1. Rebuild user_profile
            db.execSQL("ALTER TABLE user_profile RENAME TO user_profile_old")
            db.execSQL("""
                CREATE TABLE user_profile (
                    id INTEGER NOT NULL PRIMARY KEY,
                    age INTEGER NOT NULL,
                    gender TEXT NOT NULL,
                    height INTEGER NOT NULL,
                    activityLevel TEXT NOT NULL,
                    goal TEXT NOT NULL,
                    currentWeight REAL NOT NULL,
                    targetWeight REAL NOT NULL,
                    dailyCalories INTEGER NOT NULL,
                    protein INTEGER NOT NULL,
                    carbs INTEGER NOT NULL,
                    fat INTEGER NOT NULL,
                    fiber INTEGER NOT NULL,
                    workoutDaysPerWeek INTEGER NOT NULL,
                    targetWeightChangePerWeek REAL NOT NULL,
                    weeksToGoal INTEGER NOT NULL,
                    maintenanceCalories INTEGER NOT NULL,
                    calorieAdjustmentDaily INTEGER NOT NULL,
                    calorieAdjustmentWeekly INTEGER NOT NULL,
                    cuisinePreferences TEXT NOT NULL,
                    onboardingComplete INTEGER NOT NULL,
                    workoutSetupComplete INTEGER NOT NULL,
                    fitnessLevel TEXT NOT NULL,
                    benchmarkSkipped INTEGER NOT NULL,
                    squat1RmKg REAL NOT NULL,
                    benchPress1RmKg REAL NOT NULL,
                    deadlift1RmKg REAL NOT NULL,
                    weekStartDay INTEGER NOT NULL,
                    equipmentSelection TEXT NOT NULL,
                    gymLocation TEXT NOT NULL,
                    aiProvider TEXT NOT NULL,
                    aiModelId TEXT NOT NULL,
                    offlineModelId TEXT NOT NULL,
                    aiSplitModels INTEGER NOT NULL,
                    aiTextProvider TEXT NOT NULL,
                    aiTextModelId TEXT NOT NULL,
                    aiVisionProvider TEXT NOT NULL,
                    aiVisionModelId TEXT NOT NULL,
                    dailyWaterGoalMl INTEGER NOT NULL,
                    waterReminderEnabled INTEGER NOT NULL,
                    waterSnoozeUntilMs INTEGER NOT NULL,
                    measurementUseMetric INTEGER NOT NULL,
                    notificationsEnabled INTEGER NOT NULL,
                    waterAlarmRemindersEnabled INTEGER NOT NULL,
                    waterReminderMode TEXT NOT NULL,
                    waterReminderIntervalMinutes INTEGER NOT NULL,
                    waterReminderTimesPerDay INTEGER NOT NULL,
                    waterReminderDailyTimeMinute INTEGER NOT NULL,
                    waterReminderWeeklyDay INTEGER NOT NULL,
                    waterReminderWindowStartMinute INTEGER NOT NULL,
                    waterReminderWindowEndMinute INTEGER NOT NULL,
                    workoutReminderEnabled INTEGER NOT NULL,
                    workoutReminderTimeMinute INTEGER NOT NULL,
                    workoutReminderRepeat INTEGER NOT NULL,
                    workoutAlarmSoundUri TEXT NOT NULL,
                    themeMode TEXT NOT NULL,
                    notificationsLastViewedAt INTEGER NOT NULL,
                    aiHybridInference INTEGER NOT NULL
                )
            """.trimIndent())
            db.execSQL("""
                INSERT INTO user_profile (
                    id, age, gender, height, activityLevel, goal, currentWeight, targetWeight,
                    dailyCalories, protein, carbs, fat, fiber, workoutDaysPerWeek,
                    targetWeightChangePerWeek, weeksToGoal, maintenanceCalories,
                    calorieAdjustmentDaily, calorieAdjustmentWeekly, cuisinePreferences,
                    onboardingComplete, workoutSetupComplete, fitnessLevel, benchmarkSkipped,
                    squat1RmKg, benchPress1RmKg, deadlift1RmKg, weekStartDay,
                    equipmentSelection, gymLocation, aiProvider, aiModelId, offlineModelId,
                    aiSplitModels, aiTextProvider, aiTextModelId, aiVisionProvider, aiVisionModelId,
                    dailyWaterGoalMl, waterReminderEnabled, waterSnoozeUntilMs,
                    measurementUseMetric, notificationsEnabled, waterAlarmRemindersEnabled,
                    waterReminderMode, waterReminderIntervalMinutes, waterReminderTimesPerDay,
                    waterReminderDailyTimeMinute, waterReminderWeeklyDay,
                    waterReminderWindowStartMinute, waterReminderWindowEndMinute,
                    workoutReminderEnabled, workoutReminderTimeMinute, workoutReminderRepeat,
                    workoutAlarmSoundUri, themeMode, notificationsLastViewedAt, aiHybridInference
                )
                SELECT 
                    id, age, gender, height, activityLevel, goal, currentWeight, targetWeight,
                    dailyCalories, protein, carbs, fat, fiber, workoutDaysPerWeek,
                    targetWeightChangePerWeek, weeksToGoal, maintenanceCalories,
                    calorieAdjustmentDaily, calorieAdjustmentWeekly, cuisinePreferences,
                    onboardingComplete, workoutSetupComplete, fitnessLevel, benchmarkSkipped,
                    squat1RmKg, benchPress1RmKg, deadlift1RmKg, weekStartDay,
                    equipmentSelection, gymLocation, aiProvider, aiModelId, offlineModelId,
                    aiSplitModels, aiTextProvider, aiTextModelId, aiVisionProvider, aiVisionModelId,
                    dailyWaterGoalMl, waterReminderEnabled, waterSnoozeUntilMs,
                    measurementUseMetric, notificationsEnabled, waterAlarmRemindersEnabled,
                    waterReminderMode, waterReminderIntervalMinutes, waterReminderTimesPerDay,
                    waterReminderDailyTimeMinute, waterReminderWeeklyDay,
                    waterReminderWindowStartMinute, waterReminderWindowEndMinute,
                    workoutReminderEnabled, workoutReminderTimeMinute, workoutReminderRepeat,
                    workoutAlarmSoundUri, themeMode, notificationsLastViewedAt, aiHybridInference
                FROM user_profile_old
            """.trimIndent())
            db.execSQL("DROP TABLE user_profile_old")

            // 2. Rebuild meals to fix schema mismatches (defaults)
            db.execSQL("ALTER TABLE meals RENAME TO meals_old")
            db.execSQL("""
                CREATE TABLE meals (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    mealType TEXT NOT NULL,
                    foodName TEXT NOT NULL,
                    weightGrams INTEGER NOT NULL,
                    servingLabel TEXT NOT NULL,
                    servingQuantity REAL NOT NULL,
                    calories INTEGER NOT NULL,
                    protein INTEGER NOT NULL,
                    carbs INTEGER NOT NULL,
                    fat INTEGER NOT NULL,
                    fiber INTEGER NOT NULL,
                    timestamp INTEGER NOT NULL
                )
            """.trimIndent())
            db.execSQL("""
                INSERT INTO meals (id, mealType, foodName, weightGrams, servingLabel, servingQuantity, calories, protein, carbs, fat, fiber, timestamp)
                SELECT id, mealType, foodName, weightGrams, servingLabel, servingQuantity, calories, protein, carbs, fat, fiber, timestamp FROM meals_old
            """.trimIndent())
            db.execSQL("DROP TABLE meals_old")
        }
    }

    /** Clears stale ExerciseDB / wrong AI tutorial cache; guides now come from bundled catalog. */
    val MIGRATION_36_37 = object : Migration(36, 37) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DELETE FROM cached_exercise_guides")
        }
    }

    val ALL = arrayOf(
        MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19,
        MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23,
        MIGRATION_23_24, MIGRATION_24_25, MIGRATION_25_26, MIGRATION_26_27,
        MIGRATION_27_28, MIGRATION_28_29, MIGRATION_29_30, MIGRATION_30_31,
        MIGRATION_31_32, MIGRATION_32_33, MIGRATION_33_34, MIGRATION_34_35,
        MIGRATION_35_36, MIGRATION_36_37
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
