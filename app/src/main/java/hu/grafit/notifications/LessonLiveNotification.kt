package hu.grafit.notifications

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import hu.grafit.R
import hu.grafit.data.*
import java.time.Instant

/** Android owns the ticking chronometer: no foreground service or per-second background sync. */
object LessonLiveNotification {
    private const val ID = 1202
    private const val CHANNEL = "lesson_countdown"
    private fun alarmIntent(context: Context) = PendingIntent.getBroadcast(context, ID,
        Intent(context, LessonAlarmReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
    fun requestUpdate(context: Context) { executor.execute { update(context.applicationContext) } }
    fun update(context: Context) { runCatching { render(context) } }
    private fun render(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val alarms = context.getSystemService(AlarmManager::class.java)
        val pending = alarmIntent(context)
        val options = SchoolNotifications.options(context)
        val api = SchoolServices.api(context)
        // Keep account changes atomic with local posting, without waiting on network work.
        synchronized(SchoolServices.accountLock) {
            if (!options.enabled || !options.liveLesson || !SchoolNotifications.canNotify(context) || !api.signedIn) {
                manager.cancel(ID)
                alarms.cancel(pending)
                return
            }
            val lessons = api.cached()?.lessons.orEmpty()
            val now = Instant.now()
            val current = ongoingLesson(lessons, now)
            if (current == null) manager.cancel(ID) else {
                manager.createNotificationChannel(NotificationChannel(CHANNEL, "Tanóra visszaszámlálása", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Csendes, élő visszaszámlálás az aktuális tanóra végéig"
                    setSound(null, null)
                    lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                })
                val end = current.end.atZone(schoolZone).toInstant().toEpochMilli()
                val start = current.start.atZone(schoolZone).toInstant().toEpochMilli()
                val values = mapOf("tantargy" to current.subject, "tanar" to current.teacher, "terem" to current.room,
                    "tema" to current.topic, "veg" to current.end.toLocalTime().toString())
                val title = renderNotification(options.liveTitle, values).ifBlank { "Tanóra" }
                val body = renderNotification(options.liveMessage, values)
                val public = Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_grafit_notification)
                    .setContentTitle("Tanóra folyamatban").setWhen(end).setUsesChronometer(true).setChronometerCountDown(true).build()
                val builder = Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_grafit_notification)
                    .setContentTitle(title).setContentText(body).setStyle(Notification.BigTextStyle().bigText(body))
                    .setWhen(end).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
                    .setOngoing(true).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_EVENT)
                    .setTimeoutAfter((end - now.toEpochMilli()).coerceAtLeast(1)).setVisibility(Notification.VISIBILITY_PRIVATE)
                    .setPublicVersion(public).setContentIntent(SchoolNotifications.contentIntent(context))
                    .setColor(when (options.liveColor) { 1 -> Color.rgb(81, 101, 143); 2 -> Color.rgb(80, 106, 83); else -> Color.GRAY })
                if (options.liveProgress) {
                    val seconds = ((end - start) / 1000).coerceIn(1, Int.MAX_VALUE.toLong()).toInt()
                    val elapsed = ((now.toEpochMilli() - start) / 1000).coerceIn(0, seconds.toLong()).toInt()
                    builder.setProgress(seconds, elapsed, false)
                }
                // Android 16+ Live Updates are optional; platform policy and user settings own promotion.
                // Reflection keeps the same APK compatible with Android 9–15 using a normal ongoing timer.
                if (Build.VERSION.SDK_INT >= 36 && options.livePromoted) runCatching {
                    Notification.Builder::class.java.getMethod("setRequestPromotedOngoing", Boolean::class.javaPrimitiveType)
                        .invoke(builder, true)
                }
                manager.notify(ID, builder.build())
            }
            val next = nextLessonTransition(lessons, now)
            if (next == null) alarms.cancel(pending) else {
                // Inexact alarms avoid requesting the restricted exact-alarm permission.
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toEpochMilli(), pending)
            }
        }
    }
}

class LessonAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Thread {
            try { LessonLiveNotification.update(context.applicationContext) }
            finally { pending.finish() }
        }.start()
    }
}

class NotificationRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Thread {
            try {
                hu.grafit.integrations.StudyReminders.reschedule(context.applicationContext)
                hu.grafit.widgets.GrafitWidget.updateAll(context.applicationContext)
                SchoolNotifications.schedule(context.applicationContext)
                LessonLiveNotification.update(context.applicationContext)
            } finally { pending.finish() }
        }.start()
    }
}
