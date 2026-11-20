package hu.grafit.data

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.security.MessageDigest

class AppUpdatesTest {
    @Test fun versionsAreComparedNumericallyAndOnlyStableTagsAreAccepted() {
        assertTrue(isNewerVersion("v0.10.0", "0.9.9")); assertTrue(isNewerVersion("1.0", "0.9.9"))
        assertFalse(isNewerVersion("0.5.0", "0.5.0")); assertFalse(isNewerVersion("0.4.9", "0.5.0"))
        assertFalse(isNewerVersion("v1.0.0-beta", "0.5.0")); assertFalse(isNewerVersion("invalid", "0.5.0"))
    }
    @Test fun assetLinksMustBelongToThisReposHttpsReleases() {
        assertTrue(isGrafitReleaseAsset("https://github.com/VKornel1031/Grafit-/releases/download/v0.6.0/grafit.apk"))
        assertFalse(isGrafitReleaseAsset("http://github.com/VKornel1031/Grafit-/releases/download/v0.6.0/grafit.apk"))
        assertFalse(isGrafitReleaseAsset("https://github.com/other/project/releases/download/v1/grafit.apk"))
        assertFalse(isGrafitReleaseAsset("https://evil.example/grafit.apk"))
    }
    @Test fun installPolicyRequiresSamePackageSignatureAndHigherVersionCode() {
        val installed = ApkIdentity("hu.grafit", 8, setOf("certificate"))
        validateUpdate(installed, installed.copy(versionCode = 9))
        listOf(installed, installed.copy(versionCode = 7), installed.copy(packageName = "other", versionCode = 9),
            installed.copy(signers = setOf("other"), versionCode = 9), installed.copy(signers = emptySet(), versionCode = 9)).forEach {
            assertTrue(runCatching { validateUpdate(installed, it) }.isFailure)
        }
    }
    @Test fun releaseWithoutApkOrWithoutNewVersionDoesNotPrompt() {
        MockWebServer().use { server ->
            server.start()
            val service = AppUpdates(OkHttpClient(), server.url("/latest").toString())
            server.enqueue(MockResponse().setResponseCode(404))
            assertNull(service.latest("0.5.0"))
            server.enqueue(MockResponse().setBody("""{"tag_name":"v0.6.0","assets":[]}"""))
            assertNull(service.latest("0.5.0"))
            server.enqueue(MockResponse().setBody("""{"tag_name":"v0.5.0","assets":[]}"""))
            assertNull(service.latest("0.5.0"))
            server.enqueue(MockResponse().setBody("""{"tag_name":"v0.6.0","prerelease":true,"assets":[]}"""))
            assertNull(service.latest("0.5.0"))
        }
    }
    @Test fun publishedApkDownloadsAtomicallyAndVerifiesItsDigest() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val directory = Files.createTempDirectory("grafit-update-test").toFile()
            try {
                val bytes = "fixture-apk-payload".toByteArray()
                val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
                val url = server.url("/grafit-release.apk").toString()
                val service = AppUpdates(OkHttpClient(), server.url("/latest").toString()) { it == url }
                val json = JSONObject().put("tag_name", "v0.6.0").put("body", "Release notes").put("assets", JSONArray().put(
                    JSONObject().put("name", "grafit-release.apk").put("browser_download_url", url).put("size", bytes.size).put("digest", "sha256:$digest")))
                server.enqueue(MockResponse().setBody(json.toString()))
                val release = service.latest("0.5.0")!!
                assertEquals("v0.6.0", release.version)
                server.enqueue(MockResponse().setBody(String(bytes)))
                val result = service.download(release, directory) { }
                assertArrayEquals(bytes, result.readBytes())
                assertFalse(directory.resolve("download.part").exists())
                server.enqueue(MockResponse().setBody(String(bytes)))
                assertTrue(runCatching { service.download(release.copy(sha256 = "0".repeat(64)), directory) { } }.isFailure)
                assertFalse(directory.resolve("download.part").exists())
            } finally { directory.deleteRecursively() }
        }
    }
    @Test fun incompleteDownloadsAreNeverPublishedAsApks() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val directory = Files.createTempDirectory("grafit-update-short").toFile()
            try {
                val url = server.url("/app.apk").toString()
                val service = AppUpdates(OkHttpClient(), server.url("/latest").toString()) { it == url }
                server.enqueue(MockResponse().setBody("short"))
                assertTrue(runCatching { service.download(AppRelease("0.6.0", "", url, 100, null), directory) { } }.isFailure)
                assertTrue(directory.listFiles().orEmpty().isEmpty())
            } finally { directory.deleteRecursively() }
        }
    }
}
