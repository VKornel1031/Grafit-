package hu.grafit.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class LessonAssignmentsTest {
    private val lesson = Lesson("lesson", "Matematika", LocalDateTime.parse("2026-11-27T08:00:00"),
        LocalDateTime.parse("2026-11-27T08:45:00"), "101", "Tanár", "Egyenletek", "Megtartott", subjectId = "math")
    private fun task(id: String, date: String = "2026-11-27", subject: String = "Matematika", kind: String = "Házi feladat", subjectId: String? = "math") =
        SchoolTask(subject, kind, "Teljes feladatszöveg", date, id, subjectId)

    @Test fun directHomeworkUidIsAuthoritativeEvenWhenDueOnAnotherDay() {
        val homework = task("hw,full-id", "2026-12-01", "Másik névváltozat", subjectId = null)
        val result = assignmentsForLesson(lesson.copy(homeworkId = "hw,full-id"), listOf(homework))
        assertEquals(listOf(homework), result.linkedHomework)
        assertTrue(result.dueThatDay.isEmpty())
        assertFalse(result.missingLinkedHomework)
        assertTrue(assignmentsForLesson(lesson.copy(homeworkId = "hw,other-id"), listOf(homework)).missingLinkedHomework)
    }
    @Test fun dueTasksMatchTheExactDayAndSubjectAndNeverIncludeMessages() {
        val homework = task("hw")
        val test = task("test", kind = "Dolgozat")
        val unrelated = listOf(task("other-day", "2026-11-28"), task("other-id", subjectId = "other"),
            task("notice", kind = "Faliújság"), task("note", kind = "Feljegyzés"))
        assertEquals(listOf(homework, test), assignmentsForLesson(lesson, listOf(homework, test) + unrelated).dueThatDay)
    }
    @Test fun sameDayLinkedHomeworkIsNotShownTwiceAndHolidaysHaveNoAssignments() {
        val homework = task("hw")
        val result = assignmentsForLesson(lesson.copy(homeworkId = "hw"), listOf(homework, homework))
        assertEquals(listOf(homework), result.linkedHomework)
        assertTrue(result.dueThatDay.isEmpty())
        val holiday = assignmentsForLesson(lesson.copy(hasSubject = false), listOf(homework))
        assertTrue(holiday.linkedHomework.isEmpty() && holiday.dueThatDay.isEmpty())
    }
    @Test fun legacyTasksUseNormalisedNamesWithoutFuzzySubjectMatching() {
        val matching = task("hw", subject = "  MATEMATIKA\u00a0", subjectId = null)
        val unrelated = task("different", subject = "Matematikai gyakorlat", subjectId = null)
        assertEquals(listOf(matching), assignmentsForLesson(lesson, listOf(matching, unrelated)).dueThatDay)
    }
    @Test fun missingHomeworkAndEmptySchedulesAreExplicit() {
        val missing = assignmentsForLesson(lesson.copy(homeworkId = "missing"), emptyList())
        assertTrue(missing.missingLinkedHomework)
        assertFalse(assignmentsForLesson(lesson, emptyList()).missingLinkedHomework)
    }
    @Test fun rawApiIdsAuthorAndAssignmentDateSurviveCacheRoundTrip() {
        val raw = JSONObject("""{"Uid":"hw","Tantargy":{"Uid":"math","Nev":"Matematika"},"Szoveg":"Feladat","HataridoDatuma":"2026-12-01T08:00:00","FeladasDatuma":"2026-11-27T08:00:00","RogzitoTanarNeve":"Tanár"}""")
        val homework = SchoolTask.homework(raw)
        assertEquals(homework, SchoolTask.parse(homework.json()))
        assertEquals("hw", homework.id)
        assertEquals("math", homework.subjectId)
        assertEquals("Tanár", homework.teacher)
        assertEquals("2026-11-27", homework.assignedDate)
        val old = SchoolTask.parse(JSONObject("""{"subject":"Régi","kind":"Házi feladat","text":"Régi feladat","date":"2026-11-27"}"""))
        assertEquals("", old.id)
        assertNull(old.subjectId)
    }
    @Test fun lessonUidAndMessageHtmlFallbackAreRetained() {
        val parsed = Lesson.parse(JSONObject("""{"Uid":"lesson","Nev":"Matematika","Tantargy":{"Nev":"Matematika"},"KezdetIdopont":"2026-11-27T08:00:00","VegIdopont":"2026-11-27T08:45:00","HaziFeladatUid":"hw"}"""))
        assertEquals("hw", parsed.homeworkId)
        val message = SchoolTask.notice(JSONObject("""{"Uid":"message","Cim":"Iskolai hír","RogzitoNeve":"Titkárság","Tartalom":"<p>Teljes szöveg</p>","ErvenyessegKezdete":"2026-11-27"}"""))
        assertEquals("Titkárság", message.teacher)
        assertEquals("<p>Teljes szöveg</p>", message.text)
        assertEquals(message, SchoolTask.parse(message.json()))
    }
}
