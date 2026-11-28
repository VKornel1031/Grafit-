package hu.grafit.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class ToolAttachment(val id: String, val name: String, val mime: String)
class SchoolExtrasStore(private val context: Context) {
    private val state = SecureStore(context, "grafit_school_extras")
    fun read(account: String) = SchoolExtras.parse(state.read(accountHash(account)))
    fun save(account: String, value: SchoolExtras) = state.write(accountHash(account), value.json())
    companion object { private val keyLock = Any(); const val MAX_FILE_BYTES = 8 * 1024 * 1024 }
    private val key: SecretKey get() = synchronized(keyLock) {
        val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keys.getKey("grafit_attachments", null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("grafit_attachments", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun file(account: String, id: String): File {
        require(id.matches(Regex("[0-9a-f-]{36}")))
        return File(File(context.filesDir, "school_attachments/${accountHash(account)}").apply { mkdirs() }, "$id.bin")
    }
    fun import(account: String, uri: Uri): ToolAttachment {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri).orEmpty()
        require(mime == "application/pdf" || mime in setOf("image/jpeg", "image/png", "image/webp", "image/gif", "image/heic", "image/heif")) { "Válassz PDF-et vagy támogatott képet." }
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }.orEmpty().substringAfterLast('/').substringAfterLast('\\').take(100).ifBlank { "Dokumentum" }
        val bytes = resolver.openInputStream(uri)?.use { it.readBytesBounded() } ?: error("A fájl nem olvasható.")
        require(bytes.isNotEmpty()) { "A fájl üres." }
        if (mime.startsWith("image/")) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "A kép nem olvasható." }
        } else require(bytes.take(5).toByteArray().toString(Charsets.US_ASCII) == "%PDF-") { "A fájl nem érvényes PDF." }
        val id = UUID.randomUUID().toString()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
        val atomic = AtomicFile(file(account, id)); val output = atomic.startWrite()
        try { output.write(cipher.iv); output.write(cipher.doFinal(bytes)); atomic.finishWrite(output) }
        catch (e: Exception) { atomic.failWrite(output); throw e }
        return ToolAttachment(id, name, mime)
    }
    private fun java.io.InputStream.readBytesBounded(): ByteArray {
        val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
        while (true) { val count = read(buffer); if (count < 0) break
            require(output.size() + count <= MAX_FILE_BYTES) { "Egy melléklet legfeljebb 8 MB lehet." }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
    fun bytes(account: String, id: String): ByteArray {
        val input = file(account, id)
        require(input.length() in 29..(MAX_FILE_BYTES + 64).toLong())
        val raw = input.readBytes()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, raw.copyOfRange(0, 12))) }
        return cipher.doFinal(raw.copyOfRange(12, raw.size))
    }
    fun image(account: String, id: String): Bitmap {
        val bytes = bytes(account, id)
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        require(options.outWidth > 0 && options.outHeight > 0)
        var sample = 1
        while (maxOf(options.outWidth, options.outHeight) / sample > 2048) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: error("A kép nem olvasható.")
    }
    fun remove(account: String, id: String) { if (id.isNotBlank()) AtomicFile(file(account, id)).delete() }
    fun export(account: String, id: String, mime: String): File {
        val folder = File(context.cacheDir, "school_exports").apply { mkdirs() }
        folder.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 60 * 60 * 1000 }?.forEach(File::delete)
        val extension = if (mime == "application/pdf") "pdf" else when(mime) { "image/png" -> "png"; "image/webp" -> "webp"; "image/gif" -> "gif"; "image/heic", "image/heif" -> "heic"; else -> "jpg" }
        return File(folder, "${UUID.randomUUID()}.$extension").apply { writeBytes(bytes(account, id)) }
    }
}
