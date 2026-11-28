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
import hu.grafit.integrations.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class GrafitViewModel @JvmOverloads constructor(app: Application, checkUpdatesOnStart: Boolean = true) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("appearance", 0)
    private val api = SchoolServices.api(app)
    var notificationOptions by mutableStateOf(api.profileKey?.let { StudyStore(app).notifications(it) }?.let(NotificationOptions::parse) ?: SchoolNotifications.options(app))
        private set
    fun configureNotifications(options: NotificationOptions) {
        notificationOptions = options
        SchoolNotifications.save(getApplication(), options)
        activeAccount?.let { account -> viewModelScope.launch { studyWrites.withLock { withContext(Dispatchers.IO) { studyStore.saveNotifications(account, options.json()) } } } }
    }
    private val studyStore = StudyStore(app)
    private val studyWrites = Mutex()
    var study by mutableStateOf(studyStore.read(api.profileKey ?: "demo")); private set
    var savedAccounts by mutableStateOf(emptyList<Pair<String, String>>()); private set
    val activeAccount: String? get() = if (demo) "demo" else api.profileKey
    var toolsMessage by mutableStateOf<String?>(null); private set
    var calendars by mutableStateOf(emptyList<GoogleCalendarSync.Calendar>()); private set
    fun loadCalendars() { viewModelScope.launch {
        runCatching { withContext(Dispatchers.IO) { GoogleCalendarSync.calendars(getApplication()) } }
            .onSuccess { calendars = it }.onFailure { toolsMessage = it.message }
    } }
    fun updateStudy(value: StudyState) {
        if (accountChanging) return
        val account = activeAccount ?: return
        val previous = study
        val integrationsChanged = previous.entries != value.entries || previous.calendarId != value.calendarId ||
            previous.calendarSync != value.calendarSync || previous.calendarKinds != value.calendarKinds || previous.widgetGrades != value.widgetGrades
        study = value
        viewModelScope.launch {
            runCatching { studyWrites.withLock { withContext(Dispatchers.IO) { studyStore.save(account, value) } } }
                .onSuccess { if (integrationsChanged) refreshPersonalIntegrations() }.onFailure { toolsMessage = "A terv mentése sikertelen: ${it.message}" }
        }
    }
    fun saveEntry(entry: StudyEntry) {
        require(entry.title.isNotBlank() && entry.title.length <= 160)
        java.time.LocalDate.parse(entry.date)
        if (entry.time.isNotBlank()) java.time.LocalTime.parse(entry.time)
        updateStudy(study.copy(entries = study.entries.filterNot { it.id == entry.id } + entry))
    }
    fun deleteEntry(id: String) = updateStudy(study.copy(entries = study.entries.filterNot { it.id == id }))
    fun toggleEntry(id: String) = updateStudy(study.copy(entries = study.entries.map { if (it.id == id) it.copy(done = !it.done) else it }))
    fun refreshPersonalIntegrations() {
        if (accountChanging) return
        val account = activeAccount ?: return
        val snapshot = data
        val state = study
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                SchoolNotifications.schedule(getApplication())
                StudyReminders.reschedule(getApplication())
                hu.grafit.widgets.GrafitWidget.updateAll(getApplication())
                if (account != "demo" && state.calendarSync && snapshot != null) {
                    val current = synchronized(SchoolServices.accountLock) {
                        if (api.profileKey == account) api.calendarSnapshot() else null
                    }
                    if (current?.first != null) {
                        val count = GoogleCalendarSync.sync(getApplication(), account, current.first!!, state, api.syncIssues.isEmpty(), current.second)
                        withContext(Dispatchers.Main) { if (activeAccount == account) toolsMessage = "$count esemény szinkronizálva a kiválasztott naptárba." }
                    }
                }
            }.onFailure { withContext(Dispatchers.Main) { toolsMessage = it.message ?: "A naptárszinkron sikertelen." } }
        }
    }
    fun removeCalendarExports() {
        val account = activeAccount ?: return
        val id = study.calendarId
        updateStudy(study.copy(calendarSync = false))
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { GoogleCalendarSync.removeExports(getApplication(), account, id) } }
                .onSuccess { toolsMessage = "A Grafit által létrehozott események törölve." }.onFailure { toolsMessage = it.message }
        }
    }
    fun switchAccount(key: String) {
        if (busy || profileBusy) return
        work {
            accountChanging = true
            try {
                val old = activeAccount
                studyWrites.withLock {
                    withContext(Dispatchers.IO) {
                        if (old != null) {
                            studyStore.saveAppearance(old, appearance.json())
                            studyStore.saveNotifications(old, notificationOptions.json())
                        }
                        api.switchAccount(key)
                    }
                }
                demo = false; signedIn = true; date = today()
                loadLocalAccount()
                getApplication<Application>().getSystemService(android.app.NotificationManager::class.java).cancelAll()
                SchoolNotifications.schedule(getApplication())
            } finally { accountChanging = false }
            refreshPersonalIntegrations()
        }
    }
    fun forgetAccount(key: String) {
        if (busy || profileBusy || key == activeAccount) return
        work { savedAccounts = withContext(Dispatchers.IO) { api.forgetAccount(key); api.accounts() } }
    }
    private suspend fun loadLocalAccount() {
        val key = requireNotNull(api.profileKey)
        val local = withContext(Dispatchers.IO) { Triple(profiles.read(key), studyStore.read(key), studyStore.appearance(key)) }
        profile = local.first; study = local.second
        notificationOptions = NotificationOptions.parse(withContext(Dispatchers.IO) { studyStore.notifications(key) })
        SchoolNotifications.save(getApplication(), notificationOptions)
        appearance = local.third?.let { Appearance.parse(org.json.JSONObject(it)).resolveCss() } ?: Appearance()
        prefs.edit().putString("configuration", appearance.json()).apply()
        data = withContext(Dispatchers.IO) { api.cached() }
        savedAccounts = withContext(Dispatchers.IO) { api.accounts() }
    }
    private val profiles = ProfileStore(app)
    var profile by mutableStateOf(api.profileKey?.let(profiles::read) ?: LocalProfile())
        private set
    val displayName: String get() = profile.displayName.ifBlank { data?.student.orEmpty() }.ifBlank { "Grafit" }
    private val profileAccount: String get() = if (demo) "demo" else requireNotNull(api.profileKey)
    var appearance by mutableStateOf(Appearance.parse(runCatching {
        (api.profileKey?.let { studyStore.appearance(it) } ?: prefs.getString("configuration", null))?.let { org.json.JSONObject(it) } ?: org.json.JSONObject(prefs.all)
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
    init { viewModelScope.launch { savedAccounts = withContext(Dispatchers.IO) { api.accounts() } }; if (signedIn) syncAll(); SchoolNotifications.save(app, notificationOptions); if (checkUpdatesOnStart) updates.check() }
    /** Bring background-fetched data into the UI without starting another network sync. */
    fun resumeLocalData() {
        if (busy || demo || !signedIn) return
        val account = api.profileKey
        viewModelScope.launch {
            val latest = withContext(Dispatchers.IO) { api.cached() }
            if (!busy && !demo && signedIn && api.profileKey == account && latest != null) { data = latest; refreshPersonalIntegrations() }
        }
    }
    fun customize(value: Appearance) {
        val updated = value.syncCssControls(appearance).resolveCss()
        appearance = updated
        prefs.edit().putString("configuration", updated.json()).apply()
        activeAccount?.let { account -> viewModelScope.launch { studyWrites.withLock { withContext(Dispatchers.IO) { studyStore.saveAppearance(account, updated.json()) } } } }
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
                val localProfile = studyWrites.withLock {
                    withContext(Dispatchers.IO) {
                        activeAccount?.let { studyStore.saveAppearance(it, appearance.json()); studyStore.saveNotifications(it, notificationOptions.json()) }
                        api.login(code, verifier)
                        profiles.read(requireNotNull(api.profileKey))
                    }
                }
                // Expose the account only after its local profile has been loaded.
                profile = localProfile
                signedIn = true; demo = false; data = null
                loadLocalAccount()
                getApplication<Application>().getSystemService(android.app.NotificationManager::class.java).cancelAll()
                SchoolNotifications.schedule(getApplication())
            } finally {
                accountChanging = false
            }
            data = withContext(Dispatchers.IO) { api.load(date, ::publishSync) }
            showSyncIssues()
        }
    }
    private fun publishSync(snapshot: SchoolData) {
        val account = api.profileKey
        viewModelScope.launch { if (!accountChanging && !demo && account == api.profileKey) data = snapshot }
    }
    private fun showSyncIssues() {
        LessonLiveNotification.requestUpdate(getApplication())
        viewModelScope.launch { savedAccounts = withContext(Dispatchers.IO) { api.accounts() } }
        refreshPersonalIntegrations()
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
        study = studyStore.read("demo")
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
                savedAccounts = withContext(Dispatchers.IO) { api.accounts() }
                study = StudyState()
                withContext(Dispatchers.IO) { StudyReminders.reschedule(getApplication()); hu.grafit.widgets.GrafitWidget.updateAll(getApplication()) }
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
