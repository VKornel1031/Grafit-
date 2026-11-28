package hu.grafit.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import hu.grafit.data.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SchoolExtrasFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun monthlyRecapIsAvailableImmediatelyAndCanBePausedAndRevisited() {
        val vm = GrafitViewModel(compose.activity.application, checkUpdatesOnStart = false)
        compose.runOnUiThread { vm.startDemo() }
        compose.setContent { GrafitApp(vm) }
        compose.onNodeWithContentDescription("Profil megnyitása").performClick()
        compose.onNode(hasScrollAction() and hasAnyDescendant(hasText("Beállítások"))).performTouchInput { swipeUp() }
        compose.onNodeWithText("Eszközök / Egyéb").performScrollTo().performClick()
        compose.onNodeWithText("A hónapod").performClick()
        compose.onNodeWithText("Összefoglaló megnyitása").assertIsEnabled()
        compose.onNodeWithText("Összefoglaló megnyitása").performClick()
        if (vm.appearance.hideGrades) compose.onNodeWithText("Felfedés").performClick()
        compose.onNodeWithContentDescription("Szünet").performClick()
        compose.onNodeWithText("Indítás").performClick()
        compose.onNodeWithText("A havi súlyozott átlagod").assertIsDisplayed()
        compose.onNodeWithText("Következő").performClick()
        compose.onNodeWithText("Következő").performClick()
        compose.onNodeWithText("Így álltak össze a jegyeid").assertIsDisplayed()
        compose.onNodeWithContentDescription("Összefoglaló bezárása").performClick()
        compose.onNodeWithText("Összefoglaló megnyitása").assertIsDisplayed()
    }
    @Test fun accountMetadataAndEncryptedAttachmentsRemainSeparate() {
        val app = compose.activity.application
        val store = SchoolExtrasStore(app)
        val a = "test-a:${UUID.randomUUID()}"; val b = "test-b:${UUID.randomUUID()}"
        val folder = File(app.cacheDir, "school_exports").apply { mkdirs() }
        val input = File(folder, "test-${UUID.randomUUID()}.png")
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        var file: ToolAttachment? = null
        try {
            input.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val uri = FileProvider.getUriForFile(app, "${app.packageName}.files", input)
            val imported = store.import(a, uri); file = imported
            store.save(a, SchoolExtras(excuses = listOf(ExcuseRecord("2026-11-28", attachment = imported.id, fileName = imported.name, mime = imported.mime))))
            assertEquals(imported.id, store.read(a).excuses.single().attachment)
            assertTrue(store.read(b).excuses.isEmpty())
            val encrypted = File(app.filesDir, "school_attachments/${accountHash(a)}/${imported.id}.bin").readBytes()
            assertFalse(encrypted.contentEquals(input.readBytes()))
            assertArrayEquals(input.readBytes(), store.bytes(a, imported.id))
            assertTrue(runCatching { store.bytes(b, imported.id) }.isFailure)
            val decoded = store.image(a, imported.id)
            assertEquals(16, decoded.width); decoded.recycle()
        } finally {
            file?.let { store.remove(a, it.id) }
            store.save(a, SchoolExtras()); store.save(b, SchoolExtras())
            input.delete(); bitmap.recycle()
        }
    }
}
