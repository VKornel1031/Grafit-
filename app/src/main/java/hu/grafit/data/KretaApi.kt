package hu.grafit.data

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

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
    fun isBrowserNavigationAllowed(url: String): Boolean {
        val uri = runCatching { url.toHttpUrl() }.getOrNull() ?: return false
        return uri.scheme == "https" && uri.username.isEmpty() && uri.password.isEmpty()
    }
    fun callbackCode(url: String, state: String): String? {
        val uri = runCatching { url.toHttpUrl() }.getOrNull() ?: return null
        val target = redirect.toHttpUrl()
        if (uri.scheme != target.scheme || uri.host != target.host || uri.port != target.port || uri.encodedPath != target.encodedPath) return null
        require(uri.queryParameter("state") == state) { "A bejelentkezési munkamenet érvénytelen. Próbáld újra." }
        return uri.queryParameter("code")?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("A bejelentkezés megszakadt. Próbáld újra.")
    }
    fun accountKey(idToken: String): String {
        val claims = JSONObject(String(Base64.getUrlDecoder().decode(idToken.split('.')[1]), Charsets.UTF_8))
        val user = claims.text("kreta:institute_user_id").ifBlank { claims.text("sub") }
        require(user.isNotBlank()) { "Hiányzó fiókazonosító." }
        return institute(idToken) + ":" + user
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
