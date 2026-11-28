package hu.grafit.widgets

import android.app.*
import android.appwidget.*
import android.content.*
import android.graphics.Color
import android.os.Bundle
import android.widget.RemoteViews
import hu.grafit.MainActivity
import hu.grafit.R
import hu.grafit.data.*
import hu.grafit.notifications.SchoolServices
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

open class GrafitWidget : AppWidgetProvider() {
    protected open val mode: Int = 0
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) { updateAll(context) }
    override fun onDisabled(context: Context) { updateAll(context) }
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) { render(context, manager, id, mode) }
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == "hu.grafit.WIDGET_REFRESH") updateAll(context)
    }
    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            listOf(GrafitWidget::class.java, NextClassWidget::class.java, HomeworkWidget::class.java, GradesWidget::class.java).forEachIndexed { mode, cls ->
                manager.getAppWidgetIds(ComponentName(context, cls)).forEach { render(context, manager, it, mode) }
            }
            scheduleClock(context)
        }
        private fun scheduleClock(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val installed = listOf(GrafitWidget::class.java, NextClassWidget::class.java, HomeworkWidget::class.java, GradesWidget::class.java)
                .any { manager.getAppWidgetIds(ComponentName(context, it)).isNotEmpty() }
            val pending = PendingIntent.getBroadcast(context, 9301, Intent(context, WidgetClockReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val alarms = context.getSystemService(AlarmManager::class.java)
            val next = if (installed) nextLessonTransition(SchoolServices.api(context).cached()?.lessons.orEmpty(), java.time.Instant.now()) else null
            if (next == null) alarms.cancel(pending) else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toEpochMilli(), pending)
        }
        private fun render(context: Context, manager: AppWidgetManager, id: Int, mode: Int) {
            val api = SchoolServices.api(context)
            synchronized(SchoolServices.accountLock) {
                val data = api.cached()
                val account = api.profileKey
                val state = account?.let { StudyStore(context).read(it) } ?: StudyState()
                val time = DateTimeFormatter.ofPattern("HH:mm")
                val now = LocalDateTime.now(schoolZone)
                val lessons = data?.lessons.orEmpty().filter { it.hasSubject && it.state != "Elmaradt" && it.start.toLocalDate() == today() }.sortedBy { it.start }
                val title = listOf("Mai órarend", "Következő tanóra", "Feladatok", "Legutóbbi jegyek")[mode]
                val body = if (account == null || data == null) "Nyisd meg a Grafitot és jelentkezz be." else when (mode) {
                    0 -> lessons.joinToString("\n") { "${it.start.format(time)}  ${it.subject} · ${it.room}" }.ifBlank { "Ma nincsenek letöltött tanórák." }
                    1 -> lessons.firstOrNull { it.end > now }?.let { "${it.subject}\n${it.start.format(time)}–${it.end.format(time)} · ${it.room}\n${it.teacher}" } ?: if (lessons.isEmpty()) "Erre a napra nincs letöltött tanóra." else "Mára nincs több tanórád."
                    2 -> (data.tasks.filter { it.kind in setOf("Házi feladat", "Dolgozat") && it.date >= today().toString() }
                        .map { "${it.date} · ${it.subject}: ${it.text.replace(Regex("<[^>]*>"), " ")}" } + state.entries.filter { !it.done && it.date >= today().toString() }.map { "${it.date} · ${it.title}" })
                        .sorted().take(4).joinToString("\n").ifBlank { "Nincsenek közelgő feladatok." }
                    else -> if (!state.widgetGrades || runCatching { org.json.JSONObject(context.getSharedPreferences("appearance", 0).getString("configuration", "{}")!!).optBoolean("hide") }.getOrDefault(true)) "A jegyek rejtve vannak. Engedélyezd az Eszközök → Widgetek menüben." else data.grades.sortedByDescending { it.date }.take(4)
                        .joinToString("\n") { "${it.subject}: ${it.value} · ${it.date}" }.ifBlank { "Nincsenek letöltött jegyek." }
                }
                val view = RemoteViews(context.packageName, R.layout.grafit_widget)
                view.setTextViewText(R.id.widget_title, "grafit · $title")
                view.setTextViewText(R.id.widget_body, body.take(3000))
                val css = runCatching { org.json.JSONObject(context.getSharedPreferences("appearance", 0).getString("configuration", "{}")!!) }.getOrNull()?.optString("customCss").orEmpty()
                val primary = runCatching { hu.grafit.ui.ThemeCss.parse(css)["primary"]?.let { Color.parseColor(if (it.length == 4) "#" + it.drop(1).map { c -> "$c$c" }.joinToString("") else it) } }.getOrNull()
                view.setTextColor(R.id.widget_title, primary ?: Color.LTGRAY)
                val open = PendingIntent.getActivity(context, id, Intent(context, MainActivity::class.java).putExtra("tool", if (mode == 2) "planner" else if (mode == 3) "stats" else "calendar"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                view.setOnClickPendingIntent(R.id.widget_body, open)
                view.setOnClickPendingIntent(R.id.widget_title, open)
                val refresh = PendingIntent.getBroadcast(context, id, Intent(context, GrafitWidget::class.java).setAction("hu.grafit.WIDGET_REFRESH"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                view.setOnClickPendingIntent(R.id.widget_refresh, refresh)
                manager.updateAppWidget(id, view)
            }
        }
    }
}
class NextClassWidget : GrafitWidget() { override val mode = 1 }
class HomeworkWidget : GrafitWidget() { override val mode = 2 }
class GradesWidget : GrafitWidget() { override val mode = 3 }

class WidgetClockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Thread { try { GrafitWidget.updateAll(context.applicationContext) } finally { pending.finish() } }.start()
    }
}
