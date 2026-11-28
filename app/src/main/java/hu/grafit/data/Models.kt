package hu.grafit.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

val schoolZone: ZoneId = ZoneId.of("Europe/Budapest")
fun today(): LocalDate = LocalDate.now(schoolZone)
fun JSONObject.text(key: String, fallback: String = ""): String =
    if (isNull(key)) fallback else optString(key, fallback)
fun JSONObject.subject(): String = optJSONObject("Tantargy")?.text("Nev") ?: text("Nev", "Egyéb")
fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
fun localTime(value: String): LocalDateTime = runCatching {
    OffsetDateTime.parse(value).atZoneSameInstant(schoolZone).toLocalDateTime()
}.getOrElse { LocalDateTime.parse(value) }

data class Grade(val id: String, val subject: String, val value: String, val number: Int?, val weight: Int,
                 val date: String, val topic: String, val teacher: String, val type: String, val valueType: String = "Osztalyzat",
                 val subjectId: String? = null) {
    companion object {
        fun parse(j: JSONObject) = Grade(j.text("Uid"), j.subject(), j.text("SzamErtek", j.text("SzovegesErtek")),
            if (j.isNull("SzamErtek")) null else j.optInt("SzamErtek").takeIf { it in 1..5 },
            j.optInt("SulySzazalekErteke", 0), j.text("KeszitesDatuma").take(10), j.text("Tema"),
            j.text("ErtekeloTanarNeve"), j.optJSONObject("Tipus")?.text("Nev") ?: "", j.optJSONObject("ErtekFajta")?.text("Nev") ?: "",
            j.optJSONObject("Tantargy")?.text("Uid")?.takeIf { it.isNotBlank() })
    }
}
fun average(grades: List<Grade>): Double? {
    val numeric = grades.filter { it.number != null && it.weight > 0 && it.valueType == "Osztalyzat" }
    val total = numeric.sumOf { it.weight.toDouble() }
    return if (total == 0.0) null else numeric.sumOf { it.number!! * it.weight.toDouble() } / total
}

data class Lesson(val id: String, val subject: String, val start: LocalDateTime, val end: LocalDateTime,
                  val room: String, val teacher: String, val topic: String, val state: String,
                  val hasSubject: Boolean = true, val subjectId: String? = null, val homeworkId: String? = null) {
    companion object {
        fun parse(j: JSONObject) = Lesson(j.text("Uid"), j.subject(), localTime(j.getString("KezdetIdopont")),
            localTime(j.getString("VegIdopont")), j.text("TeremNeve"), j.text("HelyettesTanarNeve", j.text("TanarNeve")),
            j.text("Tema"), j.optJSONObject("Allapot")?.text("Nev") ?: "",
            j.optJSONObject("Tantargy")?.text("Nev")?.isNotBlank() == true,
            j.optJSONObject("Tantargy")?.text("Uid")?.takeIf { it.isNotBlank() },
            j.text("HaziFeladatUid").takeIf { it.isNotBlank() })
    }
}
data class Absence(val subject: String, val date: String, val state: String, val minutes: Int) {
    companion object { fun parse(j: JSONObject) = Absence(j.subject(), taskDate(j.text("Datum")),
        j.text("IgazolasAllapota"), j.optInt("KesesPercben")) }
}
data class SchoolSubject(val name: String, val id: String? = null)

data class SchoolData(val student: String, val school: String, val grades: List<Grade>,
                      val lessons: List<Lesson>, val absences: List<Absence>, val syncedAt: Long = System.currentTimeMillis(), val tasks: List<SchoolTask> = emptyList(),
                      val subjects: List<SchoolSubject> = emptyList(), val lessonChanges: List<LessonChange> = emptyList()) {
    val subjectNames: List<String> get() = subjects.map { it.name }.distinct()
    companion object {
        fun parse(j: JSONObject) = SchoolData(j.getJSONObject("student").text("Nev"),
            j.getJSONObject("student").text("IntezmenyNev"), (j.optJSONArray("grades") ?: JSONArray()).objects().map(Grade::parse),
            (j.optJSONArray("lessons") ?: JSONArray()).objects().map(Lesson::parse).sortedBy { it.start },
            (j.optJSONArray("absences") ?: JSONArray()).objects().map(Absence::parse), j.optLong("syncedAt"), j.optJSONArray("tasks")?.objects()?.map(SchoolTask::parse).orEmpty(),
            j.optJSONArray("subjects")?.objects().orEmpty().filter { it.optInt("tipusId", 0) == 0 }
                .map { SchoolSubject(it.text("tantargyNev").trim(), it.text("tantargyId").takeIf { id -> id.isNotBlank() }) }
                .filter { it.name.isNotBlank() }.distinct(),
            j.optJSONArray("lessonChanges")?.objects().orEmpty().map(LessonChange::parse))
        fun demo(): SchoolData {
            val subjects = listOf("Matematika", "Magyar irodalom", "Történelem", "Angol nyelv", "Informatika")
            val date = today()
            return SchoolData("Kiss Alex", "Grafit Bemutató Gimnázium", subjects.flatMapIndexed { i, s ->
                listOf(5, 4, 5).mapIndexed { n, v -> Grade("$i-$n", s, "$v", v, 100,
                    date.minusDays((i + n).toLong()).toString(), "Témazáró dolgozat", "Minta tanár", "EvkoziJegyErtekeles") }
            }, (-7L..14L).flatMap { offset ->
                val d = date.plusDays(offset)
                if (d.dayOfWeek.value > 5) emptyList() else subjects.mapIndexed { i, s ->
                    Lesson("$d-$i", s, d.atTime(8 + i, 0), d.atTime(8 + i, 45), "${102 + i}. terem",
                        "Minta tanár", "Ismétlés és gyakorlás", "Megtartott") }
            }, listOf(Absence("Matematika", date.minusDays(3).toString(), "Igazolt", 0)), tasks = listOf(
                SchoolTask("Matematika", "Házi feladat", "Tankönyv 42. oldal, 1–5. feladat.", date.plusDays(1).toString()),
                SchoolTask("Történelem", "Dolgozat", "A reformkor", date.plusDays(2).toString())))
        }
    }
}


private fun taskDate(value: String): String = runCatching { localTime(value).toLocalDate().toString() }.getOrDefault(value.take(10))

data class SchoolTask(val subject: String, val kind: String, val text: String, val date: String,
                      val id: String = "", val subjectId: String? = null, val teacher: String = "", val assignedDate: String = "") {
    companion object {
        fun parse(j: JSONObject) = SchoolTask(j.text("subject"), j.text("kind"), j.text("text"), j.text("date"),
            j.text("id"), j.text("subjectId").takeIf { it.isNotBlank() }, j.text("teacher"), j.text("assignedDate"))
        fun homework(j: JSONObject) = SchoolTask(j.subject(), "Házi feladat", j.text("Szoveg"), taskDate(j.text("HataridoDatuma")),
            j.text("Uid"), j.optJSONObject("Tantargy")?.text("Uid")?.takeIf { it.isNotBlank() }, j.text("RogzitoTanarNeve"), taskDate(j.text("FeladasDatuma")))
        fun exam(j: JSONObject) = SchoolTask(j.subject(), "Dolgozat", j.text("Temaja"), taskDate(j.text("Datum")),
            j.text("Uid"), j.optJSONObject("Tantargy")?.text("Uid")?.takeIf { it.isNotBlank() }, j.text("RogzitoTanarNeve"))
        fun note(j: JSONObject) = SchoolTask(j.text("Cim"), "Feljegyzés", j.text("Tartalom").ifBlank { j.text("TartalomFormazott") }, taskDate(j.text("Datum")),
            id = j.text("Uid"), teacher = j.text("KeszitoTanarNeve"))
        fun notice(j: JSONObject) = SchoolTask(j.text("Cim"), "Faliújság", j.text("TartalomText").ifBlank { j.text("Tartalom") }, taskDate(j.text("ErvenyessegKezdete")),
            id = j.text("Uid"), teacher = j.text("RogzitoNeve"))
    }
    fun json() = JSONObject().put("subject", subject).put("kind", kind).put("text", text).put("date", date)
        .put("id", id).put("subjectId", subjectId).put("teacher", teacher).put("assignedDate", assignedDate)
}
