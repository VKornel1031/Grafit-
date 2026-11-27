package hu.grafit.data

import java.text.Normalizer
import java.util.Locale

private fun subjectNameKey(name: String): String = Normalizer.normalize(name, Normalizer.Form.NFC)
    .replace(Regex("[\u200B\uFEFF\u2060\u00AD]"), "").trim().replace(Regex("[\\s\\p{Z}]+"), " ").lowercase(Locale.ROOT)
private fun subjectKey(id: String?): String? = id?.substringBefore(',')?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() && it != "0" }

data class LessonAssignments(val linkedHomework: List<SchoolTask>, val dueThatDay: List<SchoolTask>, val missingLinkedHomework: Boolean)

/** An explicit KRÉTA homework link takes precedence; due-day matches are labelled separately. */
fun assignmentsForLesson(lesson: Lesson, tasks: List<SchoolTask>): LessonAssignments {
    if (!lesson.hasSubject) return LessonAssignments(emptyList(), emptyList(), false)
    val linked = tasks.filter { it.kind == "Házi feladat" && lesson.homeworkId != null && it.id == lesson.homeworkId }
    val due = tasks.filter { task ->
        val lessonSubject = subjectKey(lesson.subjectId)
        val taskSubject = subjectKey(task.subjectId)
        val sameSubject = if (lessonSubject != null && taskSubject != null) lessonSubject == taskSubject
            else subjectNameKey(lesson.subject).let { it.isNotEmpty() && it == subjectNameKey(task.subject) }
        task.kind in setOf("Házi feladat", "Dolgozat") && task.date == lesson.start.toLocalDate().toString() &&
            sameSubject && task !in linked
    }
    return LessonAssignments(linked.distinct(), due.distinct(), lesson.homeworkId != null && linked.isEmpty())
}
