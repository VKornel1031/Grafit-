package hu.grafit.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SubjectGroupingTest {
    private fun mark(name: String, value: Int, id: String? = null, weight: Int = 100) =
        Grade("$name-$value", name, "$value", value, weight, "2026-11-20", "", "",
            "EvkoziJegyErtekeles", subjectId = id)

    @Test fun spacesCaseAndInvisibleUnicodeDoNotCreateExtraRows() {
        val data = SchoolData.demo().copy(
            subjects = listOf(SchoolSubject("Történelem")),
            grades = listOf(mark("TÖRTÉNELEM\u00A0", 5), mark("\u200BTörte\u0301nelem\uFEFF", 3)),
            lessons = SchoolData.demo().lessons.take(1).map { it.copy(subject = " Történelem ") })
        assertEquals(listOf("Történelem"), data.gradeSubjects())
        assertEquals(2, data.gradeSummaries().single().grades.size)
        assertEquals(4.0, data.annualAverage()!!, 0.0)
    }

    @Test fun repeatedWhitespaceAndNonBreakingSpacesHaveOneSubjectAverage() {
        val data = SchoolData.demo().copy(subjects = emptyList(), lessons = emptyList(),
            grades = listOf(mark("Angol  nyelv", 5), mark(" angol\u202Fnyelv ", 1)))
        val summary = data.gradeSummaries().single()
        assertEquals("Angol nyelv", summary.name)
        assertEquals(3.0, average(summary.grades)!!, 0.0)
    }

    @Test fun cachedKretaIdsLinkDifferentNamesAcrossAllThreeSources() {
        val data = SchoolData.parse(JSONObject("""{
            "student":{"Nev":"Teszt"},
            "subjects":[{"tantargyId":123,"tantargyNev":"Digitális kultúra","tipusId":0}],
            "grades":[{"Uid":"grade","Tantargy":{"Uid":"123,Tantargy","Nev":"Informatika"},
                "SzamErtek":5,"SulySzazalekErteke":100,"ErtekFajta":{"Nev":"Osztalyzat"}}],
            "lessons":[{"Uid":"lesson","Tantargy":{"Uid":"123","Nev":"IKT"},
                "KezdetIdopont":"2026-11-20T08:00:00","VegIdopont":"2026-11-20T08:45:00"}]
        }"""))
        val summary = data.gradeSummaries().single()
        assertEquals("Digitális kultúra", summary.name)
        assertEquals("Informatika", summary.grades.single().subject)
        assertEquals(5.0, data.annualAverage()!!, 0.0)
    }

    @Test fun linkedAliasesBringInMarksWithoutIdsAndCountOnlyOnceAnnually() {
        val data = SchoolData.demo().copy(lessons = emptyList(),
            subjects = listOf(SchoolSubject("Digitális kultúra", "123"), SchoolSubject("Informatika", "123")),
            grades = listOf(mark("Digitális kultúra", 5, weight = 200), mark("Informatika", 2), mark("Fizika", 1)))
        assertEquals(2, data.gradeSummaries().size)
        assertEquals(2, data.gradeSummaries().first().grades.size)
        assertEquals(2.5, data.annualAverage()!!, 0.0)
    }

    @Test fun gradeIdsAlsoUnifyAliasesWhenTheSubjectCatalogueIsUnavailable() {
        val grades = listOf(mark("Digitális kultúra", 5, "123,Tantargy", 200),
            mark("Informatika", 2, "123"), mark("Fizika", 1, "456"))
        val data = SchoolData.demo().copy(subjects = emptyList(), lessons = emptyList(), grades = grades)
        assertEquals(2, data.gradeSummaries().size)
        assertEquals(3, data.gradeSummaries().sumOf { it.grades.size })
        assertEquals(2.5, annualAverage(grades)!!, 0.0)
        assertEquals(annualAverage(grades), data.annualAverage())
    }

    @Test fun similarButDistinctSubjectsAndHungarianAccentsStayDistinct() {
        val names = listOf("Magyar nyelv", "Magyar irodalom", "Matematika", "Emelt matematika", "Ö", "Ő")
        val data = SchoolData.demo().copy(subjects = emptyList(), lessons = emptyList(),
            grades = names.mapIndexed { index, name -> mark(name, 4, "${index + 1}") })
        assertEquals(names.toSet(), data.gradeSubjects().toSet())
    }

    @Test fun identicalNamesMergeButMissingAndZeroIdsDoNotMergeUnrelatedSubjects() {
        val data = SchoolData.demo().copy(lessons = emptyList(), grades = emptyList(),
            subjects = listOf(SchoolSubject("Matematika", "1"), SchoolSubject("matematika", "2"),
                SchoolSubject("Fizika", "0"), SchoolSubject("Kémia", "0"), SchoolSubject("Biológia")))
        assertEquals(listOf("Matematika", "Fizika", "Kémia", "Biológia"), data.gradeSubjects())
    }
}
