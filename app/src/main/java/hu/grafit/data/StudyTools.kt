package hu.grafit.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.text.Normalizer
import java.util.Locale
import kotlin.math.ceil

data class SimulatedGrade(val subject: String, val value: Int, val weight: Int = 100) {
    fun json() = JSONObject().put("subject", subject).put("value", value).put("weight", weight)
}
data class StudyEntry(val id: String, val title: String, val subject: String = "", val date: String,
    val time: String = "", val minutes: Int = 30, val kind: String = "task", val done: Boolean = false,
    val notes: String = "", val examKey: String = "", val reminder: Int = -1) {
    fun json() = JSONObject().put("id", id).put("title", title).put("subject", subject).put("date", date)
        .put("time", time).put("minutes", minutes).put("kind", kind).put("done", done).put("notes", notes).put("examKey", examKey).put("reminder", reminder)
    companion object {
        fun parse(j: JSONObject) = StudyEntry(j.getString("id"), j.getString("title"), j.text("subject"), j.getString("date"),
            j.text("time"), j.optInt("minutes", 30).coerceIn(5, 720), j.text("kind", "task"), j.optBoolean("done"),
            j.text("notes"), j.text("examKey"), j.optInt("reminder", -1))
    }
}
data class StudyState(val entries: List<StudyEntry> = emptyList(), val simulations: List<SimulatedGrade> = emptyList(),
    val examNotes: Map<String, String> = emptyMap(), val calendarId: Long = -1, val calendarSync: Boolean = false,
    val widgetGrades: Boolean = false,
    val calendarKinds: Set<String> = setOf("Órarend", "Házi feladat", "Dolgozat", "Tanulás", "Saját feladat", "Saját esemény"), val goals: Map<String, Double> = emptyMap()) {
    fun json() = JSONObject().put("goals", JSONObject(goals)).put("entries", JSONArray(entries.map { it.json() }))
        .put("simulations", JSONArray(simulations.map { it.json() })).put("examNotes", JSONObject(examNotes))
        .put("calendarKinds", JSONArray(calendarKinds.toList())).put("calendarId", calendarId).put("calendarSync", calendarSync).put("widgetGrades", widgetGrades).toString()
    companion object {
        fun parse(value: String?) = runCatching {
            val j = JSONObject(value ?: "{}")
            val notes = j.optJSONObject("examNotes") ?: JSONObject()
            StudyState(j.optJSONArray("entries")?.objects().orEmpty().map(StudyEntry::parse),
                j.optJSONArray("simulations")?.objects().orEmpty().map {
                    SimulatedGrade(it.getString("subject"), it.getInt("value").coerceIn(1, 5), it.optInt("weight", 100).coerceIn(1, 1000))
                }, notes.keys().asSequence().associateWith { notes.getString(it) }, j.optLong("calendarId", -1), j.optBoolean("calendarSync"), j.optBoolean("widgetGrades"),
                j.optJSONArray("calendarKinds")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
                    ?: setOf("Órarend", "Házi feladat", "Dolgozat", "Tanulás", "Saját feladat", "Saját esemény"),
                j.optJSONObject("goals")?.let { values -> values.keys().asSequence().mapNotNull { key -> values.optDouble(key).takeIf { it in 1.0..5.0 }?.let { key to it } }.toMap() }.orEmpty())
        }.getOrDefault(StudyState())
    }
}
fun taskKey(task: SchoolTask): String = task.id.ifBlank { "${task.kind}|${task.subject}|${task.date}|${task.text}" }
fun simulatedSubjects(data: SchoolData, extras: List<SimulatedGrade>): List<SubjectGrades> = data.gradeSummaries().map { group ->
    group.copy(grades = group.grades + extras.filter { it.subject == group.name }.mapIndexed { i, grade ->
        Grade("simulation-$i", group.name, grade.value.toString(), grade.value, grade.weight, "", "Próbajegy", "", "EvkoziJegyErtekeles")
    })
}
/** Null means the target cannot be reached with the specified future grade. */
fun gradesNeeded(grades: List<Grade>, target: Double, futureGrade: Int, weight: Int): Int? {
    require(target in 1.0..5.0 && futureGrade in 1..5 && weight > 0)
    val valid = grades.filter { it.number != null && it.weight > 0 && it.valueType == "Osztalyzat" }
    val total = valid.sumOf { it.weight.toDouble() }
    val sum = valid.sumOf { it.number!! * it.weight.toDouble() }
    if (total > 0 && sum / total >= target) return 0
    if (futureGrade.toDouble() < target || (futureGrade.toDouble() == target && total > 0)) return null
    if (total == 0.0) return 1
    return ceil((target * total - sum) / (weight * (futureGrade - target))).toInt().coerceAtLeast(1)
}
data class MonthStatistic(val month: String, val average: Double?, val count: Int)
fun monthlyGradeStats(grades: List<Grade>): List<MonthStatistic> = grades.filter { it.date.length >= 7 && it.number != null && it.weight > 0 && it.valueType == "Osztalyzat" }
    .groupBy { it.date.take(7) }.toSortedMap().map { (month, items) -> MonthStatistic(month, average(items), items.count { it.number != null }) }

data class CalendarItem(val key: String, val title: String, val start: LocalDateTime, val end: LocalDateTime,
    val kind: String, val notes: String, val allDay: Boolean = false)
fun calendarItems(data: SchoolData, state: StudyState): List<CalendarItem> = buildList {
    data.lessons.filter { it.hasSubject && it.state != "Elmaradt" }.forEach {
        add(CalendarItem("lesson:${it.id}", it.subject, it.start, it.end, "Órarend", listOf(it.room, it.teacher, it.topic).filter(String::isNotBlank).joinToString("\n")))
    }
    data.tasks.filter { it.kind in setOf("Házi feladat", "Dolgozat") }.forEach { task ->
        runCatching { LocalDate.parse(task.date) }.getOrNull()?.let { day ->
            add(CalendarItem("school:${task.kind}:${taskKey(task)}", "${task.kind}: ${task.subject}", day.atStartOfDay(), day.plusDays(1).atStartOfDay(), task.kind, task.text, true))
        }
    }
    state.entries.filter { !it.done && it.kind != "checklist" }.forEach { entry ->
        runCatching {
            val day = LocalDate.parse(entry.date)
            val start = if (entry.time.isBlank()) day.atStartOfDay() else LocalDateTime.parse("${entry.date}T${entry.time}")
            add(CalendarItem("personal:${entry.id}", entry.title, start, if (entry.time.isBlank()) day.plusDays(1).atStartOfDay() else start.plusMinutes(entry.minutes.toLong()),
                when (entry.kind) { "study" -> "Tanulás"; "event" -> "Saját esemény"; else -> "Saját feladat" }, entry.notes, entry.time.isBlank()))
        }
    }
}.distinctBy { it.key }.sortedBy { it.start }

fun searchKey(value: String): String = Normalizer.normalize(value.replace(Regex("<[^>]*>"), " "), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
data class SearchHit(val kind: String, val title: String, val body: String, val date: String,
    val task: SchoolTask? = null, val lesson: Lesson? = null, val entry: StudyEntry? = null, val grade: Grade? = null)
fun searchSchool(data: SchoolData, state: StudyState, query: String, hideGradeValues: Boolean = false): List<SearchHit> {
    val words = searchKey(query).trim().split(Regex("\\s+")).filter(String::isNotBlank)
    if (words.isEmpty()) return emptyList()
    val records = data.tasks.map { SearchHit(it.kind, it.subject, "${it.teacher}\n${it.text}", it.date, task = it) } +
        data.lessons.map { SearchHit("Tanóra", it.subject, "${it.teacher}\n${it.room}\n${it.topic}", it.start.toLocalDate().toString(), lesson = it) } +
        data.grades.map { SearchHit("Jegy", if (hideGradeValues) it.subject else "${it.subject}: ${it.value}", "${it.teacher}\n${it.topic}", it.date, grade = it) } +
        data.absences.map { SearchHit("Hiányzás", it.subject, "${it.state} · ${it.minutes} perc", it.date) } +
        state.entries.map { SearchHit("Saját terv", it.title, "${it.subject}\n${it.notes}", it.date, entry = it) } +
        state.examNotes.map { note ->
            val exam = data.tasks.firstOrNull { taskKey(it) == note.key }
            SearchHit("Dolgozatjegyzet", exam?.subject ?: "Mentett dolgozatjegyzet", note.value, exam?.date.orEmpty())
        }
    return records.filter { hit -> searchKey("${hit.title} ${hit.body} ${hit.date} ${hit.kind}").let { text -> words.all(text::contains) } }
        .sortedByDescending { it.date }.take(300)
}

/** Deletion of exported lessons needs actual cached coverage for that calendar day. */
fun canRemoveExport(kind: String, day: LocalDate, schoolCollectionsComplete: Boolean, coverage: List<Pair<LocalDate, LocalDate>>): Boolean = when (kind) {
    "personal" -> true
    "school" -> schoolCollectionsComplete
    "lesson" -> coverage.any { (from, to) -> day >= from && day < to }
    else -> false
}
