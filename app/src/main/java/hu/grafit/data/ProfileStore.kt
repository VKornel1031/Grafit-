package hu.grafit.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.util.Base64
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/** Local appearance overrides, isolated by KRÉTA account and kept across sign-ins. */
data class LocalProfile(val displayName: String = "", val photo: String? = null) {
    fun json(): String = JSONObject().put("displayName", displayName).put("photo", photo).toString()
    companion object {
        fun parse(value: String): LocalProfile {
            val json = JSONObject(value)
            return LocalProfile(json.text("displayName"), json.text("photo").takeIf { it.isNotEmpty() })
        }
    }
}

class ProfileStore(private val context: Context) {
    private val storage = SecureStore(context, "grafit_profiles")
    private fun key(account: String): String = MessageDigest.getInstance("SHA-256")
        .digest(account.toByteArray()).joinToString("") { "%02x".format(it) }

    fun read(account: String): LocalProfile = storage.read(key(account))?.let {
        runCatching { LocalProfile.parse(it) }.getOrNull()
    } ?: LocalProfile()

    fun save(account: String, profile: LocalProfile) = storage.write(key(account), profile.json())

    /** Read while the picker's temporary permission is valid, then retain a bounded copy. */
    fun importPhoto(uri: Uri): String {
        val bitmap = if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetSampleSize((maxOf(info.size.width, info.size.height) / 512).coerceAtLeast(1))
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Ez a kép nem olvasható." }
            val options = BitmapFactory.Options().apply {
                inSampleSize = Integer.highestOneBit((maxOf(bounds.outWidth, bounds.outHeight) / 512).coerceAtLeast(1))
            }
            val decoded = context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, options) }
                ?: throw IllegalArgumentException("Ez a kép nem olvasható.")
            val orientation = context.contentResolver.openInputStream(uri).use {
                if (it == null) ExifInterface.ORIENTATION_NORMAL else
                    ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
            val transform = Matrix().apply {
                when (orientation) {
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                    ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(270f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
                }
            }
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, transform, true)
        }
        val side = minOf(bitmap.width, bitmap.height)
        val square = Bitmap.createBitmap(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side)
        val thumbnail = Bitmap.createScaledBitmap(square, minOf(side, 512), minOf(side, 512), true)
        return ByteArrayOutputStream().use { output ->
            check(thumbnail.compress(Bitmap.CompressFormat.PNG, 100, output))
            Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        }
    }
}
