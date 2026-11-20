package hu.grafit.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

private const val maximumApkSize = 200L * 1024 * 1024
data class AppRelease(val version: String, val notes: String, val assetUrl: String, val size: Long, val sha256: String?)
data class ApkIdentity(val packageName: String, val versionCode: Long, val signers: Set<String>)

fun validateUpdate(installed: ApkIdentity, candidate: ApkIdentity) {
    require(candidate.packageName == installed.packageName) { "A letöltött APK nem a Grafit alkalmazáshoz tartozik." }
    require(candidate.versionCode > installed.versionCode) { "A letöltött APK nem újabb a telepített változatnál." }
    require(installed.signers.isNotEmpty() && installed.signers == candidate.signers) {
        "Az APK aláírása eltér a telepített alkalmazásétól. Frissítéshez azonos kiadási kulccsal aláírt APK szükséges."
    }
}

fun isNewerVersion(candidate: String, installed: String): Boolean {
    fun parts(value: String): List<Int>? {
        val m = Regex("v?([0-9]+)\\.([0-9]+)(?:\\.([0-9]+))?").matchEntire(value.trim()) ?: return null
        return (1..3).map { if (m.groupValues[it].isEmpty()) 0 else m.groupValues[it].toIntOrNull() ?: return null }
    }
    val a = parts(candidate) ?: return false
    val b = parts(installed) ?: return false
    for (i in a.indices) if (a[i] != b[i]) return a[i] > b[i]
    return false
}

fun isGrafitReleaseAsset(url: String): Boolean = url.toHttpUrlOrNull()?.let {
    it.scheme == "https" && it.host == "github.com" && it.username.isEmpty() && it.password.isEmpty() &&
        it.encodedPath.startsWith("/VKornel1031/Grafit-/releases/download/") && it.encodedPath.endsWith(".apk")
} == true

class AppUpdates(
    private val http: OkHttpClient = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS).followSslRedirects(false).build(),
    private val latestUrl: String = "https://api.github.com/repos/VKornel1031/Grafit-/releases/latest",
    private val allowedAsset: (String) -> Boolean = ::isGrafitReleaseAsset
) {
    fun latest(installedVersion: String): AppRelease? = http.newCall(Request.Builder().url(latestUrl)
        .header("Accept", "application/vnd.github+json").header("User-Agent", "Grafit-Android-Updater").build()).execute().use { response ->
        if (response.code == 404) return null
        if (!response.isSuccessful) throw IOException("A GitHub frissítési adatai most nem érhetők el (${response.code}).")
        val release = JSONObject(response.body?.string() ?: throw IOException("Üres GitHub-válasz."))
        val version = release.text("tag_name")
        if (release.optBoolean("draft") || release.optBoolean("prerelease") || !isNewerVersion(version, installedVersion)) return null
        val assets = release.optJSONArray("assets")?.objects().orEmpty().filter {
            it.text("name").endsWith(".apk", ignoreCase = true) && !it.text("name").contains("androidTest", ignoreCase = true) &&
                !it.text("name").contains("unsigned", ignoreCase = true) && allowedAsset(it.text("browser_download_url")) &&
                it.optLong("size") in 1..maximumApkSize
        }
        val asset = assets.firstOrNull { it.text("name").contains("release", ignoreCase = true) }
            ?: assets.firstOrNull { !it.text("name").contains("debug", ignoreCase = true) }
            ?: assets.firstOrNull() ?: return null
        val digest = asset.text("digest").takeIf { it.matches(Regex("sha256:[0-9a-fA-F]{64}")) }?.substringAfter(':')
        AppRelease(version, release.text("body").take(4000), asset.getString("browser_download_url"), asset.getLong("size"), digest)
    }

    suspend fun download(release: AppRelease, directory: File, progress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        require(allowedAsset(release.assetUrl)) { "Érvénytelen frissítési hivatkozás." }
        require(release.size in 1..maximumApkSize) { "Érvénytelen APK-méret." }
        check(directory.isDirectory || directory.mkdirs()) { "Nem hozható létre a frissítés helyi mappája." }
        val temporary = File(directory, "download.part")
        val target = File(directory, "grafit-update.apk")
        try {
            http.newCall(Request.Builder().url(release.assetUrl).build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Az APK letöltése sikertelen (${response.code}).")
                val body = response.body ?: throw IOException("Üres APK-válasz.")
                val digest = MessageDigest.getInstance("SHA-256")
                var received = 0L
                var lastProgress = 0L
                body.byteStream().use { input -> temporary.outputStream().use { output ->
                    val bytes = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(bytes)
                        if (count == -1) break
                        received += count
                        if (received > release.size || received > maximumApkSize) throw IOException("Az APK mérete eltér a kiadás adataitól.")
                        output.write(bytes, 0, count)
                        digest.update(bytes, 0, count)
                        val time = System.nanoTime()
                        if (time - lastProgress > 200_000_000) { progress(received.toFloat() / release.size); lastProgress = time }
                    }
                } }
                if (received != release.size) throw IOException("Az APK letöltése hiányos. Próbáld újra.")
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                if (release.sha256 != null && !release.sha256.equals(actual, ignoreCase = true)) throw IOException("Az APK ellenőrzőösszege nem egyezik.")
            }
            currentCoroutineContext().ensureActive()
            check(!target.exists() || target.delete()) { "A korábbi frissítési fájl nem törölhető." }
            check(temporary.renameTo(target)) { "Az APK mentése sikertelen." }
            progress(1f)
            target
        } finally { temporary.delete() }
    }
}
