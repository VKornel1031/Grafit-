package hu.grafit.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import hu.grafit.MainActivity
import hu.grafit.R
import hu.grafit.data.*

/** One API per process serialises jobs with foreground login, sync, and logout. */
object SchoolServices {
    internal val accountLock = Any()
    @Volatile private var client: KretaApi? = null
    @Synchronized fun api(context: Context): KretaApi = client ?: run {
        val app = context.applicationContext
        val tracker = NotificationTracker(SecureStore(app))
        KretaApi(SecureStore(app), onCollectionUpdated = { key, rows ->
            val options = SchoolNotifications.options(app)
            val events = tracker.update(key, rows, options)
            if (SchoolNotifications.canNotify(app)) events.forEach { SchoolNotifications.post(app, it, options) }
        }, accountMutationLock = accountLock, accountArchive = SecureStore(app, "grafit_accounts")).also { client = it }
    }
}

object SchoolNotifications {
    private const val CHANNEL = "school_updates"
    private const val JOB_ID = 1201
    fun options(context: Context): NotificationOptions = NotificationOptions.parse(
        context.getSharedPreferences("notifications", 0).getString("options", null))

    @Synchronized fun save(context: Context, options: NotificationOptions) {
        context.getSharedPreferences("notifications", 0).edit().putString("options", options.json()).apply()
        schedule(context)
        LessonLiveNotification.requestUpdate(context)
        if (!options.enabled) context.getSystemService(NotificationManager::class.java).cancelAll()
    }

    fun canNotify(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()

    fun schedule(context: Context) {
        val scheduler = context.getSystemService(android.app.job.JobScheduler::class.java)
        val api = SchoolServices.api(context)
        val calendarEnabled = api.profileKey?.let { StudyStore(context).read(it).calendarSync } == true && hu.grafit.integrations.GoogleCalendarSync.permitted(context)
        val enabled = api.signedIn && ((options(context).enabled && options(context).categories.isNotEmpty() && canNotify(context)) || calendarEnabled)
        if (!enabled) { scheduler.cancel(JOB_ID); return }
        // Keep an existing schedule: repeated app opens must not postpone the next check.
        if (scheduler.getPendingJob(JOB_ID) == null) scheduler.schedule(JobInfo.Builder(JOB_ID,
            ComponentName(context, NotificationJobService::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true)
            .setPeriodic(15 * 60 * 1000L).build())
    }

    internal fun contentIntent(context: Context): PendingIntent = PendingIntent.getActivity(context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    @Synchronized fun post(context: Context, event: SchoolEvent, options: NotificationOptions, preview: Boolean = false) {
        val latest = SchoolNotifications.options(context)
        if (!latest.enabled || (!preview && event.kind !in latest.categories) || !canNotify(context)) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Iskolai újdonságok", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Új jegyek, házi feladatok és bejelentett dolgozatok"
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        })
        val title = renderNotification(options.title(event.kind), event.values)
        val body = renderNotification(options.message(event.kind), event.values)
        val generic = Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_grafit_notification)
            .setContentTitle("Grafit").setContentText("Új iskolai értesítés").build()
        val notification = Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_grafit_notification)
            .setContentTitle(title).setContentText(body).setStyle(Notification.BigTextStyle().bigText(body))
            .setContentIntent(contentIntent(context)).setAutoCancel(true).setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(generic).build()
        try { manager.notify("${event.kind}:${event.id}", 0, notification) }
        catch (_: SecurityException) { /* Permission can be revoked while the notification is built. */ }
    }
}
