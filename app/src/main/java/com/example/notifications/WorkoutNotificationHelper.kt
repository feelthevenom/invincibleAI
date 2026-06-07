package com.example.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.data.AppDatabase
import com.example.data.AppNotification
import com.example.data.DietDateUtils
import com.example.data.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

object WorkoutNotificationHelper {
    const val CHANNEL_ID = "workout_alarms"
    private const val NOTIFICATION_ID = 2001
    private const val REQUEST_CODE = 9100
    private const val TAG = "WorkoutAlarm"

    fun createChannel(context: Context, soundUri: Uri? = null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val alarmSound = soundUri ?: defaultAlarmUri()
            val channel = android.app.NotificationChannel(
                CHANNEL_ID,
                "Workout Alarms",
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alarm-style reminders for scheduled workouts"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 200, 400)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setSound(
                    alarmSound,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun defaultAlarmUri(): Uri =
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

    fun resolveAlarmSoundUri(profile: UserProfile): Uri? {
        if (profile.workoutAlarmSoundUri.isNotBlank()) {
            return try {
                Uri.parse(profile.workoutAlarmSoundUri)
            } catch (_: Exception) {
                defaultAlarmUri()
            }
        }
        return defaultAlarmUri()
    }

    fun scheduleReminder(context: Context, profile: UserProfile) {
        createChannel(context, resolveAlarmSoundUri(profile))
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = reminderPendingIntent(context)
        alarmManager.cancel(pending)
        if (!profile.workoutReminderEnabled) return

        val trigger = nextTrigger(profile.workoutReminderTimeMinute)
        try {
            if (canScheduleExactAlarms(context)) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, trigger, pending)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule workout alarm", e)
        }
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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

    fun showWorkoutAlarm(context: Context, profile: UserProfile) {
        val appContext = context.applicationContext
        if (!NotificationUtils.canPostNotifications(appContext)) {
            Log.w(TAG, "Notifications not permitted — cannot show workout alarm")
            return
        }
        val soundUri = resolveAlarmSoundUri(profile)
        createChannel(appContext, soundUri)

        try {
            runBlocking(Dispatchers.IO) {
                AppDatabase.getDatabase(appContext).gymDao().insertNotification(
                    AppNotification(
                        title = "Workout Alarm",
                        body = "Time for your scheduled workout. Let's go!",
                        category = "workout"
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log workout alarm notification", e)
        }

        val openIntent = PendingIntent.getActivity(
            appContext, 1,
            Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Workout Alarm")
            .setContentText("Time for your scheduled workout!")
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setVibrate(longArrayOf(0, 400, 200, 400, 200, 400))
            .apply { soundUri?.let { setSound(it) } }
            .build()

        try {
            NotificationManagerCompat.from(appContext).notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post workout alarm notification", e)
        }
    }
}

class WorkoutReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pendingResult = goAsync()
        try {
            val appContext = context.applicationContext
            Thread {
                try {
                    val profile = runBlocking(Dispatchers.IO) {
                        AppDatabase.getDatabase(appContext).gymDao().getUserProfile().first()
                    }
                    if (profile != null && profile.workoutReminderEnabled) {
                        WorkoutNotificationHelper.showWorkoutAlarm(appContext, profile)
                        if (profile.workoutReminderRepeat) {
                            WorkoutNotificationHelper.scheduleReminder(appContext, profile)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("WorkoutReminder", "Receiver failed", e)
                } finally {
                    pendingResult.finish()
                }
            }.start()
        } catch (e: Exception) {
            Log.e("WorkoutReminder", "Receiver setup failed", e)
            pendingResult.finish()
        }
    }
}
