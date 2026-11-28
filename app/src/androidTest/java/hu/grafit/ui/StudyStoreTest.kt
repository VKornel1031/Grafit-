package hu.grafit.ui

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import hu.grafit.data.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class StudyStoreTest {
    @Test fun plansThemesAndNotificationsRemainAccountSpecificAfterReopening() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val a = "test:${UUID.randomUUID()}"
        val b = "test:${UUID.randomUUID()}"
        val store = StudyStore(context)
        val state = StudyState(entries = listOf(StudyEntry("entry", "Saját tanulás", date = "2026-12-01", notes = "Saját jegyzet")), examNotes = mapOf("exam" to "Képletek"))
        try {
            store.save(a, state)
            store.saveAppearance(a, Appearance(mode = "dark").json())
            store.saveNotifications(a, NotificationOptions(enabled = true).json())
            val reopened = StudyStore(context)
            assertEquals(state, reopened.read(a))
            assertEquals(StudyState(), reopened.read(b))
            assertNull(reopened.appearance(b))
            assertNull(reopened.notifications(b))
            assertTrue(NotificationOptions.parse(reopened.notifications(a)).enabled)
            assertEquals("dark", Appearance.parse(org.json.JSONObject(reopened.appearance(a)!!)).mode)
        } finally {
            store.save(a, StudyState()); store.save(b, StudyState())
        }
    }
}
