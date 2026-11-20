package hu.grafit.data

import java.time.LocalDateTime

enum class SchoolDayStatus { NoClasses, Finished, Upcoming, Ongoing }
data class SchoolDay(val status: SchoolDayStatus, val next: Lesson?, val lessons: List<Lesson>)

fun schoolDay(lessons: List<Lesson>, now: LocalDateTime): SchoolDay {
    val today = lessons.filter { it.hasSubject && it.state != "Elmaradt" && it.start.toLocalDate() == now.toLocalDate() }
        .sortedBy { it.start }
    val ongoing = today.firstOrNull { it.start <= now && it.end > now }
    val upcoming = today.firstOrNull { it.start > now }
    return SchoolDay(when {
        today.isEmpty() -> SchoolDayStatus.NoClasses
        ongoing != null -> SchoolDayStatus.Ongoing
        upcoming != null -> SchoolDayStatus.Upcoming
        else -> SchoolDayStatus.Finished
    }, ongoing ?: upcoming, today)
}
