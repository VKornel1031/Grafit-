package hu.grafit.data

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AppUpdatesTest {
    @Test fun versionsAreComparedNumericallyAndOnlyStableTagsAreAccepted() {
        assertTrue(isNewerVersion("v0.10.0", "0.9.9")); assertTrue(isNewerVersion("1.0", "0.9.9"))
        assertFalse(isNewerVersion("0.5.0", "0.5.0")); assertFalse(isNewerVersion("0.4.9", "0.5.0"))
        assertFalse(isNewerVersion("v1.0.0-beta", "0.5.0")); assertFalse(isNewerVersion("invalid", "0.5.0"))
    }
    @Test fun assetLinksMustBelongToThisReposHttpsReleases() {
        assertTrue(isGrafitReleaseAsset("https://github.com/VKornel1031/Grafit-/releases/download/v1.0.1/grafit.apk"))
        assertFalse(isGrafitReleaseAsset("http://github.com/VKornel1031/Grafit-/releases/download/v1.0.1/grafit.apk"))
        assertFalse(isGrafitReleaseAsset("https://github.com/other/project/releases/download/v1/grafit.apk"))
        assertFalse(isGrafitReleaseAsset("https://evil.example/grafit.apk"))
    }
    @Test fun browserLinksAreFixedToTheOfficialReleasePage() {
        assertEquals("https://github.com/VKornel1031/Grafit-/releases/tag/v1.0.1", releasePageUrl("v1.0.1"))
        listOf("https://evil.example", "../../other", "1.0.1?redirect=evil", "1.0.1/path", "1.0.1\n").forEach {
            assertTrue(runCatching { releasePageUrl(it) }.isFailure)
        }
    }
    @Test fun releaseWithoutApkOrWithoutNewVersionDoesNotPrompt() {
        MockWebServer().use { server ->
            server.start()
            val service = AppUpdates(OkHttpClient(), server.url("/latest").toString())
            server.enqueue(MockResponse().setResponseCode(404))
            assertNull(service.latest("1.0.1"))
            listOf("""{"tag_name":"v1.0.2","assets":[]}""", """{"tag_name":"v1.0.1","assets":[]}""",
                """{"tag_name":"v1.0.2","prerelease":true,"assets":[]}""").forEach {
                server.enqueue(MockResponse().setBody(it)); assertNull(service.latest("1.0.1"))
            }
        }
    }
    @Test fun metadataCheckDoesNotDownloadOrInstallAnApk() {
        MockWebServer().use { server ->
            server.start()
            val url = "https://github.com/VKornel1031/Grafit-/releases/download/v1.0.2/grafit-1.0.2.apk"
            val json = JSONObject().put("tag_name", "v1.0.2").put("body", "Release notes").put("assets", JSONArray().put(
                JSONObject().put("name", "grafit-1.0.2.apk").put("browser_download_url", url).put("size", 100)))
            server.enqueue(MockResponse().setBody(json.toString()))
            val release = AppUpdates(OkHttpClient(), server.url("/latest").toString()).latest("1.0.1")!!
            assertEquals("v1.0.2", release.version)
            assertEquals(url, release.assetUrl)
            assertEquals(1, server.requestCount)
            assertEquals("GET", server.takeRequest().method)
        }
    }
    @Test fun foreignAndUnsignedAssetsCannotTriggerUpdatePrompts() {
        MockWebServer().use { server ->
            server.start()
            val assets = JSONArray().put(JSONObject().put("name", "app.apk").put("size", 100)
                .put("browser_download_url", "https://evil.example/app.apk"))
                .put(JSONObject().put("name", "app-unsigned.apk").put("size", 100)
                    .put("browser_download_url", "https://github.com/VKornel1031/Grafit-/releases/download/v1.0.2/app-unsigned.apk"))
            server.enqueue(MockResponse().setBody(JSONObject().put("tag_name", "v1.0.2").put("assets", assets).toString()))
            assertNull(AppUpdates(OkHttpClient(), server.url("/latest").toString()).latest("1.0.1"))
        }
    }

    @Test fun checksOtherPublishedReleasesAndAcceptsVersionFromTitle() {
        MockWebServer().use { server ->
            server.start()
            val asset = JSONObject().put("name", "grafit-1.0.1.apk").put("size", 100)
                .put("browser_download_url", "https://github.com/VKornel1031/Grafit-/releases/download/stable-patch/grafit.apk")
            val releases = JSONArray().put(JSONObject().put("tag_name", "v1.0.0"))
                .put(JSONObject().put("tag_name", "stable-patch").put("name", "Grafit v1.0.1").put("assets", JSONArray().put(asset)))
            server.enqueue(MockResponse().setBody(releases.toString()))
            val result = AppUpdates(OkHttpClient(), server.url("/releases").toString()).latest("1.0.0")!!
            assertEquals("stable-patch", result.version)
            assertEquals("https://github.com/VKornel1031/Grafit-/releases/tag/stable-patch", releasePageUrl(result.version))
        }
    }
    @Test fun missingApkIsReportedAndDoesNotHideAnotherInstallableRelease() {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setBody("""[{"tag_name":"v1.0.1","assets":[]}]"""))
            val service = AppUpdates(OkHttpClient(), server.url("/releases").toString())
            assertNull(service.latest("1.0.0"))
            assertEquals("1.0.1", service.missingApkVersion)
        }
    }
    @Test fun foreignBuildMetadataCannotTriggerSameVersionUpdates() {
        MockWebServer().use { server ->
            server.start()
            val prefix = "https://github.com/VKornel1031/Grafit-/releases/download/v1.0.0/"
            val assets = JSONArray().put(JSONObject().put("name", "release-manifest.json").put("browser_download_url", server.url("/manifest").toString()))
                .put(JSONObject().put("name", "grafit.apk").put("size", 100).put("browser_download_url", prefix + "grafit.apk"))
            // Production metadata URLs must remain on this repository; foreign metadata is ignored.
            server.enqueue(MockResponse().setBody(JSONObject().put("tag_name", "v1.0.0").put("assets", assets).toString()))
            assertNull(AppUpdates(OkHttpClient(), server.url("/releases").toString()).latest("1.0.0", 20))
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun buildMetadataDetectsReplacementAndRejectsOlderBuilds() {
        MockWebServer().use { server ->
            server.start()
            val assets = JSONArray().put(JSONObject().put("name", "signing-migration.json").put("browser_download_url", server.url("/manifest").toString()))
                .put(JSONObject().put("name", "grafit.apk").put("size", 100).put("browser_download_url",
                    "https://github.com/VKornel1031/Grafit-/releases/download/v1.0.0/grafit.apk"))
            val service = AppUpdates(OkHttpClient(), server.url("/releases").toString(), allowedMetadata = { it.startsWith(server.url("/").toString()) })
            for (code in listOf(21, 19)) {
                server.enqueue(MockResponse().setBody(JSONObject().put("tag_name", "v1.0.0").put("assets", assets).toString()))
                server.enqueue(MockResponse().setBody("""{"version":"1.0.0","versionCode":$code}"""))
                val result = service.latest("1.0.0", 20)
                if (code > 20) assertNotNull(result) else assertNull(result)
            }
        }
    }
}
