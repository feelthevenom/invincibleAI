package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED -> rescheduleAll(context)
        }
    }

    private fun rescheduleAll(context: Context) {
        runBlocking {
            val profile = AppDatabase.getDatabase(context).gymDao().getUserProfile().first() ?: return@runBlocking
            if (profile.waterReminderEnabled) {
                WaterNotificationHelper.scheduleReminders(context, profile)
            }
            if (profile.workoutReminderEnabled) {
                WorkoutNotificationHelper.scheduleReminder(context, profile)
            }
        }
    }
}
