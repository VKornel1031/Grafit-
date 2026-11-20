package hu.grafit.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import hu.grafit.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class Appearance(val mode: String = "system", val color: Int = 0, val dynamic: Boolean = true,
                      val compact: Boolean = false, val hideGrades: Boolean = false,
                      val showGrades: Boolean = true, val showAbsences: Boolean = true, val radius: Float = 24f, val refreshRate: Int = 120)
class GrafitViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("appearance", 0)
    private val api = KretaApi(SecureStore(app))
    private val profiles = ProfileStore(app)
    private var subjectsAttempted = false
    private var lastSyncAttempt = 0L
    var profile by mutableStateOf(api.profileKey?.let(profiles::read) ?: LocalProfile())
        private set
    val displayName: String get() = profile.displayName.ifBlank { data?.student.orEmpty() }.ifBlank { "Grafit" }
    private val profileAccount: String get() = if (demo) "demo" else requireNotNull(api.profileKey)
    var appearance by mutableStateOf(Appearance(prefs.getString("mode", "system")!!, prefs.getInt("color", 0),
        prefs.getBoolean("dynamic", true), prefs.getBoolean("compact", false), prefs.getBoolean("hide", false),
        prefs.getBoolean("grades", true), prefs.getBoolean("absences", true), prefs.getFloat("radius", 24f), prefs.getInt("refreshRate", 120)))
        private set
    var data by mutableStateOf(api.cached()); private set
    var signedIn by mutableStateOf(api.signedIn); private set
    var demo by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var date by mutableStateOf(today()); private set
    init { if (signedIn) syncAll() }
    fun customize(value: Appearance) {
        appearance = value
        prefs.edit().putString("mode", value.mode).putInt("color", value.color).putBoolean("dynamic", value.dynamic)
            .putBoolean("compact", value.compact).putBoolean("hide", value.hideGrades).putBoolean("grades", value.showGrades)
            .putBoolean("absences", value.showAbsences).putFloat("radius", value.radius).putInt("refreshRate", value.refreshRate).apply()
    }
    private fun work(failureMessage: String = "Nem sikerült csatlakozni. Ellenőrizd a kapcsolatot, és próbáld újra.", action: suspend () -> Unit) {
        if (busy) return
        busy = true; error = null
        viewModelScope.launch {
            try { action() } catch (e: CancellationException) { throw e } catch (e: Exception) {
                error = if (e is ApiFailure || e is IllegalArgumentException) e.message else failureMessage
            } finally { busy = false }
        }
    }
    fun login(code: String, verifier: String) = work {
        withContext(Dispatchers.IO) { api.login(code, verifier) }
        signedIn = true; demo = false; data = null
        profile = withContext(Dispatchers.IO) { profiles.read(profileAccount) }
        lastSyncAttempt = System.currentTimeMillis()
        subjectsAttempted = false
        data = withContext(Dispatchers.IO) { api.load(date, ::publishSync) }
        showSyncIssues()
    }
    private fun publishSync(snapshot: SchoolData) { viewModelScope.launch { data = snapshot } }
    private fun showSyncIssues() {
        if (api.syncIssues.isNotEmpty()) {
            val names = api.syncIssues.take(3).joinToString(", ")
            val remaining = if (api.syncIssues.size > 3) " és további ${api.syncIssues.size - 3}" else ""
            error = "Néhány részfrissítés sikertelen ($names$remaining)."
        }
    }
    private fun syncAll() = work {
        lastSyncAttempt = System.currentTimeMillis()
        subjectsAttempted = false
        data = withContext(Dispatchers.IO) { api.load(date, ::publishSync) }
        showSyncIssues()
    }
    fun reload() = work {
        if (!demo) {
            lastSyncAttempt = System.currentTimeMillis()
            subjectsAttempted = false
            data = withContext(Dispatchers.IO) {
                if (api.syncIssues.isNotEmpty()) api.load(date, ::publishSync) else api.refreshHomeFeed(::publishSync)
            }
            showSyncIssues()
        }
    }
    fun loadSubjects() {
        if (demo || busy || subjectsAttempted) return
        subjectsAttempted = true
        work {
            val updated = withContext(Dispatchers.IO) { api.getSubjects(); api.cached() }
            if (updated != null) data = updated
        }
    }
    fun onResume() {
        if (signedIn && !demo && !busy && System.currentTimeMillis() - lastSyncAttempt >= 120_000) reload()
    }
    fun selectDate(value: LocalDate) {
        if (busy) return
        date = value
        if (signedIn && !demo) work {
            data = withContext(Dispatchers.IO) { api.getLessonsCovering(value, value.plusDays(14)) }
        }
    }
    fun startDemo() {
        if (busy) return
        demo = true; error = null; data = SchoolData.demo()
        profile = profiles.read("demo")
    }
    fun saveProfileName(name: String) = work {
        val trimmed = name.trim()
        require(trimmed.length <= 60 && trimmed.none { it.isISOControl() }) { "A név legfeljebb 60 karakter lehet." }
        val updated = profile.copy(displayName = trimmed)
        withContext(Dispatchers.IO) { profiles.save(profileAccount, updated) }
        profile = updated
    }
    fun updateProfilePhoto(uri: Uri?) = work("Nem sikerült beolvasni vagy menteni a képet. Válassz egy másik képet.") {
        val updated = withContext(Dispatchers.IO) {
            val photo = uri?.let(profiles::importPhoto)
            profile.copy(photo = photo).also { profiles.save(profileAccount, it) }
        }
        profile = updated
    }
    fun logout() = work {
        withContext(Dispatchers.IO) { api.logout() }
        demo = false; signedIn = false; data = null; profile = LocalProfile(); date = today()
        android.webkit.CookieManager.getInstance().removeAllCookies(null)
        android.webkit.WebStorage.getInstance().deleteAllData()
    }
    fun dismissError() { error = null }
}
