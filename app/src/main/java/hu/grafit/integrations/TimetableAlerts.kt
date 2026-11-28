package hu.grafit.integrations

import android.app.*
import android.content.Context
import android.content.Intent
import hu.grafit.MainActivity
import hu.grafit.R
import hu.grafit.data.LessonChange
import hu.grafit.notifications.SchoolNotifications

object TimetableAlerts {
    fun post(context: Context, changes: List<LessonChange>) {
        if (!SchoolNotifications.canNotify(context)) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("timetable_changes", "Órarend-változások", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(context, 83, Intent(context, MainActivity::class.java).putExtra("tool", "changes"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val generic = Notification.Builder(context, "timetable_changes").setSmallIcon(R.drawable.ic_grafit_notification).setContentTitle("Órarend-változás").build()
        val body = changes.take(5).joinToString("\n\n") { "${it.date} · ${it.subject}\n${it.details}" }
        try { manager.notify("timetable_changes", 0, Notification.Builder(context, "timetable_changes").setSmallIcon(R.drawable.ic_grafit_notification)
            .setContentTitle("${changes.size} órarendi változás").setContentText(body).setStyle(Notification.BigTextStyle().bigText(body))
            .setContentIntent(open).setAutoCancel(true).setVisibility(Notification.VISIBILITY_PRIVATE).setPublicVersion(generic).build()) }
        catch (_: SecurityException) { }
    }
}
