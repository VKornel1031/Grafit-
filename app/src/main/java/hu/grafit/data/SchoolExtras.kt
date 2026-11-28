package hu.grafit.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth

/** Extra tools are local to an account and never write to KRÉTA. */
data class ExcuseRecord(val date: String, val deadline: String = "", val notes: String = "", val status: String = "pending",
    val attachment: String = "", val fileName: String = "", val mime: String = "", val remind: Boolean = false) {
    fun json() = JSONObject().put("date", date).put("deadline", deadline).put("notes", notes).put("status", status)
        .put("attachment", attachment).put("fileName", fileName).put("mime", mime).put("remind", remind)
    companion object { fun parse(j: JSONObject) = ExcuseRecord(j.getString("date"), j.text("deadline"), j.text("notes"),
        j.text("status", "pending"), j.text("attachment"), j.text("fileName"), j.text("mime"), j.optBoolean("remind")) }
}
data class RoomPin(val room: String, val x: Float, val y: Float) {
    fun json() = JSONObject().put("room", room).put("x", x.toDouble()).put("y", y.toDouble())
}
data class SchoolFloor(val id: String, val name: String, val attachment: String, val mime: String, val pins: List<RoomPin> = emptyList()) {
    fun json() = JSONObject().put("id", id).put("name", name).put("attachment", attachment).put("mime", mime)
        .put("pins", JSONArray(pins.map { it.json() }))
    companion object { fun parse(j: JSONObject) = SchoolFloor(j.getString("id"), j.getString("name"), j.getString("attachment"), j.text("mime"),
        j.optJSONArray("pins")?.objects().orEmpty().map { RoomPin(it.getString("room"), it.getDouble("x").toFloat().coerceIn(0f, 1f), it.getDouble("y").toFloat().coerceIn(0f, 1f)) }) }
}
data class SchoolExtras(val excuses: List<ExcuseRecord> = emptyList(), val floors: List<SchoolFloor> = emptyList(),
    val changesNotify: Boolean = false, val notifiedChanges: Set<String> = emptySet(), val catchUpDone: Set<String> = emptySet()) {
    fun json() = JSONObject().put("excuses", JSONArray(excuses.map { it.json() })).put("floors", JSONArray(floors.map { it.json() }))
        .put("changesNotify", changesNotify).put("notifiedChanges", JSONArray(notifiedChanges.toList())).put("catchUpDone", JSONArray(catchUpDone.toList())).toString()
    companion object {
        fun parse(raw: String?): SchoolExtras = runCatching {
            val j = JSONObject(raw ?: "{}")
            fun strings(key: String) = j.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }.orEmpty()
            SchoolExtras(j.optJSONArray("excuses")?.objects().orEmpty().map(ExcuseRecord::parse),
                j.optJSONArray("floors")?.objects().orEmpty().map(SchoolFloor::parse), j.optBoolean("changesNotify"), strings("notifiedChanges"), strings("catchUpDone"))
        }.getOrDefault(SchoolExtras())
    }
}
data class LessonChange(val id: String, val subject: String, val date: String, val details: String, val detectedAt: Long) {
    fun json() = JSONObject().put("id", id).put("subject", subject).put("date", date).put("details", details).put("detectedAt", detectedAt)
    companion object { fun parse(j: JSONObject) = LessonChange(j.getString("id"), j.getString("subject"), j.getString("date"), j.getString("details"), j.getLong("detectedAt")) }
}
/** Only previously fetched days can generate changes; initial fetches establish a baseline. */
fun timetableChanges(old: List<Lesson>, fresh: List<Lesson>, covered: (LocalDate) -> Boolean, at: Long): List<LessonChange> {
    val remaining = old.filter { it.hasSubject }.toMutableList()
    val changes = mutableListOf<LessonChange>()
    fun add(lesson: Lesson, detail: String) {
        changes += LessonChange("${lesson.id}:$at:$detail", lesson.subject, lesson.start.toLocalDate().toString(), detail, at)
    }
    fresh.filter { it.hasSubject }.forEach { lesson ->
        val previous = remaining.firstOrNull { it.id == lesson.id }
            ?: remaining.firstOrNull { it.start == lesson.start && it.subject == lesson.subject }
        if (previous != null) remaining.remove(previous)
        if (!covered(lesson.start.toLocalDate()) && (previous == null || !covered(previous.start.toLocalDate()))) return@forEach
        if (previous == null) { add(lesson, "Új tanóra · ${lesson.start.toLocalTime()}–${lesson.end.toLocalTime()}"); return@forEach }
        val details = buildList {
            if (previous.subject != lesson.subject) add("Tantárgy: ${previous.subject} → ${lesson.subject}")
            if (previous.start != lesson.start || previous.end != lesson.end) add("Időpont: ${previous.start.toLocalDate()} ${previous.start.toLocalTime()}–${previous.end.toLocalTime()} → ${lesson.start.toLocalDate()} ${lesson.start.toLocalTime()}–${lesson.end.toLocalTime()}")
            if (previous.room != lesson.room) add("Terem: ${previous.room.ifBlank { "—" }} → ${lesson.room.ifBlank { "—" }}")
            if (previous.teacher != lesson.teacher) add("Tanár: ${previous.teacher.ifBlank { "—" }} → ${lesson.teacher.ifBlank { "—" }}")
            if (previous.state != lesson.state) add("Állapot: ${previous.state.ifBlank { "Megtartva" }} → ${lesson.state.ifBlank { "Megtartva" }}")
        }
        if (details.isNotEmpty()) add(lesson, details.joinToString("\n"))
    }
    remaining.filter { covered(it.start.toLocalDate()) }.forEach { add(it, "Kimaradt a friss órarendből · ${it.start.toLocalTime()}") }
    return changes
}
data class CatchUpItem(val key: String, val title: String, val date: String, val notes: String, val task: SchoolTask? = null, val lesson: Lesson? = null)
fun catchUpItems(data: SchoolData, from: LocalDate, to: LocalDate, now: LocalDate): List<CatchUpItem> {
    require(from <= to)
    val lessons = data.lessons.filter { it.hasSubject && it.state != "Elmaradt" && it.start.toLocalDate() in from..to }
    val subjects = lessons.map { searchKey(it.subject).trim() }.toSet()
    fun identity(id: String?) = id?.substringBefore(',')?.trim()?.lowercase()?.takeIf { it.isNotBlank() && it != "0" }
    fun related(task: SchoolTask) = lessons.any { lesson ->
        val first = identity(lesson.subjectId); val second = identity(task.subjectId)
        if (first != null && second != null) first == second else searchKey(task.subject).trim() in subjects
    }
    return (lessons.map { CatchUpItem("lesson:${it.id}", it.subject, it.start.toLocalDate().toString(), it.topic.ifBlank { "A letöltött órán nincs megadva tananyag." }, lesson = it) } +
        data.tasks.filter { task -> runCatching { LocalDate.parse(task.date) }.getOrNull()?.let { date ->
            task.kind in setOf("Házi feladat", "Dolgozat") && (date in from..to ||
                (task.kind == "Dolgozat" && date in now..now.plusDays(14) && related(task)))
        } == true }.map { CatchUpItem("task:${taskKey(it)}", "${it.kind} · ${it.subject}", it.date, it.text, task = it) }).distinctBy { it.key }.sortedBy { it.date }
}
data class WorkloadDay(val date: LocalDate, val lessons: Int, val homework: Int, val tests: Int, val personal: Int, val items: List<CalendarItem>) {
    val score get() = lessons + homework * 2 + tests * 4 + personal * 2
    val level get() = when { score >= 16 -> "Sűrű nap"; score >= 8 -> "Közepes terhelés"; score > 0 -> "Könnyebb nap"; else -> "Nincs mentett teendő" }
}
fun workload(data: SchoolData, study: StudyState, from: LocalDate, days: Int = 14): List<WorkloadDay> {
    require(days in 1..31)
    val events = calendarItems(data, study).groupBy { it.start.toLocalDate() }
    return (0 until days).map { offset ->
        val date = from.plusDays(offset.toLong()); val items = events[date].orEmpty()
        WorkloadDay(date, items.count { it.kind == "Órarend" }, items.count { it.kind == "Házi feladat" }, items.count { it.kind == "Dolgozat" }, items.count { it.key.startsWith("personal:") }, items)
    }
}
data class MonthlyRecap(val month: YearMonth, val numericCount: Int, val average: Double?, val previousAverage: Double?,
    val distribution: Map<Int, Int>, val strongest: String?, val improved: String?, val improvement: Double?,
    val absenceCount: Int, val lateMinutes: Int, val completed: Int, val homework: Int, val exams: Int, val activeDays: Int)
fun monthlyRecap(data: SchoolData, study: StudyState, month: YearMonth): MonthlyRecap {
    fun inMonth(date: String, target: YearMonth = month) = runCatching { YearMonth.from(LocalDate.parse(date)) == target }.getOrDefault(false)
    val grades = data.grades.filter { inMonth(it.date) && it.number != null && it.weight > 0 && it.valueType == "Osztalyzat" }
    val previous = data.grades.filter { inMonth(it.date, month.minusMonths(1)) }
    val groups = data.gradeSummaries()
    val averages = groups.mapNotNull { g -> average(g.grades.filter { inMonth(it.date) })?.let { g.name to it } }
    val improvements = groups.mapNotNull { g ->
        val current = average(g.grades.filter { inMonth(it.date) }); val before = average(g.grades.filter { inMonth(it.date, month.minusMonths(1)) })
        if (current != null && before != null && current > before) g.name to current - before else null
    }.maxByOrNull { it.second }
    val absences = data.absences.filter { inMonth(it.date) }
    return MonthlyRecap(month, grades.size, average(grades), average(previous), (1..5).associateWith { n -> grades.count { it.number == n } },
        averages.maxWithOrNull(compareBy<Pair<String, Double>> { it.second }.thenByDescending { it.first })?.first,
        improvements?.first, improvements?.second, absences.count { it.minutes == 0 }, absences.sumOf { it.minutes },
        study.entries.count { it.done && it.kind != "checklist" && inMonth(it.date) }, data.tasks.count { it.kind == "Házi feladat" && inMonth(it.date) },
        data.tasks.count { it.kind == "Dolgozat" && inMonth(it.date) }, grades.map { it.date }.distinct().size)
}

fun excuseStillNeeded(data: SchoolData?, date: String): Boolean {
    val rows = data?.absences.orEmpty().filter { it.date == date }
    return rows.isEmpty() || rows.any { searchKey(it.state).trim() != "igazolt" }
}
fun excuseReminder(record: ExcuseRecord, data: SchoolData?): StudyEntry? {
    if (!record.remind || record.status == "submitted" || record.deadline.isBlank() || !excuseStillNeeded(data, record.date)) return null
    LocalDate.parse(record.deadline)
    return StudyEntry("excuse:${record.date}", "Hiányzásigazolás határideje", date = record.deadline, time = "08:00", notes = "Hiányzás: ${record.date}", reminder = 0)
}
