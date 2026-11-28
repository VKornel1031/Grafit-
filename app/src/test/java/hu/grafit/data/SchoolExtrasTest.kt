package hu.grafit.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

class SchoolExtrasTest {
    private val day = LocalDate.of(2026, 11, 28)
    private fun lesson(id: String = "l1", subject: String = "Matematika", date: LocalDate = day) = Lesson(id, subject, date.atTime(8, 0), date.atTime(8, 45), "101", "Tanár", "Egyenletek", "Megtartott")
    private fun grade(id: String, n: Int?, date: String, weight: Int = 100) = Grade(id, "Matematika", "$n", n, weight, date, "", "", "EvkoziJegyErtekeles")
    private fun data(grades: List<Grade> = emptyList(), lessons: List<Lesson> = listOf(lesson()), tasks: List<SchoolTask> = emptyList(), absences: List<Absence> = emptyList()) = SchoolData("Teszt", "Iskola", grades, lessons, absences, tasks = tasks)
    @Test fun firstTimetableFetchIsNotAChange() {
        assertTrue(timetableChanges(emptyList(), listOf(lesson()), { false }, 1).isEmpty())
    }
    @Test fun replacementIdsAndTopicEditsDoNotCreateFalseAlerts() {
        assertTrue(timetableChanges(listOf(lesson()), listOf(lesson().copy(id = "new", topic = "Új tananyag")), { true }, 1).isEmpty())
    }
    @Test fun timetableChangesRetainExactOldAndNewDetails() {
        val changes = timetableChanges(listOf(lesson()), listOf(lesson().copy(room = "202", teacher = "Helyettes", state = "Elmaradt")), { true }, 45)
        assertEquals(1, changes.size)
        assertTrue(changes.single().details.contains("101 → 202"))
        assertTrue(changes.single().details.contains("Tanár → Helyettes"))
        assertTrue(changes.single().details.contains("Elmaradt"))
        assertEquals(changes.single(), LessonChange.parse(changes.single().json()))
    }
    @Test fun removalsAndAdditionsNeedPriorCoverage() {
        assertEquals(1, timetableChanges(listOf(lesson()), emptyList(), { true }, 1).size)
        assertTrue(timetableChanges(listOf(lesson()), emptyList(), { false }, 1).isEmpty())
        assertEquals(1, timetableChanges(emptyList(), listOf(lesson()), { true }, 1).size)
        assertTrue(timetableChanges(emptyList(), listOf(lesson().copy(hasSubject = false)), { true }, 1).isEmpty())
    }
    @Test fun cacheDiffDoesNotCallFirstFetchAnAlertAndPersistsOnlyActualChanges() {
        val values = mutableMapOf<String, String>()
        val store = object : StringStore {
            override fun read(name: String) = values[name]
            override fun write(name: String, value: String) { values[name] = value }
            override fun clear() = values.clear()
        }
        val cache = SchoolCache(store)
        fun row(room: String) = org.json.JSONArray().put(JSONObject().put("Uid", "1").put("Tantargy", JSONObject().put("Nev", "Matek"))
            .put("KezdetIdopont", "2026-11-28T08:00:00").put("VegIdopont", "2026-11-28T08:45:00").put("TeremNeve", room))
        cache.replaceLessons(day, day.plusDays(1), row("101"))
        assertEquals(0, cache.snapshot().getJSONArray("lessonChanges").length())
        cache.replaceLessons(day, day.plusDays(1), row("202"))
        assertEquals(1, cache.snapshot().getJSONArray("lessonChanges").length())
        cache.replaceLessons(day, day.plusDays(1), row("202"))
        assertEquals(1, cache.snapshot().getJSONArray("lessonChanges").length())
    }
    @Test fun monthlyRecapUsesMonthBoundariesWeightsAndNoInventedCompletion() {
        val grades = listOf(grade("old", 2, "2026-10-31"), grade("a", 2, "2026-11-01"), grade("b", 5, "2026-11-30", 200),
            grade("future", 1, "2026-12-01"), grade("text", null, "2026-11-03"), grade("zero", 1, "2026-11-05", 0))
        val study = StudyState(entries = listOf(StudyEntry("a", "Terv", date = "2026-11-01", done = true), StudyEntry("b", "Nincs kész", date = "2026-11-01"), StudyEntry("c", "Más hónap", date = "2026-10-01", done = true)))
        val result = monthlyRecap(data(grades), study, YearMonth.of(2026, 11))
        assertEquals(2, result.numericCount)
        assertEquals(4.0, result.average!!, .0001)
        assertEquals(2.0, result.previousAverage!!, .0001)
        assertEquals(2.0, result.improvement!!, .0001)
        assertEquals(1, result.completed)
        assertEquals(2, result.activeDays)
        assertEquals(1, result.distribution[5])
    }
    @Test fun januaryComparesAgainstPreviousDecemberAndLeapDayBelongsToFebruary() {
        val january = monthlyRecap(data(listOf(grade("a", 3, "2025-12-31"), grade("b", 5, "2026-01-01"))), StudyState(), YearMonth.of(2026, 1))
        assertEquals(3.0, january.previousAverage!!, .0001)
        assertEquals(1, monthlyRecap(data(listOf(grade("a", 5, "2024-02-29"))), StudyState(), YearMonth.of(2024, 2)).numericCount)
    }
    @Test fun noGradesMeansNoFabricatedAverageOrBestSubject() {
        val result = monthlyRecap(data(), StudyState(), YearMonth.of(2026, 11))
        assertNull(result.average); assertNull(result.strongest); assertNull(result.improvement)
        assertEquals(0, result.numericCount)
        assertEquals(0, result.distribution.values.sum())
    }
    @Test fun recapSeparatesLateMinutesAndAbsences() {
        val result = monthlyRecap(data(absences = listOf(Absence("Matek", "2026-11-01", "Igazolt", 0), Absence("Matek", "2026-11-02", "Igazolatlan", 10), Absence("Matek", "2026-10-01", "Igazolt", 0))), StudyState(), YearMonth.of(2026, 11))
        assertEquals(1, result.absenceCount); assertEquals(10, result.lateMinutes)
    }
    @Test fun workloadExcludesCancelledLessonsAndCompletedPlansAndSeparatesWeights() {
        val tasks = listOf(SchoolTask("Matek", "Házi feladat", "Feladat", day.toString(), id = "hw"), SchoolTask("Matek", "Dolgozat", "Teszt", day.toString(), id = "exam"))
        val result = workload(data(lessons = listOf(lesson(), lesson("cancel").copy(state = "Elmaradt")), tasks = tasks),
            StudyState(entries = listOf(StudyEntry("own", "Saját", date = day.toString()), StudyEntry("done", "Kész", date = day.toString(), done = true))), day)
        assertEquals(14, result.size); assertEquals(9, result.first().score)
        assertEquals(1, result.first().lessons); assertEquals(1, result.first().personal)
        assertEquals(0, result.last().score)
    }
    @Test fun catchUpFiltersDatesAndIncludesOnlyRelatedUpcomingExams() {
        val tasks = listOf(SchoolTask("Matematika", "Dolgozat", "Jövőbeli", day.plusDays(2).toString(), id = "exam"),
            SchoolTask("Angol", "Dolgozat", "Más tárgy", day.plusDays(2).toString(), id = "other"),
            SchoolTask("Matematika", "Házi feladat", "Mai", day.toString(), id = "hw"))
        val records = catchUpItems(data(lessons = listOf(lesson(), lesson("outside", date = day.minusDays(2))), tasks = tasks), day, day, day)
        assertEquals(setOf("lesson:l1", "task:exam", "task:hw"), records.map { it.key }.toSet())
    }
    @Test fun extrasRoundTripAndMissingOldSettingsKeepDefaults() {
        val state = SchoolExtras(listOf(ExcuseRecord("2026-11-28", "2026-12-02", "Ékezetes jegyzet", "prepared", "file", "igazolás.pdf", "application/pdf", true)),
            listOf(SchoolFloor("floor", "Emelet", "plan", "image/png", listOf(RoomPin("101", .25f, .75f)))), true, setOf("change"), setOf("lesson"))
        assertEquals(state, SchoolExtras.parse(state.json()))
        assertEquals(SchoolExtras(), SchoolExtras.parse("{}"))
    }
    @Test fun movedKnownLessonIsReportedEvenWhenItsNewDayHasNoBaseline() {
        val result = timetableChanges(listOf(lesson()), listOf(lesson(date = day.plusDays(1))), { it == day }, 1)
        assertEquals(1, result.size)
        assertTrue(result.single().details.contains("Időpont"))
    }
    @Test fun catchUpUsesSubjectIdentityEvenWhenDisplayNamesDiffer() {
        val task = SchoolTask("Matek", "Dolgozat", "Témák", day.plusDays(1).toString(), id = "test", subjectId = "42")
        val result = catchUpItems(data(lessons = listOf(lesson().copy(subjectId = "42")), tasks = listOf(task)), day, day, day)
        assertTrue(result.any { it.task == task })
    }
    @Test fun excuseReminderStopsForSubmittedAndOfficiallyExcusedDates() {
        val record = ExcuseRecord(day.toString(), day.plusDays(7).toString(), remind = true)
        assertNotNull(excuseReminder(record, data()))
        assertNull(excuseReminder(record.copy(status = "submitted"), data()))
        val accepted = data(absences = listOf(Absence("Matek", day.toString(), "Igazolt", 0)))
        assertNull(excuseReminder(record, accepted))
        assertFalse(excuseStillNeeded(accepted, day.toString()))
        val mixed = accepted.copy(absences = accepted.absences + Absence("Angol", day.toString(), "Igazolatlan", 0))
        assertNotNull(excuseReminder(record, mixed))
    }

    @Test fun unrelatedLegacyLessonCannotOverrideAnExplicitSubjectIdentityMismatch() {
        val task = SchoolTask("Matematika", "Dolgozat", "Más csoport", day.plusDays(1).toString(), id = "test", subjectId = "99")
        val result = catchUpItems(data(lessons = listOf(lesson().copy(subjectId = "42"), lesson("english", subject = "Angol")), tasks = listOf(task)), day, day, day)
        assertTrue(result.none { it.task == task })
    }

}
