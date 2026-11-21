package hu.grafit.data

import org.json.JSONArray
import org.json.JSONObject

/** Message rules are shared by foreground sync and Android's background job. */
enum class SchoolEventKind(val label: String, val defaultTitle: String, val defaultMessage: String) {
    Grade("Új jegy", "Új jegy érkezett", "{tantargy}: {jegy} · {tema}"),
    BadGrade("Gyengébb jegy", "Új gyengébb jegy", "{tantargy}: {jegy}. A következő sikerülhet jobban!"),
    Homework("Házi feladat", "Új házi feladat", "{tantargy}: {feladat} · Határidő: {datum}"),
    Test("Bejelentett dolgozat", "Új dolgozat", "{tantargy}: {feladat} · {datum}")
}

data class NotificationOptions(
    val enabled: Boolean = false,
    val categories: Set<SchoolEventKind> = SchoolEventKind.entries.toSet(),
    val badGradeThreshold: Int = 2,
    val titles: Map<SchoolEventKind, String> = emptyMap(),
    val messages: Map<SchoolEventKind, String> = emptyMap(),
    val liveLesson: Boolean = false,
    val livePromoted: Boolean = true,
    val liveProgress: Boolean = true,
    val liveColor: Int = 0,
    val liveTitle: String = "{tantargy}",
    val liveMessage: String = "{terem} · {tanar}"
) {
    fun title(kind: SchoolEventKind) = titles[kind] ?: kind.defaultTitle
    fun message(kind: SchoolEventKind) = messages[kind] ?: kind.defaultMessage
    fun json(): String = JSONObject().put("enabled", enabled)
        .put("categories", JSONArray(categories.map { it.name })).put("badGradeThreshold", badGradeThreshold)
        .put("titles", JSONObject(titles.mapKeys { it.key.name })).put("messages", JSONObject(messages.mapKeys { it.key.name }))
        .put("liveLesson", liveLesson).put("livePromoted", livePromoted).put("liveProgress", liveProgress)
        .put("liveColor", liveColor).put("liveTitle", liveTitle).put("liveMessage", liveMessage).toString()
    companion object {
        fun parse(value: String?): NotificationOptions = runCatching {
            val j = JSONObject(value ?: "{}")
            val categories = j.optJSONArray("categories")?.let { a ->
                (0 until a.length()).mapNotNull { name -> SchoolEventKind.entries.find { it.name == a.optString(name) } }.toSet()
            } ?: SchoolEventKind.entries.toSet()
            fun templates(key: String) = SchoolEventKind.entries.mapNotNull { kind ->
                j.optJSONObject(key)?.text(kind.name)?.takeIf { it.isNotBlank() }?.take(500)?.let { kind to it }
            }.toMap()
            NotificationOptions(j.optBoolean("enabled"), categories, j.optInt("badGradeThreshold", 2).coerceIn(1, 4),
                templates("titles"), templates("messages"), j.optBoolean("liveLesson"), j.optBoolean("livePromoted", true),
                j.optBoolean("liveProgress", true), j.optInt("liveColor").coerceIn(0, 2),
                j.text("liveTitle", "{tantargy}").take(120), j.text("liveMessage", "{terem} · {tanar}").take(500))
        }.getOrDefault(NotificationOptions())
    }
}

/** Literal, single-pass substitution: data cannot inject other placeholders. */
fun renderNotification(template: String, values: Map<String, String>): String =
    Regex("\\{([a-z]+)\\}").replace(template) { values[it.groupValues[1]] ?: it.value }.take(1000)

data class SchoolEvent(val id: String, val kind: SchoolEventKind, val values: Map<String, String>)

/** Persist only IDs in the encrypted account store. First response establishes a silent baseline. */
class NotificationTracker(private val store: StringStore) {
    @Synchronized fun update(key: String, rows: JSONArray, options: NotificationOptions): List<SchoolEvent> {
        require(key in setOf("grades", "homework", "tests"))
        val state = store.read("notification_seen")?.let(::JSONObject) ?: JSONObject()
        val previous = state.optJSONArray(key)
        val seen = previous?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }.orEmpty()
        val records = rows.objects().associateBy { it.getString("Uid") }
        val events = if (previous == null || !options.enabled) emptyList() else records.filterKeys { it !in seen }.map { (id, j) ->
            if (key == "grades") {
                val grade = Grade.parse(j)
                val kind = if (grade.number != null && grade.number <= options.badGradeThreshold && grade.valueType == "Osztalyzat")
                    SchoolEventKind.BadGrade else SchoolEventKind.Grade
                SchoolEvent(id, kind, mapOf("tantargy" to grade.subject, "jegy" to grade.value,
                    "tema" to grade.topic, "tanar" to grade.teacher, "datum" to grade.date))
            } else {
                val task = if (key == "homework") SchoolTask.homework(j) else SchoolTask.exam(j)
                SchoolEvent(id, if (key == "homework") SchoolEventKind.Homework else SchoolEventKind.Test,
                    mapOf("tantargy" to task.subject, "feladat" to task.text, "tema" to task.text, "datum" to task.date))
            }
        }.filter { it.kind in options.categories }
        state.put(key, JSONArray((seen + records.keys).toList()))
        store.write("notification_seen", state.toString())
        return events
    }
}
