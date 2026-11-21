package hu.grafit.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

private class EventStore : StringStore {
    private val entries = mutableMapOf<String, String>()
    override fun read(name: String) = entries[name]
    override fun write(name: String, value: String) { entries[name] = value }
    override fun clear() = entries.clear()
}

class NotificationRulesTest {
    private fun grade(id: String, value: Int) = JSONObject().put("Uid", id).put("SzamErtek", value)
        .put("Tantargy", JSONObject().put("Nev", "Matematika"))
        .put("ErtekFajta", JSONObject().put("Nev", "Osztalyzat"))
    private val enabled = NotificationOptions(enabled = true)

    @Test fun firstSyncIsSilentAndNewIdsNotifyExactlyOnceAcrossRestarts() {
        val store = EventStore()
        val tracker = NotificationTracker(store)
        assertTrue(tracker.update("grades", JSONArray().put(grade("old", 4)), enabled).isEmpty())
        val rows = JSONArray().put(grade("old", 4)).put(grade("new", 5))
        assertEquals(listOf("new"), tracker.update("grades", rows, enabled).map { it.id })
        assertTrue(NotificationTracker(store).update("grades", rows, enabled).isEmpty())
        tracker.update("grades", JSONArray(), enabled)
        assertTrue(tracker.update("grades", rows, enabled).isEmpty())
    }
    @Test fun badGradeUsesOnlyItsOwnTemplateAndThreshold() {
        val tracker = NotificationTracker(EventStore())
        tracker.update("grades", JSONArray(), enabled)
        val rows = JSONArray().put(grade("bad", 2)).put(grade("good", 4))
        val events = tracker.update("grades", rows, enabled)
        assertEquals(listOf(SchoolEventKind.BadGrade, SchoolEventKind.Grade), events.map { it.kind })
        assertEquals("Matematika", events.first().values["tantargy"])
        val disabledBad = enabled.copy(categories = setOf(SchoolEventKind.Grade))
        assertTrue(tracker.update("grades", JSONArray().put(grade("another-bad", 1)), disabledBad).isEmpty())
    }
    @Test fun disabledNotificationsStillAdvanceBaselineWithoutBacklog() {
        val tracker = NotificationTracker(EventStore())
        tracker.update("grades", JSONArray(), enabled)
        val rows = JSONArray().put(grade("new", 5))
        assertTrue(tracker.update("grades", rows, NotificationOptions()).isEmpty())
        assertTrue(tracker.update("grades", rows, enabled).isEmpty())
    }
    @Test fun homeworkAndTestsHaveSeparateBaselinesAndRealUids() {
        val tracker = NotificationTracker(EventStore())
        tracker.update("homework", JSONArray(), enabled)
        tracker.update("tests", JSONArray(), enabled)
        val row = JSONObject().put("Uid", "same-id").put("Tantargy", JSONObject().put("Nev", "Fizika"))
            .put("Szoveg", "42. oldal").put("HataridoDatuma", "2026-11-23T08:00:00")
            .put("Temaja", "Erők").put("Datum", "2026-11-24T08:00:00")
        val homework = tracker.update("homework", JSONArray().put(row), enabled).single()
        val test = tracker.update("tests", JSONArray().put(row), enabled).single()
        assertEquals(SchoolEventKind.Homework, homework.kind)
        assertEquals("42. oldal", homework.values["feladat"])
        assertEquals("2026-11-23", homework.values["datum"])
        assertEquals(SchoolEventKind.Test, test.kind)
        assertEquals("Erők", test.values["feladat"])
    }
    @Test fun accountLogoutClearsTheBaselineSoOtherAccountDoesNotNotifyOldRecords() {
        val store = EventStore()
        val tracker = NotificationTracker(store)
        tracker.update("grades", JSONArray().put(grade("a", 5)), enabled)
        store.clear()
        assertTrue(tracker.update("grades", JSONArray().put(grade("b", 1)), enabled).isEmpty())
    }
    @Test fun messageSubstitutionIsLiteralAndNeverRecursive() {
        assertEquals("Matematika: 5, {ismeretlen}", renderNotification("{tantargy}: {jegy}, {ismeretlen}",
            mapOf("tantargy" to "Matematika", "jegy" to "5")))
        assertEquals("{jegy}", renderNotification("{tantargy}", mapOf("tantargy" to "{jegy}", "jegy" to "1")))
    }
    @Test fun allCustomizationPersistsAndInvalidValuesAreClamped() {
        val configured = enabled.copy(categories = setOf(SchoolEventKind.BadGrade), badGradeThreshold = 3,
            titles = mapOf(SchoolEventKind.BadGrade to "Saját cím"), messages = mapOf(SchoolEventKind.BadGrade to "{jegy} · {tantargy}"),
            liveLesson = true, liveProgress = false, liveColor = 2, liveTitle = "Óra", liveMessage = "{terem}")
        assertEquals(configured, NotificationOptions.parse(configured.json()))
        assertEquals(4, NotificationOptions.parse(JSONObject().put("badGradeThreshold", 99).toString()).badGradeThreshold)
    }
}
