package hu.grafit.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import hu.grafit.data.LocalProfile
import hu.grafit.data.ProfileStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ProfileStoreTest {
    @Test fun importedPhotoAndNamePersistAndStayAccountScoped() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File(context.cacheDir, "profile-test.png")
        val bitmap = Bitmap.createBitmap(1200, 800, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.BLUE)
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        try {
            val store = ProfileStore(context)
            val photo = store.importPhoto(Uri.fromFile(source))
            val firstAccount = "test-${UUID.randomUUID()}"
            val secondAccount = "test-${UUID.randomUUID()}"
            store.save(firstAccount, LocalProfile("Árvíztűrő Tükörfúrógép", photo))
            store.save(secondAccount, LocalProfile("Másik profil"))
            val restored = ProfileStore(context).read(firstAccount)
            assertEquals("Árvíztűrő Tükörfúrógép", restored.displayName)
            val bytes = Base64.decode(restored.photo, Base64.NO_WRAP)
            val image = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            assertEquals(image.width, image.height)
            assertTrue(image.width <= 512)
            assertEquals("Másik profil", store.read(secondAccount).displayName)
            assertNull(store.read(secondAccount).photo)
        } finally {
            source.delete()
        }
    }
}
