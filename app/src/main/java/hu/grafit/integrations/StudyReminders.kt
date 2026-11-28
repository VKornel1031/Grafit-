package hu.grafit.integrations

import android.app.*
import android.content.*
import hu.grafit.MainActivity
import hu.grafit.R
import hu.grafit.data.*
import hu.grafit.notifications.*
import java.time.LocalDateTime

object StudyReminders {
    private fun pending(context: Context, id: Int, account: String = "", entry: String = "") = PendingIntent.getBroadcast(context, id,
        Intent(context, StudyReminderReceiver::class.java).setAction("hu.grafit.STUDY.$id").putExtra("account", account).putExtra("entry", entry),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    @Synchronized fun reschedule(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val prefs = context.getSharedPreferences("study_alarms", 0)
        prefs.getStringSet("ids", emptySet()).orEmpty().forEach { alarms.cancel(pending(context, it.toInt())) }
        val api = SchoolServices.api(context)
        val account = api.profileKey
        val ids = mutableSetOf<String>()
        if (account != null && SchoolNotifications.canNotify(context)) StudyStore(context).read(account).entries
            .filter { !it.done && it.reminder >= 0 }.forEach { entry ->
                runCatching {
                    val dateTime = LocalDateTime.parse("${entry.date}T${entry.time.ifBlank { "08:00" }}").minusMinutes(entry.reminder.toLong())
                    val at = dateTime.atZone(schoolZone).toInstant().toEpochMilli()
                    if (at > System.currentTimeMillis()) {
                        val id = ("study:$account:${entry.id}").hashCode()
                        ids.add(id.toString())
                        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(context, id, account, entry.id))
                    }
                }
            }
        prefs.edit().putStringSet("ids", ids).apply()
    }
}
class StudyReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        Thread {
            try {
                val account = intent.getStringExtra("account") ?: return@Thread
                synchronized(SchoolServices.accountLock) {
                    if (SchoolServices.api(context).profileKey != account || !SchoolNotifications.canNotify(context)) return@synchronized
                    val entry = StudyStore(context).read(account).entries.firstOrNull { it.id == intent.getStringExtra("entry") && !it.done && it.reminder >= 0 } ?: return@synchronized
                    val manager = context.getSystemService(NotificationManager::class.java)
                    manager.createNotificationChannel(NotificationChannel("study_reminders", "Tanulási emlékeztetők", NotificationManager.IMPORTANCE_DEFAULT))
                    val open = PendingIntent.getActivity(context, 42, Intent(context, MainActivity::class.java).putExtra("tool", "planner"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    val generic = Notification.Builder(context, "study_reminders").setSmallIcon(R.drawable.ic_grafit_notification).setContentTitle("Tanulási emlékeztető").build()
                    manager.notify("study:${entry.id}", 0, Notification.Builder(context, "study_reminders").setSmallIcon(R.drawable.ic_grafit_notification)
                        .setContentTitle(entry.title).setContentText("${entry.subject} · ${entry.date} ${entry.time}").setContentIntent(open).setAutoCancel(true)
                        .setVisibility(Notification.VISIBILITY_PRIVATE).setPublicVersion(generic).build())
                }
            } finally { result.finish() }
        }.start()
    }
}
