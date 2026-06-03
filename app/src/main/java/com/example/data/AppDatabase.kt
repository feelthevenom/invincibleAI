package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [UserProfile::class, ExerciseSet::class, MealEntry::class, CustomFoodItem::class, ProgressPhoto::class],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun gymDao(): GymDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "gym_database"
                ).fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        fun closeDatabase() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }

        fun checkpoint(context: Context) {
            val db = getDatabase(context).openHelper.writableDatabase
            db.query("PRAGMA wal_checkpoint(FULL)").use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getInt(0)
                }
            }
        }
    }
}
