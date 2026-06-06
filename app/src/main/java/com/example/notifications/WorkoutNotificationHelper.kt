package com.example.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.MainActivity
import com.example.data.AppDatabase
import com.example.data.AppNotification
import com.example.data.DietDateUtils
import com.example.data.UserProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

object WorkoutNotificationHelper {
    const val CHANNEL_ID = "workout_reminders"
    private const val NOTIFICATION_ID = 2001
    private const val REQUEST_CODE = 9100

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                CHANNEL_ID,
                "Workout Reminders",
                android.app.NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun scheduleReminder(context: Context, profile: UserProfile) {
        createChannel(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = reminderPendingIntent(context)
        alarmManager.cancel(pending)
        if (!profile.workoutReminderEnabled) return

        val trigger = nextTrigger(profile.workoutReminderTimeMinute)
        if (canScheduleExactAlarms(context)) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, trigger, pending)
        }
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else true
    }

    fun cancelReminder(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(reminderPendingIntent(context))
    }

    private fun nextTrigger(minuteOfDay: Int): Long {
        val today = DietDateUtils.dayStartFromMinute(minuteOfDay, DietDateUtils.startOfTodayMillis())
        val now = System.currentTimeMillis()
        return if (today > now) today else DietDateUtils.dayStartFromMinute(
            minuteOfDay,
            DietDateUtils.startOfTodayMillis() + DietDateUtils.DAY_MS
        )
    }

    private fun reminderPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, WorkoutReminderReceiver::class.java)
        return PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun showWorkoutReminderNotification(context: Context) {
        if (!NotificationUtils.canPostNotifications(context)) return
        createChannel(context)
        runBlocking {
            AppDatabase.getDatabase(context).gymDao().insertNotification(
                AppNotification(
                    title = "Workout Reminder",
                    body = "Time for your scheduled workout. Let's go!",
                    category = "workout"
                )
            )
        }
        val openIntent = PendingIntent.getActivity(
            context, 1,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        androidx.core.app.NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("Workout Reminder")
            .setContentText("Time for your scheduled workout!")
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .build()
            .let { androidx.core.app.NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, it) }
    }
}

class WorkoutReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        WorkoutNotificationHelper.showWorkoutReminderNotification(context)
        runBlocking {
            val profile = AppDatabase.getDatabase(context).gymDao().getUserProfile().first()
            if (profile != null && profile.workoutReminderEnabled && profile.workoutReminderRepeat) {
                WorkoutNotificationHelper.scheduleReminder(context, profile)
            }
        }
    }
}
