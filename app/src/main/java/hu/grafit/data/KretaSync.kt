package hu.grafit.data

import android.os.Build
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** Native port of Firka's token recovery, dataset sync, home refresh, and range reconciliation. */
class KretaApi(
    private val store: StringStore,
    private val http: OkHttpClient = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS).followRedirects(false).build(),
    private val userAgent: String = "hu.ekreta.student/5.15.0/${Build.MODEL}/${Build.VERSION.RELEASE}/${Build.VERSION.SDK_INT}",
    private val tokenUrl: String = OAuth.tokenUrl,
    private val schoolUrl: (String) -> String = { "https://$it.e-kreta.hu" },
    private val pause: (Long) -> Unit = Thread::sleep,
    private val now: () -> Long = System::currentTimeMillis,
    private val onCollectionUpdated: (String, JSONArray) -> Unit = { _, _ -> },
    private val accountMutationLock: Any = Any(),
    private val accountArchive: StringStore? = null
) {
    private val tokens = Any()
    private val cache = SchoolCache(store)
    @Volatile var syncIssues: List<String> = emptyList(); private set
    val signedIn: Boolean get() = store.read("session") != null
    val profileKey: String? get() = store.read("session")?.let(::JSONObject)?.let {
        it.text("profile_key").ifBlank { OAuth.accountKey(it.getString("id_token")) }
    }
    fun cached(): SchoolData? = cache.read()

    private fun token(form: FormBody): JSONObject = http.newCall(Request.Builder().url(tokenUrl)
        .post(form).header("User-Agent", userAgent).header("Accept", "*/*").build()).execute().use {
        if (!it.isSuccessful) throw ApiFailure(it.code, authenticationRequired = it.code in listOf(400, 401))
        JSONObject(it.body?.string() ?: throw IOException("Empty token response"))
    }

    @Synchronized fun login(code: String, verifier: String) = synchronized(tokens) {
        val response = token(FormBody.Builder().add("client_id", OAuth.clientId).add("grant_type", "authorization_code")
            .add("code", code).add("code_verifier", verifier).add("redirect_uri", OAuth.redirect).build())
        val idToken = response.getString("id_token")
        val key = OAuth.accountKey(idToken)
        response.put("institute", OAuth.institute(idToken)).put("profile_key", key)
            .put("expires_at", now() + response.getLong("expires_in") * 1000 - 60_000)
        synchronized(accountMutationLock) {
            if (profileKey != key) { rememberAccount(); store.clear() }
            store.write("session", response.toString())
            rememberAccount()
        }
    }

    private fun refresh(previous: JSONObject): JSONObject {
        val delays = listOf(0L, 1000L, 3000L, 5000L)
        var last: Exception? = null
        for (delay in delays) {
            if (delay > 0) pause(delay)
            try {
                val response = token(FormBody.Builder().add("client_id", OAuth.clientId)
                    .add("grant_type", "refresh_token").add("institute_code", previous.getString("institute"))
                    .add("refresh_token", previous.getString("refresh_token")).build())
                response.put("institute", previous.getString("institute"))
                    .put("profile_key", previous.text("profile_key").ifBlank { OAuth.accountKey(previous.getString("id_token")) })
                    .put("expires_at", now() + response.getLong("expires_in") * 1000 - 60_000)
                if (!response.has("id_token")) response.put("id_token", previous.getString("id_token"))
                if (!response.has("refresh_token")) response.put("refresh_token", previous.getString("refresh_token"))
                store.write("session", response.toString())
                return response
            } catch (e: ApiFailure) {
                if (e.status in listOf(400, 401)) throw e
                last = e
            } catch (e: IOException) { last = e }
        }
        throw requireNotNull(last)
    }

    private fun session(rejectedAccessToken: String? = null): JSONObject = synchronized(tokens) {
        val current = JSONObject(store.read("session") ?: throw ApiFailure(401))
        val rejected = rejectedAccessToken != null && rejectedAccessToken == current.getString("access_token")
        if (rejected || current.optLong("expires_at") <= now() + 60_000) refresh(current) else current
    }

    private fun request(path: String): String {
        fun execute(credentials: JSONObject): Triple<Int, String, String?> = http.newCall(Request.Builder()
            .url(schoolUrl(credentials.getString("institute")) + path)
            .header("Authorization", "Bearer ${credentials.getString("access_token")}")
            .header("apiKey", "21ff6c25-d1da-4a68-a811-c881a6057463")
            .header("Accept", "*/*").header("User-Agent", userAgent).build()).execute().use {
                Triple(it.code, it.body?.string() ?: "", it.header("Retry-After"))
            }
        var last: Exception? = null
        var retryAfterMillis = 0L
        for (attempt in 0..4) {
            if (attempt > 0) pause(maxOf(1000L shl (attempt - 1), retryAfterMillis))
            try {
                val credentials = session()
                var response = execute(credentials)
                if (response.first == 401) response = execute(session(credentials.getString("access_token")))
                retryAfterMillis = response.third?.let { value ->
                    value.toLongOrNull()?.coerceIn(0, 86_400)?.times(1000)
                        ?: runCatching {
                            val retryAt = java.time.ZonedDateTime.parse(value, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)
                            (retryAt.toInstant().toEpochMilli() - now()).coerceAtLeast(0)
                        }.getOrDefault(0)
                } ?: 0L
                if (response.first !in 200..299) throw ApiFailure(response.first)
                return response.second
            } catch (e: ApiFailure) {
                // Long server cooldowns are left to a later user refresh, never retried early.
                if (retryAfterMillis > 30_000 || (e.status !in listOf(408, 429) && e.status < 500)) throw e
                last = e
            } catch (e: IOException) {
                if (e !is java.net.SocketTimeoutException) throw e
                last = e
            }
        }
        throw requireNotNull(last)
    }

    private fun collection(path: String): JSONArray = when (val value = JSONTokener(request(path)).nextValue()) {
        is JSONArray -> value
        is JSONObject -> JSONArray().put(value)
        else -> throw IOException("Invalid KRÉTA collection")
    }
    private fun endpoint(name: String, from: LocalDate? = null, to: LocalDate? = null): String {
        val url = "https://placeholder/ellenorzo/v3/sajat/$name".toHttpUrl().newBuilder()
        if (from != null) url.addQueryParameter("datumTol", from.toString())
        if (to != null) url.addQueryParameter("datumIg", to.toString())
        return url.build().let { it.encodedPath + (it.encodedQuery?.let { query -> "?$query" } ?: "") }
    }
    private fun replace(key: String, name: String, from: LocalDate? = null,
                        validate: (JSONObject) -> Any = { it }) {
        val rows = collection(endpoint(name, from))
        rows.objects().forEach { it.getString("Uid"); validate(it) }
        cache.replace(key, rows)
        // Notification delivery must never turn a successful school sync into a failure.
        if (key in setOf("grades", "homework", "tests")) runCatching { onCollectionUpdated(key, rows) }
    }
    private fun messages(key: String, name: String, start: LocalDate) {
        val data = cache.snapshot()
        val lastSync = data.optLong("messagesSyncedAt")
        val from = if (lastSync > 0) java.time.Instant.ofEpochMilli(lastSync).atZone(schoolZone).toLocalDate() else start
        val rows = collection(endpoint(name, from))
        rows.objects().forEach { it.getString("Uid") }
        cache.mergeMessages(key, rows)
    }
    private fun lessons(from: LocalDate, to: LocalDate) {
        cache.replaceLessons(from, to, collection(endpoint("OrarendElemek", from, to)))
    }

    /** Coalesced by the caller and serialized against login/logout to protect account boundaries. */
    @Synchronized fun load(date: LocalDate, onProgress: (SchoolData) -> Unit = {}): SchoolData = sync(date, onProgress)
    @Synchronized fun refreshHomeFeed(date: LocalDate = today(), onProgress: (SchoolData) -> Unit = {}): SchoolData = sync(date, onProgress)

    private fun sync(date: LocalDate, onProgress: (SchoolData) -> Unit): SchoolData {
        session()
        val issues = java.util.Collections.synchronizedList(mutableListOf<String>())
        fun publish() { cache.publish(onProgress) }
        fun attempt(label: String, action: () -> Unit) {
            try { action(); publish() }
            catch (e: ApiFailure) {
                if (e.authenticationRequired) throw e
                issues.add(label)
            } catch (e: IOException) { issues.add(label) }
            catch (e: org.json.JSONException) { issues.add(label) }
            catch (e: java.time.DateTimeException) { issues.add(label) }
        }
        attempt("Tanulói adatlap") {
            val student = collection(endpoint("TanuloAdatlap")).objects().firstOrNull()
                ?: throw IOException("Empty student response")
            student.getString("Nev")
            cache.update { it.put("student", student) }
        }
        attempt("Osztályok és csoportok") { replace("classGroups", "OsztalyCsoportok") }
        val current = today()
        val start = SchoolCache.firstSchoolDay(current)
        val monday = current.minusDays((current.dayOfWeek.value - 1).toLong())
        val weekEnd = monday.plusDays(14)
        val jobs = listOf<Pair<String, () -> Unit>>(
            "Tantárgyak" to { cache.replace("subjects", collection("/dktapi/intezmenyek/munkaterek/tanulok")) },
            "Jegyek" to { replace("grades", "Ertekelesek", validate = Grade::parse) },
            "Házi feladatok" to { replace("homework", "HaziFeladatok", start, SchoolTask::homework) },
            "Dolgozatok" to { replace("tests", "BejelentettSzamonkeresek", validate = SchoolTask::exam) },
            "Faliújság" to { messages("notices", "FaliujsagElemek", start) },
            "Feljegyzések" to { messages("notes", "Feljegyzesek", start) },
            "Aktuális órarend" to { lessons(monday, weekEnd) }
        )
        runBlocking {
            val permits = Semaphore(3)
            jobs.map { (label, action) -> async(Dispatchers.IO) { permits.withPermit { attempt(label, action) } } }.awaitAll()
        }
        if (issues.none { it == "Faliújság" || it == "Feljegyzések" }) cache.update { it.put("messagesSyncedAt", now()) }
        // Fetch other weeks on demand instead of flooding KRÉTA with an entire year.
        if (date < monday || date >= weekEnd) attempt("Kiválasztott hét") {
            val selectedMonday = date.minusDays((date.dayOfWeek.value - 1).toLong())
            lessons(selectedMonday, selectedMonday.plusDays(7))
        }
        attempt("Hiányzások") { replace("absences", "Mulasztasok", validate = Absence::parse) }
        cache.update { data ->
            val teachers = SchoolCache.array(data, "lessons").objects().mapNotNull { lesson ->
                val name = lesson.text("TanarNeve")
                if (name.isBlank()) null else JSONObject().put("name", name).put("subject", lesson.subject())
                    .put("classGroup", lesson.optJSONObject("OsztalyCsoport")?.text("Uid"))
            }.distinctBy { it.toString() }
            data.put("teachers", JSONArray(teachers)).put("lastSyncAttempt", now())
            if (issues.isEmpty()) data.put("syncedAt", now())
        }
        syncIssues = issues.toList().distinct()
        val result = cached() ?: throw IOException("No usable school data")
        publish()
        return result
    }

    /** Background notifications fetch no timetable and do not refresh unrelated collections. */
    @Synchronized fun checkNotificationUpdates() {
        if (!signedIn) return
        session()
        val failures = mutableListOf<Exception>()
        val jobs = listOf<() -> Unit>(
            { replace("grades", "Ertekelesek", validate = Grade::parse) },
            { replace("homework", "HaziFeladatok", SchoolCache.firstSchoolDay(today()), SchoolTask::homework) },
            { replace("tests", "BejelentettSzamonkeresek", validate = SchoolTask::exam) }
        )
        for (job in jobs) {
            if (Thread.currentThread().isInterrupted) throw InterruptedException()
            try { job() }
            catch (e: ApiFailure) { if (e.authenticationRequired) throw e; failures.add(e) }
            catch (e: IOException) { failures.add(e) }
            catch (e: org.json.JSONException) { failures.add(e) }
        }
        failures.firstOrNull()?.let { throw it }
    }

    @Synchronized fun getSubjects(): JSONArray {
        val rows = collection("/dktapi/intezmenyek/munkaterek/tanulok")
        cache.replace("subjects", rows)
        return rows
    }
    @Synchronized fun getClassGroupAverages(studyTaskUid: String): JSONArray {
        val path = endpoint("Ertekelesek/Atlagok/OsztalyAtlagok") +
            "?oktatasiNevelesiFeladatUid=${java.net.URLEncoder.encode(studyTaskUid.substringBefore(','), "UTF-8")}" +
            "&oktatasiNevelesiFeladatUid=${java.net.URLEncoder.encode(studyTaskUid.substringBefore(','), "UTF-8")}"
        val rows = collection(path)
        cache.update { data ->
            val groups = data.optJSONObject("classAverages") ?: JSONObject()
            groups.put(studyTaskUid, rows)
            data.put("classAverages", groups)
        }
        return rows
    }
    /** Persist successful empty ranges too: a holiday week must not refetch on every tap. */
    @Synchronized fun getTimetableWeek(date: LocalDate): SchoolData {
        val monday = date.minusDays((date.dayOfWeek.value - 1).toLong())
        val end = monday.plusDays(7)
        if (!cache.coversLessons(monday, end)) lessons(monday, end)
        return cached() ?: throw IOException("No usable school data")
    }
    @Synchronized fun getLessonsCovering(from: LocalDate, to: LocalDate): SchoolData {
        SchoolCache.chunks(from, to).forEach { (start, end) -> lessons(start, end) }
        return requireNotNull(cached())
    }
    fun calendarSnapshot(): Pair<SchoolData?, List<Pair<LocalDate, LocalDate>>> {
        val raw = cache.snapshot()
        return runCatching { SchoolData.parse(raw) }.getOrNull() to raw.optJSONArray("lessonRanges")?.objects().orEmpty().mapNotNull {
            runCatching { LocalDate.parse(it.getString("from")) to LocalDate.parse(it.getString("to")) }.getOrNull()
        }
    }
    fun lessonCoverage(): List<Pair<LocalDate, LocalDate>> = cache.snapshot().optJSONArray("lessonRanges")?.objects().orEmpty().mapNotNull {
        runCatching { LocalDate.parse(it.getString("from")) to LocalDate.parse(it.getString("to")) }.getOrNull()
    }
    private fun archive(): JSONObject = accountArchive?.read("accounts")?.let(::JSONObject) ?: JSONObject()
    private fun rememberAccount() {
        val archiveStore = accountArchive ?: return
        val key = profileKey ?: return
        val accounts = archive()
        val record = JSONObject()
        listOf("session", "school", "notification_seen").forEach { name -> store.read(name)?.let { record.put(name, it) } }
        record.put("name", cached()?.student ?: key.substringBefore(':'))
        accounts.put(key, record)
        archiveStore.write("accounts", accounts.toString())
    }
    @Synchronized fun accounts(): List<Pair<String, String>> {
        rememberAccount()
        val accounts = archive()
        return accounts.keys().asSequence().map { it to accounts.getJSONObject(it).text("name", it) }.toList()
    }
    @Synchronized fun switchAccount(key: String) {
        rememberAccount()
        val record = archive().optJSONObject(key) ?: throw IllegalArgumentException("A mentett fiók nem található.")
        synchronized(accountMutationLock) {
            store.clear()
            listOf("session", "school", "notification_seen").forEach { name -> record.text(name).takeIf(String::isNotBlank)?.let { store.write(name, it) } }
            syncIssues = emptyList()
        }
    }
    @Synchronized fun forgetAccount(key: String) {
        require(key != profileKey) { "Az aktív fiókból előbb jelentkezz ki." }
        val accounts = archive().apply { remove(key) }
        accountArchive?.write("accounts", accounts.toString())
    }
    @Synchronized fun logout() = synchronized(tokens) {
        synchronized(accountMutationLock) {
            val key = profileKey
            val accounts = archive().apply { if (key != null) remove(key) }
            accountArchive?.write("accounts", accounts.toString())
            store.clear(); syncIssues = emptyList()
        }
    }
}
