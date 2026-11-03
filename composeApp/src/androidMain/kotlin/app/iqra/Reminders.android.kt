package app.iqra

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import app.iqra.engine.Progress
import kotlinx.serialization.json.Json
import java.util.Calendar

/** Set by MainActivity: asks for permission to show notifications (Android 13+). */
var requestNotificationPermission: (() -> Unit)? = null

private const val CHANNEL = "daily-reminder"

actual val remindersSupported: Boolean = true

private fun reminderIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
    context, 0, Intent(context, ReminderReceiver::class.java),
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
)

actual fun scheduleReminder(hour: Int?) {
    schedule(appContext, hour)
    if (hour != null && Build.VERSION.SDK_INT >= 33 &&
        appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
        requestNotificationPermission?.invoke()
    }
}

private fun schedule(context: Context, hour: Int?) {
    val alarms = context.getSystemService(AlarmManager::class.java) ?: return
    val intent = reminderIntent(context)
    alarms.cancel(intent)
    if (hour == null) return
    val next = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
    }
    // Inexact: no special alarm permission needed, and the phone can batch it with other work.
    alarms.setInexactRepeating(AlarmManager.RTC_WAKEUP, next.timeInMillis, AlarmManager.INTERVAL_DAY, intent)
}

private fun savedProgress(context: Context): Progress? = runCatching {
    val text = java.io.File(context.filesDir, "progress.json").readText()
    Json { ignoreUnknownKeys = true }.decodeFromString(Progress.serializer(), text)
}.getOrNull()

/** Shows the daily reminder, unless the learner has already practised today. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val progress = savedProgress(context)
        if (progress?.reminderHour == null) return
        val today = java.time.LocalDate.now().toEpochDay()
        if (progress.lastActiveDay == today) return
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Daily reminder", NotificationManager.IMPORTANCE_DEFAULT))
        val streak = progress.currentStreak(today)
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(if (streak > 0) "Keep your $streak-day streak going" else "Time for a short lesson")
            .setContentText("A few minutes of Iqra today.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(1, notification) }
    }
}

/** Alarms are cleared when the phone restarts; set the reminder again. */
class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        savedProgress(context)?.reminderHour?.let { schedule(context, it) }
    }
}
