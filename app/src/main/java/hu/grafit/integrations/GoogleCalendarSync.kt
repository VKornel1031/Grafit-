package hu.grafit.integrations

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract as C
import android.text.Html
import hu.grafit.data.*
import java.time.ZoneOffset

/** One-way export through Android's calendar provider. Google handles cloud transport. */
object GoogleCalendarSync {
    data class Calendar(val id: Long, val name: String, val account: String, val google: Boolean)
    fun permitted(context: Context) = listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
        .all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    fun calendars(context: Context): List<Calendar> {
        if (!permitted(context)) return emptyList()
        val result = mutableListOf<Calendar>()
        context.contentResolver.query(C.Calendars.CONTENT_URI,
            arrayOf(C.Calendars._ID, C.Calendars.CALENDAR_DISPLAY_NAME, C.Calendars.ACCOUNT_NAME, C.Calendars.ACCOUNT_TYPE),
            "${C.Calendars.CALENDAR_ACCESS_LEVEL} >= ? AND ${C.Calendars.SYNC_EVENTS} = 1", arrayOf(C.Calendars.CAL_ACCESS_CONTRIBUTOR.toString()), null)?.use {
            while (it.moveToNext()) result.add(Calendar(it.getLong(0), it.getString(1).orEmpty(), it.getString(2).orEmpty(), it.getString(3) == "com.google"))
        }
        return result.sortedByDescending { it.google }
    }
    fun syncSaved(context: Context) {
        val api = hu.grafit.notifications.SchoolServices.api(context)
        val snapshot = synchronized(hu.grafit.notifications.SchoolServices.accountLock) {
            val account = api.profileKey ?: return
            val cached = api.calendarSnapshot()
            val data = cached.first ?: return
            SavedExport(account, data, StudyStore(context).read(account), cached.second)
        }
        if (snapshot.state.calendarSync && permitted(context)) sync(context, snapshot.account, snapshot.data, true, snapshot.coverage)
    }
    private data class SavedExport(val account: String, val data: SchoolData, val state: StudyState, val coverage: List<Pair<java.time.LocalDate, java.time.LocalDate>>)
    private fun prefix(account: String) = "grafit://events/${accountHash(account)}/"
    @Synchronized fun sync(context: Context, account: String, data: SchoolData, replaceSchool: Boolean = false, coverage: List<Pair<java.time.LocalDate, java.time.LocalDate>> = emptyList()): Int {
        val state = StudyStore(context).read(account)
        if (!state.calendarSync || state.calendarId < 0 || hu.grafit.notifications.SchoolServices.api(context).profileKey != account) return 0
        check(permitted(context)) { "A naptárengedély hiányzik. Engedélyezd az Android beállításaiban." }
        check(calendars(context).any { it.id == state.calendarId }) { "A kiválasztott naptár már nem írható." }
        val start = today().atStartOfDay()
        val end = start.plusDays(90)
        val items = calendarItems(data, state).filter { it.start >= start && it.start < end && it.kind in state.calendarKinds }
        val resolver = context.contentResolver
        val owned = mutableMapOf<String, MutableList<Long>>()
        val starts = mutableMapOf<Long, Long>()
        val existing = mutableMapOf<Long, List<String>>()
        resolver.query(C.Events.CONTENT_URI, arrayOf(C.Events._ID, C.Events.CUSTOM_APP_URI, C.Events.DTSTART, C.Events.TITLE, C.Events.DESCRIPTION, C.Events.DTEND, C.Events.EVENT_TIMEZONE, C.Events.ALL_DAY),
            "${C.Events.CALENDAR_ID}=? AND ${C.Events.CUSTOM_APP_PACKAGE}=? AND ${C.Events.CUSTOM_APP_URI} LIKE ? AND ${C.Events.DELETED}=0",
            arrayOf(state.calendarId.toString(), context.packageName, prefix(account) + "%"), null)?.use {
            while (it.moveToNext()) {
                owned.getOrPut(it.getString(1)) { mutableListOf() }.add(it.getLong(0))
                starts[it.getLong(0)] = it.getLong(2)
                existing[it.getLong(0)] = listOf(it.getString(3).orEmpty(), it.getString(4).orEmpty(), it.getLong(2).toString(), it.getLong(5).toString(), it.getString(6).orEmpty(), it.getInt(7).toString())
            }
        }
        val expected = mutableSetOf<String>()
        for (item in items) {
            val uri = prefix(account) + item.key.substringBefore(':') + "/" + accountHash(item.key)
            expected.add(uri)
            val zone = if (item.allDay) ZoneOffset.UTC else schoolZone
            val values = ContentValues().apply {
                put(C.Events.CALENDAR_ID, state.calendarId)
                put(C.Events.TITLE, item.title)
                put(C.Events.DESCRIPTION, if (item.key.startsWith("personal:")) item.notes else Html.fromHtml(item.notes, Html.FROM_HTML_MODE_COMPACT).toString())
                put(C.Events.DTSTART, item.start.atZone(zone).toInstant().toEpochMilli())
                put(C.Events.DTEND, item.end.atZone(zone).toInstant().toEpochMilli())
                put(C.Events.EVENT_TIMEZONE, if (item.allDay) "UTC" else zone.id)
                put(C.Events.ALL_DAY, if (item.allDay) 1 else 0)
                put(C.Events.CUSTOM_APP_PACKAGE, context.packageName)
                put(C.Events.CUSTOM_APP_URI, uri)
            }
            val ids = owned[uri].orEmpty()
            if (ids.isEmpty()) checkNotNull(resolver.insert(C.Events.CONTENT_URI, values)) { "Az esemény mentése sikertelen." }
            else {
                val desired = listOf(C.Events.TITLE, C.Events.DESCRIPTION, C.Events.DTSTART, C.Events.DTEND, C.Events.EVENT_TIMEZONE, C.Events.ALL_DAY).map { values.getAsString(it).orEmpty() }
                if (existing[ids.first()] != desired) resolver.update(ContentUris.withAppendedId(C.Events.CONTENT_URI, ids.first()), values, null, null)
                ids.drop(1).forEach { resolver.delete(ContentUris.withAppendedId(C.Events.CONTENT_URI, it), null, null) }
            }
        }
        // Only delete this account's own exports; user-created and other-app events are untouched.
        val fromMillis = start.atZone(ZoneOffset.UTC).toInstant().toEpochMilli() - 24 * 60 * 60 * 1000L
        val toMillis = end.atZone(schoolZone).toInstant().toEpochMilli()
        owned.filterKeys { it !in expected }.forEach { (uri, ids) ->
            val kind = uri.removePrefix(prefix(account)).substringBefore('/')
            ids.filter { id ->
                val at = starts[id] ?: return@filter false
                at in fromMillis until toMillis && canRemoveExport(kind, java.time.Instant.ofEpochMilli(at).atZone(schoolZone).toLocalDate(), replaceSchool, coverage)
            }.forEach { resolver.delete(ContentUris.withAppendedId(C.Events.CONTENT_URI, it), null, null) }
        }
        return items.size
    }
    @Synchronized fun removeExports(context: Context, account: String, calendarId: Long) {
        check(permitted(context)) { "Naptárengedély szükséges." }
        context.contentResolver.delete(C.Events.CONTENT_URI,
            "${C.Events.CALENDAR_ID}=? AND ${C.Events.CUSTOM_APP_PACKAGE}=? AND ${C.Events.CUSTOM_APP_URI} LIKE ?",
            arrayOf(calendarId.toString(), context.packageName, prefix(account) + "%"))
    }
}
