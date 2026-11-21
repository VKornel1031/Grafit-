package hu.grafit.notifications

import android.app.job.JobParameters
import android.app.job.JobService
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors
import java.util.concurrent.Future

class NotificationJobService : JobService() {
    private val executor = Executors.newSingleThreadExecutor()
    private var pending: Future<*>? = null
    private var generation = 0
    override fun onStartJob(params: JobParameters): Boolean {
        if (!SchoolNotifications.options(this).enabled || !SchoolNotifications.canNotify(this)) return false
        val current = ++generation
        pending = executor.submit {
            try { SchoolServices.api(this).checkNotificationUpdates(); LessonLiveNotification.update(this) }
            catch (_: Exception) { /* Keep the account and cache; the next scheduled check can recover. */ }
            Handler(Looper.getMainLooper()).post { if (current == generation) jobFinished(params, false) }
        }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean {
        generation++
        pending?.cancel(true)
        return true
    }
    override fun onDestroy() {
        generation++
        executor.shutdownNow()
        super.onDestroy()
    }
}
