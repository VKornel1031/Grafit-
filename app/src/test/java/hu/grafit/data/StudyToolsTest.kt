package hu.grafit.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class StudyToolsTest {
    private fun grade(n: Int, weight: Int = 100, date: String = "2026-11-28") = Grade("g$n", "Matematika", "$n", n, weight, date, "Egyenletek", "Tanár", "EvkoziJegyErtekeles")
    private val data = SchoolData("Teszt", "Iskola", listOf(grade(2)), listOf(Lesson("lesson", "Matematika", LocalDateTime.parse("2026-11-28T08:00"), LocalDateTime.parse("2026-11-28T08:45"), "101", "Tanár", "Másodfokú egyenletek", "Megtartott")), emptyList(),
        tasks = listOf(SchoolTask("Matematika", "Dolgozat", "Egyenletek", "2026-12-01", id = "exam")))
    @Test fun simulatorNeverChangesRealGradesAndRespectsWeights() {
        val result = simulatedSubjects(data, listOf(SimulatedGrade("Matematika", 5, 200)))
        assertEquals(4.0, average(result.single().grades)!!, .0001)
        assertEquals(2.0, average(data.grades)!!, .0001)
        assertEquals(1, data.grades.size)
    }
    @Test fun targetCalculatorHandlesImpossibleAlreadyReachedEmptyAndWeightedTargets() {
        assertEquals(2, gradesNeeded(listOf(grade(2)), 4.0, 5, 100))
        assertEquals(1, gradesNeeded(listOf(grade(2)), 4.0, 5, 200))
        assertNull(gradesNeeded(listOf(grade(2)), 4.0, 4, 100))
        assertEquals(0, gradesNeeded(listOf(grade(5)), 4.0, 2, 100))
        assertEquals(1, gradesNeeded(emptyList(), 5.0, 5, 100))
        assertNull(gradesNeeded(emptyList(), 5.0, 4, 100))
    }
    @Test fun plannerNotesSimulationAndCalendarConsentRoundTrip() {
        val state = StudyState(listOf(StudyEntry("p", "Tanulás", "Matematika", "2026-12-01", "16:00", 45, "study", notes = "Jegyzet", examKey = "exam", reminder = 15)),
            listOf(SimulatedGrade("Matematika", 5, 200)), mapOf("exam" to "Fontos képletek"), 42, true, true, calendarKinds = setOf("Dolgozat", "Tanulás"), goals = mapOf("Matematika" to 4.5))
        assertEquals(state, StudyState.parse(state.json()))
        assertEquals(StudyState(), StudyState.parse(null))
    }
    @Test fun calendarUsesStableKeysAndExcludesCancelledAndCompletedItems() {
        val state = StudyState(listOf(StudyEntry("p", "Tanulás", date = "2026-12-01", time = "16:00", minutes = 45),
            StudyEntry("done", "Kész", date = "2026-12-01", done = true)))
        val items = calendarItems(data.copy(lessons = data.lessons + data.lessons.single().copy(id = "cancelled", state = "Elmaradt")), state)
        assertEquals(3, items.size)
        assertEquals(items.map { it.key }, calendarItems(data, state).map { it.key })
        assertTrue(items.single { it.key.startsWith("school:") }.allDay)
        assertEquals(45, java.time.Duration.between(items.single { it.key == "personal:p" }.start, items.single { it.key == "personal:p" }.end).toMinutes().toInt())
    }
    @Test fun searchIsOfflineAccentInsensitiveAndCoversNotesTeachersAndHomework() {
        val state = StudyState(examNotes = mapOf("exam" to "Képletek összefoglalója"))
        assertTrue(searchSchool(data, state, "masodfoku tanar").any { it.lesson != null })
        assertTrue(searchSchool(data, state, "kepletek").any { it.kind == "Dolgozatjegyzet" })
        assertTrue(searchSchool(data, state, "2026-12-01 matematika").any { it.task != null })
        assertTrue(searchSchool(data, state, "   ").isEmpty())
        assertTrue(searchSchool(data, state, "Matematika: 2", hideGradeValues = true).isEmpty())
    }
    @Test fun monthlyAveragesUseNumericWeightedGradesAndMonthOrder() {
        val months = monthlyGradeStats(listOf(grade(5, 100, "2026-10-01"), grade(2), grade(5, 200)))
        assertEquals(listOf("2026-10", "2026-11"), months.map { it.month })
        assertEquals(4.0, months.last().average!!, .0001)
    }
    @Test fun calendarDeletionRequiresCoverageAndNeverRemovesUnknownKinds() {
        val day = java.time.LocalDate.of(2026, 12, 1)
        val coverage = listOf(day to day.plusDays(7))
        assertTrue(canRemoveExport("lesson", day, false, coverage))
        assertFalse(canRemoveExport("lesson", day.plusDays(7), true, coverage))
        assertFalse(canRemoveExport("school", day, false, coverage))
        assertTrue(canRemoveExport("personal", day, false, emptyList()))
        assertFalse(canRemoveExport("other-app", day, true, coverage))
    }
    @Test fun utcHomeworkDeadlineUsesTheHungarianCalendarDay() {
        val task = SchoolTask.homework(org.json.JSONObject("""{"Uid":"hw","HataridoDatuma":"2026-11-27T23:30:00Z","FeladasDatuma":"2026-11-26T23:30:00Z"}"""))
        assertEquals("2026-11-28", task.date)
        assertEquals("2026-11-27", task.assignedDate)
    }
}
