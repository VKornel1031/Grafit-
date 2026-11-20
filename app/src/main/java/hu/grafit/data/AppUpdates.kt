package hu.grafit.data

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

private const val maximumApkSize = 200L * 1024 * 1024
data class AppRelease(val version: String, val notes: String, val assetUrl: String, val size: Long, val sha256: String?)
fun releasePageUrl(version: String): String {
    require(Regex("v?([0-9]+)\\.([0-9]+)(?:\\.([0-9]+))?").matches(version)) { "Érvénytelen kiadási verzió." }
    return "https://github.com/VKornel1031/Grafit-/releases/tag/$version"
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

}
