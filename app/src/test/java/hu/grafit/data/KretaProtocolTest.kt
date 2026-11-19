package hu.grafit.data

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import okhttp3.HttpUrl.Companion.toHttpUrl

class KretaProtocolTest {
    @Test fun pkceMatchesRfc7636() {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", OAuth.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
    }
    @Test fun callbackRequiresExactOriginAndState() {
        assertNull(OAuth.callbackCode("https://attacker.example/ellenorzo-student/prod/oauthredirect?code=abc&state=state", "state"))
        assertNull(OAuth.callbackCode("http://mobil.e-kreta.hu/ellenorzo-student/prod/oauthredirect?code=abc&state=state", "state"))
        assertEquals("abc", OAuth.callbackCode("${OAuth.redirect}?code=abc&state=state", "state"))
    }
    @Test(expected = IllegalArgumentException::class) fun callbackRejectsForgedState() {
        OAuth.callbackCode("${OAuth.redirect}?code=abc&state=forged", "state")
    }
    @Test(expected = IllegalArgumentException::class) fun callbackRejectsMissingCode() {
        OAuth.callbackCode("${OAuth.redirect}?error=access_denied&state=state", "state")
    }
    @Test fun gradeHandlesTextAndNullFields() {
        val grade = Grade.parse(JSONObject("""{"Uid":"1","Tantargy":{"Nev":"Magatartás"},"SzamErtek":null,"SzovegesErtek":"Példás","Tema":null}"""))
        assertNull(grade.number)
        assertEquals("Példás", grade.value)
        assertEquals("", grade.topic)
        assertNull(average(listOf(grade)))
    }
    @Test fun averageUsesWeightsAndOnlyMidyearGrades() {
        fun grade(n: Int, weight: Int, type: String = "EvkoziJegyErtekeles") = Grade("1", "Math", "$n", n, weight, "", "", "", type)
        assertEquals(4.333333, average(listOf(grade(5, 200), grade(3, 100), grade(1, 0, "FeleviJegyErtekeles")))!!, .00001)
        assertNull(average(listOf(grade(5, 0))))
    }
    @Test fun timezoneConvertsUtcToHungarianTime() {
        assertEquals("2026-01-05T09:00", localTime("2026-01-05T08:00:00Z").toString())
        assertEquals("2026-07-05T10:00", localTime("2026-07-05T08:00:00Z").toString())
    }
    @Test fun demoIncludesEveryCoreSection() {
        val data = SchoolData.demo()
        assertTrue(data.grades.isNotEmpty())
        assertTrue(data.lessons.isNotEmpty())
        assertTrue(data.absences.isNotEmpty())
        assertTrue(average(data.grades)!! in 1.0..5.0)
    }

    @Test fun profileKeysSeparateInstitutesAndUsers() {
        fun token(institute: String, user: String): String {
            val claims = JSONObject().put("kreta:institute_code", institute).put("kreta:institute_user_id", user)
            return "header." + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(claims.toString().toByteArray()) + ".signature"
        }
        assertEquals("school-a:123", OAuth.accountKey(token("school-a", "123")))
        assertNotEquals(OAuth.accountKey(token("school-a", "123")), OAuth.accountKey(token("school-b", "123")))
        assertNotEquals(OAuth.accountKey(token("school-a", "123")), OAuth.accountKey(token("school-a", "456")))
    }
    @Test fun localProfileRoundTripsUnicodeAndPhoto() {
        val profile = LocalProfile("Árvíztűrő Tükörfúrógép", "encoded-photo")
        assertEquals(profile, LocalProfile.parse(profile.json()))
        assertEquals(LocalProfile(), LocalProfile.parse(LocalProfile().json()))
    }

    @Test fun loginUrlMatchesFirkaNestedAuthorizeRequest() {
        val login = OAuth.loginUrl("verifier", "state").toHttpUrl()
        assertEquals("idp.e-kreta.hu", login.host)
        assertEquals("/Account/Login", login.encodedPath)
        val callback = ("https://idp.e-kreta.hu" + login.queryParameter("ReturnUrl")).toHttpUrl()
        assertEquals("/connect/authorize/callback", callback.encodedPath)
        assertEquals(OAuth.redirect, callback.queryParameter("redirect_uri"))
        assertEquals(OAuth.clientId, callback.queryParameter("client_id"))
        assertEquals(OAuth.challenge("verifier"), callback.queryParameter("code_challenge"))
        assertEquals("S256", callback.queryParameter("code_challenge_method"))
        assertEquals("state", callback.queryParameter("state"))
        assertEquals("state", callback.queryParameter("nonce"))
        assertTrue(callback.queryParameter("scope")!!.contains("offline_access"))
    }
    @Test fun loginBrowserAllowsHttpsFederationAndRejectsUnsafeSchemes() {
        assertTrue(OAuth.isBrowserNavigationAllowed("https://idp.e-kreta.hu/Account/Login"))
        assertTrue(OAuth.isBrowserNavigationAllowed("https://login.microsoftonline.com/tenant/oauth2/authorize"))
        listOf("http://idp.e-kreta.hu", "javascript:alert(1)", "file:///data/data/app", "intent://login", "https://user:password@idp.e-kreta.hu").forEach {
            assertFalse(OAuth.isBrowserNavigationAllowed(it))
        }
    }
}
