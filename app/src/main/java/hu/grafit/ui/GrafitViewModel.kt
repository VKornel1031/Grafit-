package hu.grafit.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import hu.grafit.data.*
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
    private val syncOperation = OperationState()
    private val profileOperation = OperationState()
    private var accountChanging by mutableStateOf(false)
    val busy: Boolean get() = syncOperation.busy
    val error: String? get() = syncOperation.error
    val profileBusy: Boolean get() = profileOperation.busy || accountChanging
    val profileError: String? get() = profileOperation.error
    var date by mutableStateOf(today()); private set
    init { if (signedIn) syncAll() }
    fun customize(value: Appearance) {
        appearance = value
        prefs.edit().putString("mode", value.mode).putInt("color", value.color).putBoolean("dynamic", value.dynamic)
            .putBoolean("compact", value.compact).putBoolean("hide", value.hideGrades).putBoolean("grades", value.showGrades)
            .putBoolean("absences", value.showAbsences).putFloat("radius", value.radius).putInt("refreshRate", value.refreshRate).apply()
    }
    private fun work(failureMessage: String = "Nem sikerült csatlakozni. Ellenőrizd a kapcsolatot, és próbáld újra.", action: suspend () -> Unit) =
        syncOperation.launch(viewModelScope, failureMessage, action)

    private fun editProfile(failureMessage: String = "Nem sikerült helyben menteni a profilt. Próbáld újra.", action: suspend (String) -> Unit) {
        if (profileBusy || (!signedIn && !demo)) return
        profileOperation.launch(viewModelScope, failureMessage) {
            // Capture the account on the main thread before starting any asynchronous file work.
            val account = profileAccount
            action(account)
        }
    }

    fun login(code: String, verifier: String) {
        if (profileBusy) return
        work {
            accountChanging = true
            try {
                val localProfile = withContext(Dispatchers.IO) {
                    api.login(code, verifier)
                    profiles.read(requireNotNull(api.profileKey))
                }
                // Expose the account only after its local profile has been loaded.
                profile = localProfile
                signedIn = true; demo = false; data = null
            } finally {
                accountChanging = false
            }
            data = withContext(Dispatchers.IO) { api.load(date, ::publishSync) }
            showSyncIssues()
        }
    }
    private fun publishSync(snapshot: SchoolData) { viewModelScope.launch { data = snapshot } }
    private fun showSyncIssues() {
        if (api.syncIssues.isNotEmpty()) {
            val names = api.syncIssues.take(3).joinToString(", ")
            val remaining = if (api.syncIssues.size > 3) " és további ${api.syncIssues.size - 3}" else ""
            syncOperation.error = "Néhány részfrissítés sikertelen ($names$remaining)."
        }
    }
    private fun syncAll() = work {
        data = withContext(Dispatchers.IO) { api.load(date, ::publishSync) }
        showSyncIssues()
    }
    fun reload() = work {
        if (!demo) {
            data = withContext(Dispatchers.IO) {
                if (api.syncIssues.isNotEmpty()) api.load(date, ::publishSync) else api.refreshHomeFeed(date, ::publishSync)
            }
            showSyncIssues()
        }
    }
    fun selectDate(value: LocalDate) {
        if (busy) return
        date = value
    }
    fun startDemo() {
        if (busy || profileBusy) return
        demo = true; syncOperation.error = null; data = SchoolData.demo()
        profile = profiles.read("demo")
    }
    fun saveProfileName(name: String) = editProfile { account ->
        val trimmed = name.trim()
        require(trimmed.length <= 60 && trimmed.none { it.isISOControl() }) { "A név legfeljebb 60 karakter lehet." }
        val updated = profile.copy(displayName = trimmed)
        withContext(Dispatchers.IO) { profiles.save(account, updated) }
        profile = updated
    }
    fun updateProfilePhoto(uri: Uri?) = editProfile("Nem sikerült beolvasni vagy menteni a képet. Válassz egy másik képet.") { account ->
        val photo = withContext(Dispatchers.IO) { uri?.let(profiles::importPhoto) }
        val updated = profile.copy(photo = photo)
        withContext(Dispatchers.IO) { profiles.save(account, updated) }
        profile = updated
    }
    fun logout() {
        // A local save must finish before the active account can change.
        if (busy || profileBusy) return
        accountChanging = true
        work {
            try {
                withContext(Dispatchers.IO) { api.logout() }
                demo = false; signedIn = false; data = null; profile = LocalProfile(); date = today()
                profileOperation.error = null
                android.webkit.CookieManager.getInstance().removeAllCookies(null)
                android.webkit.WebStorage.getInstance().deleteAllData()
            } finally {
                accountChanging = false
            }
        }
    }
    fun dismissError() { syncOperation.error = null }
}
