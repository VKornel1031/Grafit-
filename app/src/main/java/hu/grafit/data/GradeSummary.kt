package hu.grafit.data

import java.math.BigDecimal
import java.math.RoundingMode

/** Each subject has equal weight, regardless of how many assessments it has. */
fun annualAverage(grades: List<Grade>): Double? {
    val averages = grades.groupBy { it.subject }.values.mapNotNull(::average)
    return averages.takeIf { it.isNotEmpty() }?.average()
}

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
