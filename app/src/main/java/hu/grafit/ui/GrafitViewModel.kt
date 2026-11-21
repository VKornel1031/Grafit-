package hu.grafit.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import hu.grafit.data.*
import hu.grafit.notifications.SchoolNotifications
import hu.grafit.notifications.SchoolServices
import hu.grafit.notifications.LessonLiveNotification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class GrafitViewModel @JvmOverloads constructor(app: Application, checkUpdatesOnStart: Boolean = true) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("appearance", 0)
    private val api = SchoolServices.api(app)
    var notificationOptions by mutableStateOf(SchoolNotifications.options(app))
        private set
    fun configureNotifications(options: NotificationOptions) {
        notificationOptions = options
        SchoolNotifications.save(getApplication(), options)
    }
    private val profiles = ProfileStore(app)
    var profile by mutableStateOf(api.profileKey?.let(profiles::read) ?: LocalProfile())
        private set
    val displayName: String get() = profile.displayName.ifBlank { data?.student.orEmpty() }.ifBlank { "Grafit" }
    private val profileAccount: String get() = if (demo) "demo" else requireNotNull(api.profileKey)
    var appearance by mutableStateOf(Appearance.parse(runCatching {
        prefs.getString("configuration", null)?.let { org.json.JSONObject(it) } ?: org.json.JSONObject(prefs.all)
    }.getOrElse { org.json.JSONObject() }).resolveCss())
        private set
    var data by mutableStateOf(api.cached()); private set
    var signedIn by mutableStateOf(api.signedIn); private set
    var demo by mutableStateOf(false); private set
    private val syncOperation = OperationState()
    private val profileOperation = OperationState()
    private var accountChanging by mutableStateOf(false)
    val busy: Boolean get() = syncOperation.busy
    val error: String? get() = syncOperation.error
    val authenticationRequired: Boolean get() = syncOperation.authenticationRequired
    val profileBusy: Boolean get() = profileOperation.busy || accountChanging
    val profileError: String? get() = profileOperation.error
    internal val updates = UpdateController(app, viewModelScope)
    var date by mutableStateOf(today()); private set
    init { if (signedIn) syncAll(); SchoolNotifications.schedule(app); if (checkUpdatesOnStart) updates.check() }
    /** Bring background-fetched data into the UI without starting another network sync. */
    fun resumeLocalData() {
        if (busy || demo || !signedIn) return
        val account = api.profileKey
        viewModelScope.launch {
            val latest = withContext(Dispatchers.IO) { api.cached() }
            if (!busy && !demo && signedIn && api.profileKey == account && latest != null) data = latest
        }
    }
    fun customize(value: Appearance) {
        val updated = value.syncCssControls(appearance).resolveCss()
        appearance = updated
        prefs.edit().putString("configuration", updated.json()).apply()
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
                getApplication<Application>().getSystemService(android.app.NotificationManager::class.java).cancelAll()
                SchoolNotifications.schedule(getApplication())
            } finally {
                accountChanging = false
            }
            data = withContext(Dispatchers.IO) { api.load(date, ::publishSync) }
            showSyncIssues()
        }
    }
    private fun publishSync(snapshot: SchoolData) { viewModelScope.launch { data = snapshot } }
    private fun showSyncIssues() {
        LessonLiveNotification.requestUpdate(getApplication())
        if (api.syncIssues.isNotEmpty()) {
            val names = api.syncIssues.take(3).joinToString(", ")
            val remaining = if (api.syncIssues.size > 3) " és további ${api.syncIssues.size - 3}" else ""
            syncOperation.error = "Nem frissült: $names$remaining. A mentett adatok megmaradtak. Próbáld újra később."
        }
    }
    private fun syncAll() = work {
        data = withContext(Dispatchers.IO) { api.load(date, ::publishSync) }
        showSyncIssues()
    }
    fun reload() = work {
        if (!demo) {
            data = withContext(Dispatchers.IO) { api.refreshHomeFeed(date, ::publishSync) }
            showSyncIssues()
        }
    }
    fun selectDate(value: LocalDate) {
        if (busy) return
        date = value
        if (signedIn && !demo) work("Nem sikerült betölteni a kiválasztott hetet. A mentett órák továbbra is elérhetők.") {
            data = withContext(Dispatchers.IO) { api.getTimetableWeek(value) }
        }
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
                SchoolNotifications.schedule(getApplication())
                LessonLiveNotification.requestUpdate(getApplication())
                getApplication<Application>().getSystemService(android.app.NotificationManager::class.java).cancelAll()
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
