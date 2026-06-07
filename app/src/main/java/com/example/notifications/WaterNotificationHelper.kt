package com.example.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.data.AppNotification
import com.example.data.AppDatabase
import com.example.data.DietDateUtils
import com.example.data.UserProfile
import com.example.data.WaterGoalCalculator
import com.example.data.WaterReminderModes
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.Calendar

object WaterNotificationHelper {
    const val CHANNEL_ID = "hydration_reminders"
    const val NOTIFICATION_ID = 1001
    const val REMINDER_REQUEST_BASE = 2000

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Hydration Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Gentle notifications to drink water — not alarms"
                enableVibration(false)
                setSound(null, null)
            }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    fun scheduleReminders(context: Context, profile: UserProfile) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        cancelAll(alarmManager, context)
        if (!profile.waterReminderEnabled) return
        createChannel(context)

        val triggers = computeTriggerTimes(profile)
        if (triggers.isEmpty()) {
            // Fallback: one inexact reminder every 2 hours when notifications-only
            if (profile.notificationsEnabled) {
                scheduleInexactFallback(context, alarmManager)
            }
            return
        }

        val useExact = canScheduleExactAlarms(context)
        triggers.forEachIndexed { index, triggerAt ->
            val pending = reminderPendingIntent(context, index)
            if (useExact) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pending
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pending
                )
            }
        }
    }

    fun scheduleReminders(context: Context, enabled: Boolean) {
        if (!enabled) {
            cancelReminders(context)
            return
        }
        runBlocking {
            val profile = AppDatabase.getDatabase(context).gymDao().getUserProfile().first()
            if (profile != null) scheduleReminders(context, profile)
        }
    }

    fun cancelReminders(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        cancelAll(alarmManager, context)
    }

    private fun scheduleInexactFallback(context: Context, alarmManager: AlarmManager) {
        val pending = reminderPendingIntent(context, 0)
        val triggerAt = System.currentTimeMillis() + 2 * 60 * 60 * 1000L
        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            2 * 60 * 60 * 1000L,
            pending
        )
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else true
    }

    private fun cancelAll(alarmManager: AlarmManager, context: Context) {
        repeat(24) { index ->
            alarmManager.cancel(reminderPendingIntent(context, index))
        }
    }

    fun computeTriggerTimes(profile: UserProfile): List<Long> {
        if (!profile.waterReminderEnabled) return emptyList()
        val now = System.currentTimeMillis()
        val startMin = profile.waterReminderWindowStartMinute.coerceIn(0, 23 * 60 + 59)
        val endMin = profile.waterReminderWindowEndMinute.coerceIn(startMin + 1, 24 * 60 - 1)

        return when (profile.waterReminderMode) {
            WaterReminderModes.INTERVAL -> {
                val intervalMs = profile.waterReminderIntervalMinutes.coerceAtLeast(15) * 60_000L
                buildList {
                    var dayStart = DietDateUtils.startOfTodayMillis()
                    for (day in 0..1) {
                        var t = DietDateUtils.dayStartFromMinute(startMin, dayStart + day * DietDateUtils.DAY_MS)
                        val end = DietDateUtils.dayStartFromMinute(endMin, dayStart + day * DietDateUtils.DAY_MS)
                        while (t <= end) {
                            if (t > now) add(t)
                            t += intervalMs
                        }
                    }
                }.take(24)
            }
            WaterReminderModes.TIMES -> {
                val count = profile.waterReminderTimesPerDay.coerceIn(1, 12)
                val windowMs = (endMin - startMin) * 60_000L
                val step = if (count <= 1) 0L else windowMs / (count - 1)
                buildList {
                    val dayStart = DietDateUtils.startOfTodayMillis()
                    repeat(count) { i ->
                        val minute = startMin + ((step * i) / 60_000L).toInt()
                        val t = DietDateUtils.dayStartFromMinute(minute.coerceAtMost(endMin), dayStart)
                        if (t > now) add(t)
                        val tomorrow = DietDateUtils.dayStartFromMinute(minute.coerceAtMost(endMin), dayStart + DietDateUtils.DAY_MS)
                        if (tomorrow > now) add(tomorrow)
                    }
                }.distinct().sorted().take(24)
            }
            WaterReminderModes.DAILY -> {
                listOfNotNull(
                    nextDailyTrigger(profile.waterReminderDailyTimeMinute, now)
                )
            }
            WaterReminderModes.WEEKLY -> {
                listOfNotNull(
                    nextWeeklyTrigger(profile.waterReminderWeeklyDay, profile.waterReminderDailyTimeMinute, now)
                )
            }
            else -> emptyList()
        }
    }

    private fun nextDailyTrigger(minuteOfDay: Int, now: Long): Long? {
        val today = DietDateUtils.dayStartFromMinute(minuteOfDay, DietDateUtils.startOfTodayMillis())
        return if (today > now) today else DietDateUtils.dayStartFromMinute(
            minuteOfDay,
            DietDateUtils.startOfTodayMillis() + DietDateUtils.DAY_MS
        )
    }

    private fun nextWeeklyTrigger(weekDay: Int, minuteOfDay: Int, now: Long): Long? {
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        var daysAhead = weekDay - cal.get(Calendar.DAY_OF_WEEK)
        if (daysAhead < 0) daysAhead += 7
        val targetDay = DietDateUtils.startOfTodayMillis() + daysAhead * DietDateUtils.DAY_MS
        var trigger = DietDateUtils.dayStartFromMinute(minuteOfDay, targetDay)
        if (trigger <= now) {
            trigger = DietDateUtils.dayStartFromMinute(minuteOfDay, targetDay + 7 * DietDateUtils.DAY_MS)
        }
        return trigger
    }

    fun computeProgressPercent(context: Context): Int = runBlocking {
        val dao = AppDatabase.getDatabase(context).gymDao()
        val profile = dao.getUserProfile().first() ?: return@runBlocking 0
        if (!profile.waterReminderEnabled && !profile.notificationsEnabled) return@runBlocking 100
        val dayStart = DietDateUtils.startOfTodayMillis()
        val dayEnd = DietDateUtils.endOfDayMillis(dayStart)
        val logs = dao.getAllWaterLogs().first()
        val total = logs.filter { it.timestamp in dayStart until dayEnd }.sumOf { it.amountMl }
        val goal = WaterGoalCalculator.effectiveGoalMl(profile).coerceAtLeast(1)
        ((total.toFloat() / goal) * 100).toInt().coerceIn(0, 100)
    }

    fun showHydrationNotification(context: Context, progressPercent: Int) {
        if (!NotificationUtils.canPostNotifications(context)) return
        createChannel(context)
        val body = "Goal progress: $progressPercent%. Stay fueled and log your water."
        runBlocking {
            AppDatabase.getDatabase(context).gymDao().insertNotification(
                AppNotification(title = "Hydration Alert", body = body, category = "hydration")
            )
        }
        val openIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("Hydration Reminder")
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent)
            .setAutoCancel(true)
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
    }

    private fun reminderPendingIntent(context: Context, requestCode: Int): PendingIntent {
        val intent = Intent(context, WaterReminderReceiver::class.java)
        return PendingIntent.getBroadcast(
            context, REMINDER_REQUEST_BASE + requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

class WaterReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (!NotificationUtils.canPostNotifications(context)) {
            reschedule(context)
            return
        }
        runBlocking {
            val profile = AppDatabase.getDatabase(context).gymDao().getUserProfile().first()
            if (profile != null && profile.waterSnoozeUntilMs > System.currentTimeMillis()) {
                reschedule(context)
                return@runBlocking
            }
        }
        val progress = WaterNotificationHelper.computeProgressPercent(context)
        WaterNotificationHelper.showHydrationNotification(context, progress)
        reschedule(context)
    }

    private fun reschedule(context: Context) {
        runBlocking {
            val profile = AppDatabase.getDatabase(context).gymDao().getUserProfile().first()
            if (profile != null && profile.waterReminderEnabled) {
                WaterNotificationHelper.scheduleReminders(context, profile)
            }
        }
    }
}
