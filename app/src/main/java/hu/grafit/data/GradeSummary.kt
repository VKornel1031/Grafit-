package hu.grafit.data

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.Normalizer
import java.util.Locale

data class SubjectGrades(val name: String, val grades: List<Grade>)

private val subjectWhitespace = Regex("[\\s\\p{Z}]+")
private val invisibleSubjectCharacters = Regex("[\u200B\uFEFF\u2060\u00AD]")

private fun cleanSubjectName(name: String): String = Normalizer.normalize(name, Normalizer.Form.NFC)
    .replace(invisibleSubjectCharacters, "").replace(subjectWhitespace, " ").trim()

private fun subjectId(id: String?): String? = id?.substringBefore(',')?.trim()?.lowercase(Locale.ROOT)
    ?.takeIf { it.isNotEmpty() && it != "0" }

private data class SubjectEntry(val name: String, val id: String?, val grade: Grade? = null)

/** Resolve aliases using explicit identity or exact normalised names, never fuzzy similarity. */
private fun groupSubjects(entries: List<SubjectEntry>): List<SubjectGrades> {
    val candidates = entries.map { it.copy(name = cleanSubjectName(it.name), id = subjectId(it.id)) }
        .filter { it.name.isNotEmpty() }
    val parents = IntArray(candidates.size) { it }
    fun root(index: Int): Int {
        var result = index
        while (parents[result] != result) {
            parents[result] = parents[parents[result]]
            result = parents[result]
        }
        return result
    }
    fun merge(first: Int, second: Int) {
        val a = root(first)
        val b = root(second)
        parents[maxOf(a, b)] = minOf(a, b)
    }
    val names = mutableMapOf<String, Int>()
    val ids = mutableMapOf<String, Int>()
    candidates.forEachIndexed { index, entry ->
        names.putIfAbsent(entry.name.lowercase(Locale.ROOT), index)?.let { merge(index, it) }
        entry.id?.let { id -> ids.putIfAbsent(id, index)?.let { merge(index, it) } }
    }
    return candidates.indices.groupBy(::root).values.map { indices ->
        SubjectGrades(candidates[indices.first()].name, indices.mapNotNull { candidates[it].grade })
    }
}

/** DKT's subject names take precedence; timetable events without subjects stay out. */
fun SchoolData.gradeSummaries(): List<SubjectGrades> = groupSubjects(
    subjects.map { SubjectEntry(it.name, it.id) } +
        grades.map { SubjectEntry(it.subject, it.subjectId, it) } +
        lessons.filter { it.hasSubject }.map { SubjectEntry(it.subject, it.subjectId) })

fun SchoolData.gradeSubjects(): List<String> = gradeSummaries().map { it.name }

private fun annualAverageOfSubjects(subjects: List<SubjectGrades>): Double? =
    subjects.mapNotNull { average(it.grades) }.takeIf { it.isNotEmpty() }?.average()

fun SchoolData.annualAverage(): Double? = annualAverageOfSubjects(gradeSummaries())

/** Each subject has equal weight, regardless of how many assessments it has. */
fun annualAverage(grades: List<Grade>): Double? = annualAverageOfSubjects(
    groupSubjects(grades.map { SubjectEntry(it.subject, it.subjectId, it) }))

/** Use the same two-decimal value in the UI and when choosing a scholarship band. */
fun roundedGradeAverage(value: Double): Double =
    BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).toDouble()

/**
 * Monthly specialised-training estimate; not an eligibility decision.
 * Source: https://szfszc.hu/kepzesek/osztondijak (Szkr. 170–171. §).
 * The supplied 8% minimum of 8,000 Ft implies a 100,000 Ft monthly basis.
 * The actual award uses the previous school year's final grades. Foundation
 * training in a technikum instead has a fixed 8,000 Ft award for eligible students.
 */
fun technikumScholarshipEstimate(average: Double?): Int? {
    if (average == null || !average.isFinite() || average !in 2.0..5.0) return null
    return when {
        roundedGradeAverage(average) >= 4.5 -> 59_000
        roundedGradeAverage(average) >= 4.0 -> 42_000
        roundedGradeAverage(average) >= 3.0 -> 25_000
        else -> 8_000
    }
}
