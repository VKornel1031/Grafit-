package hu.grafit.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Firka's full-collection reconciliation and half-open timetable replacement. */
class SchoolCache(private val store: StringStore) {
    private val lock = Any()
    private fun raw(): JSONObject = store.read("school")?.let(::JSONObject) ?: JSONObject()

    fun read(): SchoolData? = synchronized(lock) {
        runCatching { SchoolData.parse(raw()) }.getOrNull()
    }

    fun publish(onProgress: (SchoolData) -> Unit) = synchronized(lock) { read()?.let(onProgress) }

    fun snapshot(): JSONObject = synchronized(lock) { raw() }

    fun update(edit: (JSONObject) -> Unit) = synchronized(lock) {
        val data = raw()
        edit(data)
        val tasks = array(data, "homework").objects().map(SchoolTask::homework) +
            array(data, "tests").objects().map(SchoolTask::exam) +
            array(data, "notices").objects().map(SchoolTask::notice) +
            array(data, "notes").objects().map(SchoolTask::note)
        // Migrate the original flattened task cache when individual datasets are first synced.
        val legacyKinds = mapOf("homework" to "Házi feladat", "tests" to "Dolgozat",
            "notices" to "Faliújság", "notes" to "Feljegyzés").filterKeys { !data.has(it) }.values
        val legacy = array(data, "tasks").objects().map(SchoolTask::parse).filter { it.kind in legacyKinds }
        if (listOf("homework", "tests", "notices", "notes").any(data::has)) {
            data.put("tasks", JSONArray((tasks + legacy).distinct().map(SchoolTask::json)))
        }
        store.write("school", data.toString())
    }

    fun replace(key: String, rows: JSONArray) = update { it.put(key, rows) }

    fun mergeMessages(key: String, rows: JSONArray) = update { data ->
        val merged = (array(data, key).objects() + rows.objects()).associateBy { it.getString("Uid") }
        data.put(key, JSONArray(merged.values.toList()))
    }

    fun replaceLessons(from: LocalDate, to: LocalDate, rows: JSONArray) {
        // Validate the complete response before removing anything from the cached range.
        val fresh = rows.objects().map { it to Lesson.parse(it) }
            .filter { (_, lesson) -> lesson.start.toLocalDate() >= from && lesson.start.toLocalDate() < to }
        update { data ->
            val retained = array(data, "lessons").objects().filter {
                val date = localTime(it.getString("KezdetIdopont")).toLocalDate()
                date < from || date >= to
            }
            val combined = (retained + fresh.map { it.first }).associateBy { it.getString("Uid") }
            data.put("lessons", JSONArray(combined.values.toList()))
            val ranges = array(data, "lessonRanges").objects().map {
                LocalDate.parse(it.getString("from")) to LocalDate.parse(it.getString("to"))
            } + (from to to)
            val merged = mutableListOf<Pair<LocalDate, LocalDate>>()
            for (range in ranges.sortedBy { it.first }) {
                val previous = merged.lastOrNull()
                if (previous != null && range.first <= previous.second) {
                    merged[merged.lastIndex] = previous.first to maxOf(previous.second, range.second)
                } else merged.add(range)
            }
            data.put("lessonRanges", JSONArray(merged.map {
                JSONObject().put("from", it.first.toString()).put("to", it.second.toString())
            }))
        }
    }

    fun coversLessons(from: LocalDate, to: LocalDate): Boolean = synchronized(lock) {
        array(raw(), "lessonRanges").objects().any {
            LocalDate.parse(it.getString("from")) <= from && LocalDate.parse(it.getString("to")) >= to
        }
    }

    companion object {
        fun array(data: JSONObject, key: String): JSONArray = data.optJSONArray(key) ?: JSONArray()
        fun firstSchoolDay(date: LocalDate): LocalDate {
            val start = LocalDate.of(date.year, 9, 1)
            return if (date < start) start.minusYears(1) else start
        }
        fun chunks(from: LocalDate, to: LocalDate): List<Pair<LocalDate, LocalDate>> = buildList {
            var cursor = from
            while (cursor < to) {
                val end = minOf(cursor.plusDays(14), to)
                add(cursor to end)
                cursor = end
            }
        }
    }
}
