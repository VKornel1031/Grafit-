package hu.grafit.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class SchoolDayTest {
    private val now = LocalDateTime.of(2026, 11, 20, 12, 0)
    private fun lesson(start: Int, end: Int, offset: Long = 0) = Lesson("$offset-$start", "Matematika",
        now.toLocalDate().plusDays(offset).atTime(start, 0), now.toLocalDate().plusDays(offset).atTime(end, 0), "", "", "", "Megtartott")

    @Test fun tomorrowDoesNotReplaceTheEndOfToday() {
        val result = schoolDay(listOf(lesson(8, 9), lesson(8, 9, 1)), now)
        assertEquals(SchoolDayStatus.Finished, result.status)
        assertNull(result.next)
        assertEquals(1, result.lessons.size)
    }
    @Test fun noClassesTodayEvenWhenOtherDaysHaveLessons() {
        assertEquals(SchoolDayStatus.NoClasses, schoolDay(listOf(lesson(8, 9, -1), lesson(8, 9, 1)), now).status)
        assertEquals(SchoolDayStatus.NoClasses, schoolDay(emptyList(), now).status)
    }
    @Test fun holidaysAndCancelledLessonsAreNotRemainingClasses() {
        val rows = listOf(lesson(0, 23).copy(hasSubject = false), lesson(13, 14).copy(state = "Elmaradt"))
        assertEquals(SchoolDayStatus.NoClasses, schoolDay(rows, now).status)
    }
    @Test fun exactStartAndEndAndBreaksHaveCorrectStatuses() {
        assertEquals(SchoolDayStatus.Ongoing, schoolDay(listOf(lesson(12, 13)), now).status)
        assertEquals(SchoolDayStatus.Finished, schoolDay(listOf(lesson(11, 12)), now).status)
        assertEquals(SchoolDayStatus.Upcoming, schoolDay(listOf(lesson(8, 9), lesson(13, 14)), now).status)
    }
    @Test fun unsortedLessonsStillSelectTheCurrentClass() {
        val result = schoolDay(listOf(lesson(14, 15), lesson(11, 13), lesson(8, 9)), now)
        assertEquals(SchoolDayStatus.Ongoing, result.status)
        assertEquals(11, result.next!!.start.hour)
    }
}
