package hu.grafit.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class LessonCountdownTest {
    private fun lesson(start: String, end: String, state: String = "Megtartott", subject: Boolean = true) =
        Lesson("slot", "Matematika", LocalDateTime.parse(start), LocalDateTime.parse(end), "101", "Tanár", "Téma", state, subject)
    private fun instant(time: String) = LocalDateTime.parse(time).atZone(schoolZone).toInstant()
    private val normal = lesson("2026-11-21T08:00:00", "2026-11-21T08:45:00")
    @Test fun countdownAppearsOnlyDuringTheLessonAndEndsAtTheExactBoundary() {
        assertNull(ongoingLesson(listOf(normal), instant("2026-11-21T07:59:59")))
        assertEquals(normal, ongoingLesson(listOf(normal), instant("2026-11-21T08:00:00")))
        assertEquals(normal, ongoingLesson(listOf(normal), instant("2026-11-21T08:44:59")))
        assertNull(ongoingLesson(listOf(normal), instant("2026-11-21T08:45:00")))
        assertEquals(instant("2026-11-21T08:00:00"), nextLessonTransition(listOf(normal), instant("2026-11-21T07:00:00")))
        assertEquals(instant("2026-11-21T08:45:00"), nextLessonTransition(listOf(normal), instant("2026-11-21T08:00:00")))
        assertNull(nextLessonTransition(listOf(normal), instant("2026-11-21T08:45:00")))
    }
    @Test fun holidaysCancelledLessonsAndBreaksNeverShowALiveClass() {
        val cancelled = normal.copy(state = "Elmaradt")
        val holiday = normal.copy(hasSubject = false)
        val afternoon = lesson("2026-11-21T09:00:00", "2026-11-21T09:45:00")
        assertNull(ongoingLesson(listOf(cancelled, holiday), instant("2026-11-21T08:20:00")))
        assertNull(nextLessonTransition(listOf(cancelled, holiday), instant("2026-11-21T07:00:00")))
        assertNull(ongoingLesson(listOf(normal, afternoon), instant("2026-11-21T08:50:00")))
        assertEquals(instant("2026-11-21T09:00:00"), nextLessonTransition(listOf(normal, afternoon), instant("2026-11-21T08:50:00")))
    }
    @Test fun overlappingLessonsUseDeterministicChronologicalOrder() {
        val late = lesson("2026-11-21T08:10:00", "2026-11-21T08:55:00")
        assertEquals(normal, ongoingLesson(listOf(late, normal), instant("2026-11-21T08:20:00")))
    }
}
