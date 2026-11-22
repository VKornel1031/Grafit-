package hu.grafit.data

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import org.json.JSONArray
import org.json.JSONTokener
import java.io.IOException
import java.util.concurrent.TimeUnit

private const val maximumApkSize = 200L * 1024 * 1024
data class AppRelease(val version: String, val notes: String, val assetUrl: String, val size: Long, val sha256: String?)
fun releasePageUrl(version: String): String {
    require(Regex("[A-Za-z0-9][A-Za-z0-9._ -]{0,100}").matches(version) && ".." !in version) { "Érvénytelen kiadási verzió." }
    return "https://github.com/VKornel1031/Grafit-/releases/tag/" + java.net.URLEncoder.encode(version, "UTF-8").replace("+", "%20")
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
        it.encodedPath.startsWith("/VKornel1031/Grafit-/releases/download/") && it.encodedPath.endsWith(".apk", ignoreCase = true)
} == true

class AppUpdates(
    private val http: OkHttpClient = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS).followSslRedirects(false).build(),
    private val latestUrl: String = "https://api.github.com/repos/VKornel1031/Grafit-/releases?per_page=100",
    private val allowedAsset: (String) -> Boolean = ::isGrafitReleaseAsset,
    private val allowedMetadata: (String) -> Boolean = { url ->
        url.toHttpUrlOrNull()?.let { it.scheme == "https" && it.host == "github.com" && it.username.isEmpty() && it.password.isEmpty() &&
            it.encodedPath.startsWith("/VKornel1031/Grafit-/releases/download/") } == true
    }
) {
    var noPublicReleases: Boolean = false; private set
    var missingApkVersion: String? = null; private set

    fun latest(installedVersion: String, installedCode: Long? = null): AppRelease? {
        missingApkVersion = null
        noPublicReleases = false
        return http.newCall(Request.Builder().url(latestUrl)
            .header("Accept", "application/vnd.github+json").header("Cache-Control", "no-cache")
            .header("User-Agent", "Grafit-Android-Updater").build()).execute().use { response ->
            if (response.code == 404) { noPublicReleases = true; return null }
            if (!response.isSuccessful) throw IOException("A GitHub frissítési adatai most nem érhetők el (${response.code}).")
            val payload = JSONTokener(response.body?.string() ?: throw IOException("Üres GitHub-válasz.")).nextValue()
            val releases = when (payload) {
                is JSONArray -> payload.objects()
                is JSONObject -> listOf(payload)
                else -> throw IOException("Érvénytelen GitHub-válasz.")
            }
            // The list includes stable releases that GitHub has not designated as 'latest'.
            for (release in releases) {
                if (release.optBoolean("draft") || release.optBoolean("prerelease")) continue
                val tag = release.text("tag_name")
                if (runCatching { releasePageUrl(tag) }.isFailure) continue
                val version = versionInRelease(tag) ?: versionInRelease(release.text("name")) ?: continue
                val newerName = isNewerVersion(version, installedVersion)
                val sameName = !newerName && !isNewerVersion(installedVersion, version)
                if (!newerName && !sameName) continue
                val allAssets = release.optJSONArray("assets")?.objects().orEmpty()
                val metadata = if (installedCode != null) allAssets.firstOrNull {
                    it.text("name") in setOf("release-manifest.json", "signing-migration.json")
                } else null
                val build = metadata?.text("browser_download_url")?.let { url ->
                    if (!allowedMetadata(url)) null
                    else runCatching {
                        http.newCall(Request.Builder().url(url).header("Cache-Control", "no-cache").build()).execute().use {
                            if (!it.isSuccessful || (it.body?.contentLength() ?: 0) > 65_536) null
                            else it.body?.source()?.let { source ->
                                val buffer = okio.Buffer()
                                while (buffer.size <= 65_536 && source.read(buffer, minOf(8192L, 65_537 - buffer.size)) != -1L) { }
                                if (buffer.size > 65_536) null else JSONObject(buffer.readUtf8())
                            }
                        }
                    }.getOrNull()
                }
                val buildCode = build?.takeIf { versionInRelease(it.text("version")) == version }?.optLong("versionCode", -1)?.takeIf { it > 0 }
                if (buildCode != null && installedCode != null && buildCode <= installedCode) continue
                if (!newerName && (buildCode == null || installedCode == null || buildCode <= installedCode)) continue
                val assets = release.optJSONArray("assets")?.objects().orEmpty().filter {
                    val name = it.text("name").lowercase()
                    name.endsWith(".apk") && listOf("androidtest", "unsigned", "debug").none(name::contains) &&
                        allowedAsset(it.text("browser_download_url")) && it.optLong("size") in 1..maximumApkSize
                }
                val asset = assets.firstOrNull { it.text("name").contains("release", ignoreCase = true) }
                    ?: assets.firstOrNull()
                if (asset == null) { missingApkVersion = version; continue }
                val digest = asset.text("digest").takeIf { it.matches(Regex("sha256:[0-9a-fA-F]{64}")) }?.substringAfter(':')
                return AppRelease(tag, release.text("body").take(4000), asset.getString("browser_download_url"), asset.getLong("size"), digest)
            }
            null
        }
    }
}

internal fun versionInRelease(label: String): String? {
    val match = Regex("(?:^|[^A-Za-z0-9])v?([0-9]+\\.[0-9]+(?:\\.[0-9]+)?)(?=$|[^A-Za-z0-9.-])", RegexOption.IGNORE_CASE).find(label.trim())
    return match?.groupValues?.get(1)
}
