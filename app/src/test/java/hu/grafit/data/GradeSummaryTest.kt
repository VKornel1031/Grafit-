package hu.grafit.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GradeSummaryTest {
    private fun mark(subject: String, value: Int?, weight: Int = 100, valueType: String = "Osztalyzat") =
        Grade("$subject-$value", subject, value?.toString() ?: "Szöveges", value, weight,
            "2026-11-20", "", "", "EvkoziJegyErtekeles", valueType)

    @Test fun subjectsKeepAssessmentWeightsButHaveEqualWeightInAnnualAverage() {
        val grades = listOf(mark("A", 5, 200), mark("A", 2), mark("B", 2))
        assertEquals(4.0, average(grades.filter { it.subject == "A" })!!, 0.00001)
        assertEquals(3.0, annualAverage(grades)!!, 0.00001)
        // The existing home-page calculation remains weighted across all marks.
        assertEquals(3.5, average(grades)!!, 0.00001)
    }

    @Test fun missingTextAndZeroWeightGradesDoNotCreateAnAverage() {
        assertNull(annualAverage(emptyList()))
        assertNull(annualAverage(listOf(mark("A", null), mark("A", 5, 0))))
        val grades = listOf(mark("A", 4), mark("B", 1, 0), mark("C", 1, valueType = "Szoveges"))
        assertEquals(4.0, annualAverage(grades)!!, 0.00001)
    }

    @Test fun everyScholarshipBandMatchesTheSuppliedTechnikumTable() {
        listOf(2.0 to 8_000, 2.99 to 8_000, 3.0 to 25_000, 3.99 to 25_000,
            4.0 to 42_000, 4.49 to 42_000, 4.5 to 59_000, 5.0 to 59_000).forEach { (average, amount) ->
            assertEquals("average=$average", amount, technikumScholarshipEstimate(average))
        }
    }

    @Test fun noEstimateIsPresentedForUnknownOrNonQualifyingAverages() {
        listOf(null, 1.0, 1.99, 0.0, -1.0, 5.01, Double.NaN, Double.POSITIVE_INFINITY).forEach {
            assertNull("average=$it", technikumScholarshipEstimate(it))
        }
    }

    @Test fun displayedHundredthsAndScholarshipBoundariesAgree() {
        assertEquals(4.5, roundedGradeAverage(4.495), 0.0)
        assertEquals(59_000, technikumScholarshipEstimate(4.495))
        assertEquals(42_000, technikumScholarshipEstimate(4.4949))
        assertEquals(42_000, technikumScholarshipEstimate(3.995))
        assertEquals(25_000, technikumScholarshipEstimate(2.995))
    }

    @Test fun annualAverageDoesNotRoundEachSubjectBeforeCombiningThem() {
        val grades = listOf(mark("A", 4, 249), mark("A", 5, 751), mark("B", 2))
        assertEquals(3.3755, annualAverage(grades)!!, 0.000001)
    }

    @Test fun cachedSubjectNamesIncludeSubjectsWithoutGradesAndExcludeRemovedRows() {
        val data = SchoolData.parse(JSONObject("""{
            "student":{"Nev":"Teszt"},
            "subjects":[{"tantargyNev":" Fizika ","tipusId":0},
                {"tantargyNev":"Fizika","tipusId":0},
                {"tantargyNev":"Törölt","tipusId":1}, {"tantargyNev":""}]
        }"""))
        assertEquals(listOf("Fizika"), data.subjectNames)
        assertTrue(data.grades.isEmpty())
    }

    @Test fun olderCachesRemainReadableWithoutSubjectNames() {
        assertTrue(SchoolData.parse(JSONObject("""{"student":{"Nev":"Teszt"}}""")).subjectNames.isEmpty())
    }

    @Test fun holidaysAndNonTeachingDaysNeverBecomeSubjectsFromTimetableNames() {
        val data = SchoolData.parse(JSONObject("""{
            "student":{"Nev":"Teszt"},
            "subjects":[{"tantargyNev":"Fizika","tipusId":0}],
            "lessons":[
                {"Uid":"holiday","Nev":"Ünnepnap","Tantargy":null,
                 "KezdetIdopont":"2026-10-23T00:00:00","VegIdopont":"2026-10-24T00:00:00"},
                {"Uid":"day-off","Nev":"Munkaszüneti nap",
                 "KezdetIdopont":"2026-11-01T00:00:00","VegIdopont":"2026-11-02T00:00:00"},
                {"Uid":"break","Nev":"Őszi szünet","Tantargy":{},
                 "KezdetIdopont":"2026-10-26T00:00:00","VegIdopont":"2026-10-27T00:00:00"},
                {"Uid":"lesson","Nev":"Angol óra","Tantargy":{"Nev":"Angol nyelv"},
                 "KezdetIdopont":"2026-11-20T08:00:00","VegIdopont":"2026-11-20T08:45:00"}]
        }"""))
        assertEquals(setOf("Fizika", "Angol nyelv"), data.gradeSubjects().toSet())
        // Holidays remain visible on their proper timetable dates.
        assertEquals(4, data.lessons.size)
        assertEquals("Ünnepnap", data.lessons.first().subject)
    }

    @Test fun realSubjectsWithNoGradesAndCancelledLessonsRemainInTheSubjectList() {
        val data = SchoolData.demo().copy(subjects = listOf(SchoolSubject("Fizika")),  grades = listOf(mark("Csak jegy", 5)),
            lessons = SchoolData.demo().lessons.take(1).map { it.copy(subject = "Elmaradt óra tantárgya", state = "Elmaradt") })
        assertEquals(setOf("Fizika", "Csak jegy", "Elmaradt óra tantárgya"), data.gradeSubjects().toSet())
    }
}
