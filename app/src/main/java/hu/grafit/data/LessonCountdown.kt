package hu.grafit.data

import java.time.Instant

/** Half-open class periods: cancelled lessons and calendar-only entries are never countdowns. */
fun ongoingLesson(lessons: List<Lesson>, now: Instant): Lesson? = lessons.asSequence()
    .filter { it.hasSubject && it.state != "Elmaradt" }
    .filter { it.start.atZone(schoolZone).toInstant() <= now && it.end.atZone(schoolZone).toInstant() > now }
    .minByOrNull { it.end }

fun nextLessonTransition(lessons: List<Lesson>, now: Instant): Instant? = lessons.asSequence()
    .filter { it.hasSubject && it.state != "Elmaradt" }
    .flatMap { sequenceOf(it.start.atZone(schoolZone).toInstant(), it.end.atZone(schoolZone).toInstant()) }
    .filter { it > now }.minOrNull()
