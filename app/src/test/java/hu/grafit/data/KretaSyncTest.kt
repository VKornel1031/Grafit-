package hu.grafit.data

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

private class MemoryStore : StringStore {
    private val values = mutableMapOf<String, String>()
    @Synchronized override fun read(name: String) = values[name]
    @Synchronized override fun write(name: String, value: String) { values[name] = value }
    @Synchronized override fun clear() = values.clear()
}

class KretaSyncTest {
    private val server = MockWebServer()
    private val store = MemoryStore()
    private val requests = CopyOnWriteArrayList<RecordedRequest>()
    private val tokenCalls = AtomicInteger()
    private lateinit var api: KretaApi
    private val date = LocalDate.of(2026, 11, 19)
    @Volatile private var rejectOldToken = false
    @Volatile private var rejectRefresh = false
    @Volatile private var failGrades = false
    @Volatile private var emptyGrades = false
    private val idToken: String get() {
        val payload = JSONObject().put("kreta:institute_code", "school-a").put("kreta:institute_user_id", "123")
        return "header." + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toString().toByteArray()) + ".signature"
    }
    @Before fun setup() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests.add(request)
                val path = request.requestUrl!!.encodedPath
                if (path == "/token") {
                    tokenCalls.incrementAndGet()
                    val refreshing = request.body.clone().readUtf8().contains("refresh_token")
                    if (refreshing && rejectRefresh) return MockResponse().setResponseCode(400)
                    return json(JSONObject().put("id_token", idToken).put("access_token", if (refreshing) "new" else "old")
                        .put("refresh_token", "refresh").put("expires_in", 3600).toString())
                }
                if (rejectOldToken && request.getHeader("Authorization") == "Bearer old") return MockResponse().setResponseCode(401)
                return when {
                    path.endsWith("TanuloAdatlap") -> json("""{"Nev":"Teszt Diák","IntezmenyNev":"Iskola"}""")
                    path.endsWith("OsztalyCsoportok") -> json("""[{"Uid":"group","Nev":"10.A"}]""")
                    path == "/dktapi/intezmenyek/munkaterek/tanulok" -> json("""[{"tantargyNev":"Fizika","tipusId":0}]""")
                    path.endsWith("Ertekelesek") -> if (failGrades) MockResponse().setResponseCode(503)
                        else json(if (emptyGrades) "[]" else """[{"Uid":"grade","Tantargy":{"Nev":"Matematika"},"SzamErtek":5}]""")
                    path.endsWith("OrarendElemek") -> {
                        val from = request.requestUrl!!.queryParameter("datumTol")!!
                        json(JSONArray().put(lesson("slot,$from", from)).toString())
                    }
                    path.endsWith("FaliujsagElemek") -> json("""[{"Uid":"notice","Cim":"Hír","TartalomText":"Szöveg"}]""")
                    else -> json("[]")
                }
            }
        }
        server.start()
        api = KretaApi(store, OkHttpClient(), "test-agent", server.url("/token").toString(),
            { server.url("/").toString().trimEnd('/') }, pause = {})
        api.login("code", "verifier")
    }
    @After fun cleanup() { server.shutdown() }
    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
    private fun lesson(uid: String, day: String) = JSONObject().put("Uid", uid).put("Nev", "Matematika")
        .put("KezdetIdopont", "${day}T08:00:00+01:00").put("VegIdopont", "${day}T08:45:00+01:00")

    @Test fun startupSyncFetchesOnlyCurrentAndSelectedWeeksAndPublishesProgress() {
        val progress = CopyOnWriteArrayList<SchoolData>()
        val result = api.load(date, progress::add)
        assertEquals("Teszt Diák", result.student)
        assertEquals(1, result.grades.size)
        assertTrue(result.lessons.isNotEmpty())
        assertTrue(progress.isNotEmpty())
        assertTrue(api.syncIssues.isEmpty())
        val ranges = requests.filter { it.requestUrl!!.encodedPath.endsWith("OrarendElemek") }
        assertTrue(ranges.size in 1..2)
        ranges.forEach {
            val from = LocalDate.parse(it.requestUrl!!.queryParameter("datumTol"))
            val to = LocalDate.parse(it.requestUrl!!.queryParameter("datumIg"))
            assertTrue(java.time.temporal.ChronoUnit.DAYS.between(from, to) in 1..14)
            assertEquals("test-agent", it.getHeader("User-Agent"))
            assertEquals("21ff6c25-d1da-4a68-a811-c881a6057463", it.getHeader("apiKey"))
        }
        assertTrue(requests.any { it.requestUrl!!.encodedPath.endsWith("HaziFeladatok") && it.requestUrl!!.queryParameter("datumTol") == "2026-09-01" })
    }
    @Test fun browsingFetchesMissingWeeksOnceIncludingEmptyWeeksAndKeepsOtherLessons() {
        api.load(today())
        val before = requireNotNull(api.cached()).lessons
        val selected = today().plusWeeks(20)
        val monday = selected.minusDays((selected.dayOfWeek.value - 1).toLong())
        val delegate = server.dispatcher
        val calls = AtomicInteger()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.requestUrl!!.encodedPath.endsWith("OrarendElemek")) {
                    calls.incrementAndGet()
                    val from = LocalDate.parse(request.requestUrl!!.queryParameter("datumTol"))
                    assertTrue(from == monday || from == today().minusDays((today().dayOfWeek.value - 1).toLong()))
                    return json("[]")
                }
                return delegate.dispatch(request)
            }
        }
        repeat(3) { api.getTimetableWeek(monday.plusDays(it.toLong())) }
        assertEquals(1, calls.get())
        assertEquals(before, requireNotNull(api.cached()).lessons)
        // Range coverage survives a new API instance (app restart).
        val restarted = KretaApi(store, OkHttpClient(), "test-agent", server.url("/token").toString(),
            { server.url("/").toString().trimEnd('/') }, pause = {})
        restarted.getTimetableWeek(selected)
        assertEquals(1, calls.get())
        // A user refresh always fetches that week again, including changes to an empty week.
        api.refreshHomeFeed(selected)
        assertEquals(3, calls.get())
    }

    @Test fun failedWeekRemainsRetryableAndDoesNotEraseCachedLessons() {
        api.load(today())
        val before = requireNotNull(api.cached()).lessons
        val selected = today().minusWeeks(25)
        val delegate = server.dispatcher
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.requestUrl!!.encodedPath.endsWith("OrarendElemek")) MockResponse().setResponseCode(503)
                else delegate.dispatch(request)
        }
        try { api.getTimetableWeek(selected); fail("Expected failure") }
        catch (e: ApiFailure) { assertEquals(503, e.status) }
        assertEquals(before, requireNotNull(api.cached()).lessons)
        server.dispatcher = delegate
        val result = api.getTimetableWeek(selected)
        assertTrue(result.lessons.size > before.size)
    }

    @Test fun timetableBadRequestIsPartialFailureAndDoesNotAskForLogin() {
        val delegate = server.dispatcher
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.requestUrl!!.encodedPath.endsWith("OrarendElemek")) MockResponse().setResponseCode(400)
                else delegate.dispatch(request)
        }
        val result = api.load(today())
        assertEquals(1, result.grades.size)
        assertEquals(listOf("Aktuális órarend"), api.syncIssues)
        assertEquals(1, tokenCalls.get())
        assertFalse(ApiFailure(400).authenticationRequired)
    }

    @Test fun timetableRateLimitHonoursServerCooldownThenRecovers() {
        api.load(today())
        val delegate = server.dispatcher
        val calls = AtomicInteger()
        val waits = CopyOnWriteArrayList<Long>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.requestUrl!!.encodedPath.endsWith("OrarendElemek") && calls.incrementAndGet() == 1) {
                    return MockResponse().setResponseCode(429).setHeader("Retry-After", "7")
                }
                return delegate.dispatch(request)
            }
        }
        val retrying = KretaApi(store, OkHttpClient(), "test-agent", server.url("/token").toString(),
            { server.url("/").toString().trimEnd('/') }, pause = { waits.add(it); Unit })
        assertTrue(retrying.getTimetableWeek(today().plusWeeks(25)).lessons.isNotEmpty())
        assertEquals(listOf(7000L), waits)
        assertEquals(2, calls.get())
    }

    @Test fun longServerCooldownDoesNotRetryEarlyOrEraseCachedData() {
        api.load(today())
        val before = requireNotNull(api.cached()).lessons
        val calls = AtomicInteger()
        val delegate = server.dispatcher
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.requestUrl!!.encodedPath.endsWith("OrarendElemek")) {
                    calls.incrementAndGet()
                    return MockResponse().setResponseCode(429).setHeader("Retry-After", "120")
                }
                return delegate.dispatch(request)
            }
        }
        try { api.getTimetableWeek(today().plusWeeks(25)); fail("Expected cooldown") }
        catch (e: ApiFailure) { assertEquals(429, e.status) }
        assertEquals(1, calls.get())
        assertEquals(before, requireNotNull(api.cached()).lessons)
    }

    @Test fun backgroundUpdatesFetchOnlyNotificationCollectionsAndPublishValidatedRows() {
        requests.clear()
        val updated = CopyOnWriteArrayList<String>()
        val background = KretaApi(store, OkHttpClient(), "test-agent", server.url("/token").toString(),
            { server.url("/").toString().trimEnd('/') }, pause = {}, onCollectionUpdated = { key, _ -> updated.add(key) })
        background.checkNotificationUpdates()
        assertEquals(listOf("grades", "homework", "tests"), updated)
        assertEquals(setOf("Ertekelesek", "HaziFeladatok", "BejelentettSzamonkeresek"), requests.map { it.requestUrl!!.encodedPath.substringAfterLast('/') }.toSet())
        assertEquals(3, requests.size)
    }

    @Test fun backgroundPartialFailureDoesNotBlockOtherNotificationsOrClearGrades() {
        api.refreshHomeFeed()
        failGrades = true
        val updated = CopyOnWriteArrayList<String>()
        val background = KretaApi(store, OkHttpClient(), "test-agent", server.url("/token").toString(),
            { server.url("/").toString().trimEnd('/') }, pause = {}, onCollectionUpdated = { key, _ -> updated.add(key) })
        try { background.checkNotificationUpdates(); fail("Expected partial failure") }
        catch (e: ApiFailure) { assertEquals(503, e.status) }
        assertEquals(listOf("homework", "tests"), updated)
        assertEquals(1, requireNotNull(background.cached()).grades.size)
    }

    @Test fun concurrent401ResponsesRotateRefreshTokenOnce() {
        rejectOldToken = true
        api.refreshHomeFeed()
        assertEquals(2, tokenCalls.get())
        assertTrue(JSONObject(store.read("session")!!).getString("access_token") == "new")
    }
    @Test fun failedDatasetRetainsCacheWhileOtherDatasetsSync() {
        api.refreshHomeFeed()
        failGrades = true
        val result = api.refreshHomeFeed()
        assertEquals(1, result.grades.size)
        assertTrue(api.syncIssues.contains("Jegyek"))
        assertTrue(result.tasks.any { it.kind == "Faliújság" })
        failGrades = false
        emptyGrades = true
        assertTrue(api.refreshHomeFeed().grades.isEmpty())
    }
    @Test fun rejectedRefreshKeepsOfflineCacheAndStopsRetrying() {
        api.refreshHomeFeed()
        val before = store.read("school")
        val session = JSONObject(store.read("session")!!).put("expires_at", 0)
        store.write("session", session.toString())
        rejectRefresh = true
        try { api.refreshHomeFeed(); fail("Expected expired session") }
        catch (e: ApiFailure) { assertEquals(400, e.status) }
        assertEquals(before, store.read("school"))
        assertEquals(2, tokenCalls.get())
    }
    @Test fun timetableReplacementPreservesAdjacentDayAndFullUids() {
        val cache = SchoolCache(store)
        cache.replaceLessons(date, date.plusDays(2), JSONArray().put(lesson("slot,first", "$date"))
            .put(lesson("slot,second", "${date.plusDays(1)}")))
        assertEquals(2, SchoolCache.array(cache.snapshot(), "lessons").length())
        cache.replaceLessons(date, date.plusDays(1), JSONArray())
        assertEquals("slot,second", SchoolCache.array(cache.snapshot(), "lessons").getJSONObject(0).getString("Uid"))
        val before = store.read("school")
        try { cache.replaceLessons(date, date.plusDays(1), JSONArray().put(JSONObject().put("Uid", "bad"))); fail("Invalid lesson") }
        catch (_: org.json.JSONException) { }
        assertEquals(before, store.read("school"))
    }
    @Test fun messagesAreIncrementalAndDoNotDuplicateIds() {
        api.refreshHomeFeed()
        api.refreshHomeFeed()
        assertEquals(1, SchoolCache.array(SchoolCache(store).snapshot(), "notices").length())
        val notices = requests.filter { it.requestUrl!!.encodedPath.endsWith("FaliujsagElemek") }
        assertEquals("2026-09-01", notices.first().requestUrl!!.queryParameter("datumTol"))
        assertEquals(today().toString(), notices.last().requestUrl!!.queryParameter("datumTol"))
    }
    @Test fun supplementalFirkaEndpointsUseInstitutionApi() {
        api.getSubjects()
        api.getClassGroupAverages("task,123")
        assertTrue(requests.any { it.requestUrl!!.encodedPath == "/dktapi/intezmenyek/munkaterek/tanulok" })
        val average = requests.last()
        assertEquals(listOf("task", "task"), average.requestUrl!!.queryParameterValues("oktatasiNevelesiFeladatUid"))
    }

    @Test fun subjectsAreIncludedInRefreshWithoutOpeningTheGradesTab() {
        val result = api.refreshHomeFeed()
        assertEquals(listOf("Fizika"), result.subjectNames)
        assertEquals(1, requests.count { it.requestUrl!!.encodedPath == "/dktapi/intezmenyek/munkaterek/tanulok" })
    }

    @Test fun manualRefreshUpdatesTheSelectedTimetablePeriod() {
        val selected = today().plusWeeks(6)
        val result = api.refreshHomeFeed(selected)
        val monday = selected.minusDays((selected.dayOfWeek.value - 1).toLong())
        assertTrue(requests.any { it.requestUrl!!.encodedPath.endsWith("OrarendElemek") &&
            it.requestUrl!!.queryParameter("datumTol") == monday.toString() })
        assertTrue(result.lessons.any { it.start.toLocalDate() == monday })
    }

    @Test fun legacyFlattenedTasksSurvivePartialCacheMigration() {
        val cache = SchoolCache(store)
        val old = SchoolTask("Hír", "Faliújság", "Régi üzenet", "2026-10-01")
        cache.update { it.put("tasks", JSONArray().put(old.json())) }
        cache.replace("homework", JSONArray())
        assertEquals(old, SchoolTask.parse(cache.snapshot().getJSONArray("tasks").getJSONObject(0)))
        cache.mergeMessages("notices", JSONArray())
        assertEquals(0, cache.snapshot().getJSONArray("tasks").length())
    }

    @Test fun reauthenticationOfSameAccountRetainsCachedSchoolData() {
        api.refreshHomeFeed()
        val before = store.read("school")
        api.login("new-code", "new-verifier")
        assertEquals(before, store.read("school"))
        assertEquals("school-a:123", api.profileKey)
    }
    @Test fun transientRefreshFailuresUseFirkaBackoffAndPersistRecovery() {
        val waits = mutableListOf<Long>()
        val session = JSONObject(store.read("session")!!).put("expires_at", 0)
        store.write("session", session.toString())
        val delegate = server.dispatcher
        val failedOnce = java.util.concurrent.atomic.AtomicBoolean(false)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.requestUrl!!.encodedPath == "/token" && !failedOnce.getAndSet(true)) {
                    return MockResponse().setResponseCode(503)
                }
                return delegate.dispatch(request)
            }
        }
        val retrying = KretaApi(store, OkHttpClient(), "test-agent", server.url("/token").toString(),
            { server.url("/").toString().trimEnd('/') }, pause = waits::add)
        retrying.refreshHomeFeed()
        assertEquals(listOf(1000L), waits)
        assertEquals("new", JSONObject(store.read("session")!!).getString("access_token"))
    }

    @Test fun studentEndpointAcceptsObjectOrSingleItemListLikeFirka() {
        val delegate = server.dispatcher
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.requestUrl!!.encodedPath.endsWith("TanuloAdatlap")) {
                    return json("""[{"Nev":"Listás Diák","IntezmenyNev":"Iskola"}]""")
                }
                return delegate.dispatch(request)
            }
        }
        assertEquals("Listás Diák", api.refreshHomeFeed().student)
    }

    @Test fun savedAccountsSwitchOfflineAndKeepCachesAndNotificationBaselinesSeparate() {
        val vault = MemoryStore()
        val multi = KretaApi(store, OkHttpClient(), "test", server.url("/token").toString(),
            { server.url("/").toString().trimEnd('/') }, pause = {}, accountArchive = vault)
        multi.refreshHomeFeed()
        store.write("notification_seen", """{"grades":["old"]}""")
        val firstCache = store.read("school")
        multi.accounts()
        val delegate = server.dispatcher
        val secondPayload = JSONObject().put("kreta:institute_code", "school-a").put("kreta:institute_user_id", "456")
        val secondIdToken = "header." + Base64.getUrlEncoder().withoutPadding().encodeToString(secondPayload.toString().toByteArray()) + ".signature"
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.requestUrl!!.encodedPath == "/token") return json(JSONObject().put("access_token", "second").put("refresh_token", "refresh-second")
                    .put("expires_in", 3600).put("id_token", secondIdToken).toString())
                return delegate.dispatch(request)
            }
        }
        multi.login("second-account", "verifier")
        assertEquals("school-a:456", multi.profileKey)
        assertNull(store.read("school"))
        assertNull(store.read("notification_seen"))
        val calls = server.requestCount
        multi.switchAccount("school-a:123")
        assertEquals(calls, server.requestCount)
        assertEquals(firstCache, store.read("school"))
        assertEquals("""{"grades":["old"]}""", store.read("notification_seen"))
        multi.logout()
        assertEquals(listOf("school-a:456"), multi.accounts().map { it.first })
        multi.switchAccount("school-a:456")
        assertEquals("school-a:456", multi.profileKey)
    }
}
