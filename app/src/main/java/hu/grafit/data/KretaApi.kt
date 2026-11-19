package hu.grafit.data

import android.os.Build
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.json.JSONArray
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit

/** Protocol adapted from Firka. See ATTRIBUTION.md and LICENSE.txt. */
object OAuth {
    const val clientId = "kreta-ellenorzo-student-mobile-android"
    const val redirect = "https://mobil.e-kreta.hu/ellenorzo-student/prod/oauthredirect"
    const val tokenUrl = "https://idp.e-kreta.hu/connect/token"
    fun random(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) })
    fun challenge(verifier: String): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
    fun loginUrl(verifier: String, state: String): String {
        val callback = "https://idp.e-kreta.hu/connect/authorize/callback".toHttpUrl().newBuilder()
            .addQueryParameter("redirect_uri", redirect).addQueryParameter("client_id", clientId)
            .addQueryParameter("response_type", "code").addQueryParameter("prompt", "login")
            .addQueryParameter("state", state).addQueryParameter("nonce", state)
            .addQueryParameter("scope", "openid email offline_access kreta-ellenorzo-webapi.public kreta-eugyintezes-webapi.public kreta-fileservice-webapi.public kreta-mobile-global-webapi.public kreta-dkt-webapi.public kreta-ier-webapi.public")
            .addQueryParameter("code_challenge", challenge(verifier)).addQueryParameter("code_challenge_method", "S256")
            .addQueryParameter("suppressed_prompt", "login").build()
        return "https://idp.e-kreta.hu/Account/Login".toHttpUrl().newBuilder()
            .addQueryParameter("ReturnUrl", callback.encodedPath + "?" + callback.encodedQuery).build().toString()
    }
    fun callbackCode(url: String, state: String): String? {
        val uri = runCatching { url.toHttpUrl() }.getOrNull() ?: return null
        val target = redirect.toHttpUrl()
        if (uri.scheme != target.scheme || uri.host != target.host || uri.port != target.port || uri.encodedPath != target.encodedPath) return null
        require(uri.queryParameter("state") == state) { "A bejelentkezési munkamenet érvénytelen. Próbáld újra." }
        return uri.queryParameter("code")?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("A bejelentkezés megszakadt. Próbáld újra.")
    }
    fun institute(idToken: String): String {
        val claims = JSONObject(String(Base64.getUrlDecoder().decode(idToken.split('.')[1]), Charsets.UTF_8))
        return claims.getString("kreta:institute_code").also {
            require(it.matches(Regex("[a-zA-Z0-9-]+"))) { "Érvénytelen intézményazonosító." }
        }
    }
}
class ApiFailure(val status: Int) : Exception(when(status) {
    400, 401 -> "A munkamenet lejárt. Jelentkezz be újra."
    403 -> "A KRÉTA nem engedélyezte a hozzáférést."
    429 -> "Túl sok kérés. Próbáld újra később."
    else -> "A KRÉTA szolgáltatás nem érhető el ($status)."
})
class KretaApi(private val store: SecureStore) {
    private val userAgent = "hu.ekreta.student/5.15.0/${Build.MODEL}/${Build.VERSION.RELEASE}/${Build.VERSION.SDK_INT}"
    private val http = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS).followRedirects(false).build()
    val signedIn: Boolean get() = store.read("session") != null
    fun cached(): SchoolData? = store.read("school")?.let { runCatching { SchoolData.parse(JSONObject(it)) }.getOrNull() }
    private fun token(form: FormBody): JSONObject = http.newCall(Request.Builder().url(OAuth.tokenUrl)
        .post(form).header("User-Agent", userAgent).build()).execute().use {
        if (!it.isSuccessful) throw ApiFailure(it.code)
        JSONObject(it.body!!.string())
    }
    @Synchronized fun login(code: String, verifier: String) {
        val response = token(FormBody.Builder().add("client_id", OAuth.clientId).add("grant_type", "authorization_code")
            .add("code", code).add("code_verifier", verifier).add("redirect_uri", OAuth.redirect).build())
        val institute = OAuth.institute(response.getString("id_token"))
        response.put("institute", institute).put("expires_at", System.currentTimeMillis() + response.getLong("expires_in") * 1000)
        store.clear()
        store.write("session", response.toString())
    }
    private fun refresh(session: JSONObject): JSONObject {
        val response = token(FormBody.Builder().add("client_id", OAuth.clientId).add("grant_type", "refresh_token")
            .add("refresh_token", session.getString("refresh_token")).add("institute_code", session.getString("institute")).build())
        response.put("institute", session.getString("institute"))
            .put("expires_at", System.currentTimeMillis() + response.getLong("expires_in") * 1000)
        if (!response.has("refresh_token")) response.put("refresh_token", session.getString("refresh_token"))
        store.write("session", response.toString())
        return response
    }
    @Synchronized fun load(date: java.time.LocalDate): SchoolData {
        var session = JSONObject(store.read("session") ?: throw ApiFailure(401))
        if (session.optLong("expires_at") < System.currentTimeMillis() + 60000) session = refresh(session)
        fun request(endpoint: String): String {
            fun perform(): Pair<Int, String> = http.newCall(Request.Builder()
                .url("https://${session.getString("institute")}.e-kreta.hu/ellenorzo/v3/sajat/$endpoint")
                .header("Authorization", "Bearer ${session.getString("access_token")}")
                .header("apiKey", "21ff6c25-d1da-4a68-a811-c881a6057463")
                .header("User-Agent", userAgent).build()).execute().use { it.code to (it.body?.string() ?: "") }
            var result = perform()
            if (result.first == 401) { session = refresh(session); result = perform() }
            if (result.first !in 200..299) throw ApiFailure(result.first)
            return result.second
        }
        val raw = JSONObject().put("student", JSONObject(request("TanuloAdatlap")))
            .put("grades", JSONArray(request("Ertekelesek")))
            .put("lessons", JSONArray(request("OrarendElemek?datumTol=${date.minusDays(7)}&datumIg=${date.plusDays(7)}")))
            .put("absences", JSONArray(request("Mulasztasok")))
            .put("syncedAt", System.currentTimeMillis())
        val tasks = JSONArray(request("HaziFeladatok")).objects().map(SchoolTask::homework) +
            JSONArray(request("BejelentettSzamonkeresek")).objects().map(SchoolTask::exam) +
            JSONArray(request("FaliujsagElemek")).objects().map(SchoolTask::notice) +
            JSONArray(request("Feljegyzesek")).objects().map(SchoolTask::note)
        raw.put("tasks", JSONArray(tasks.map { it.json() }))
        val parsed = SchoolData.parse(raw)
        store.write("school", raw.toString())
        return parsed
    }
    @Synchronized fun logout() = store.clear()
}
