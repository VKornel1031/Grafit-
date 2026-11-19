package hu.grafit.ui

import android.app.Application
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
                      val showGrades: Boolean = true, val showAbsences: Boolean = true, val radius: Float = 24f)
class GrafitViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("appearance", 0)
    private val api = KretaApi(SecureStore(app))
    var appearance by mutableStateOf(Appearance(prefs.getString("mode", "system")!!, prefs.getInt("color", 0),
        prefs.getBoolean("dynamic", true), prefs.getBoolean("compact", false), prefs.getBoolean("hide", false),
        prefs.getBoolean("grades", true), prefs.getBoolean("absences", true), prefs.getFloat("radius", 24f)))
        private set
    var data by mutableStateOf(api.cached()); private set
    var signedIn by mutableStateOf(api.signedIn); private set
    var demo by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var date by mutableStateOf(today()); private set
    init { if (signedIn) reload() }
    fun customize(value: Appearance) {
        appearance = value
        prefs.edit().putString("mode", value.mode).putInt("color", value.color).putBoolean("dynamic", value.dynamic)
            .putBoolean("compact", value.compact).putBoolean("hide", value.hideGrades).putBoolean("grades", value.showGrades)
            .putBoolean("absences", value.showAbsences).putFloat("radius", value.radius).apply()
    }
    private fun work(action: suspend () -> Unit) {
        if (busy) return
        busy = true; error = null
        viewModelScope.launch {
            try { action() } catch (e: Exception) {
                error = if (e is ApiFailure || e is IllegalArgumentException) e.message else "Nem sikerült csatlakozni. Ellenőrizd a kapcsolatot, és próbáld újra."
            } finally { busy = false }
        }
    }
    fun login(code: String, verifier: String) = work {
        withContext(Dispatchers.IO) { api.login(code, verifier) }
        signedIn = true; demo = false; data = null
        data = withContext(Dispatchers.IO) { api.load(date) }
    }
    fun reload() = work { if (!demo) data = withContext(Dispatchers.IO) { api.load(date) } }
    fun selectDate(value: LocalDate) { date = value; if (signedIn && !demo) reload() }
    fun startDemo() { if (busy) return; demo = true; error = null; data = SchoolData.demo() }
    fun logout() = work {
        withContext(Dispatchers.IO) { api.logout() }
        demo = false; signedIn = false; data = null; date = today()
        android.webkit.CookieManager.getInstance().removeAllCookies(null)
        android.webkit.WebStorage.getInstance().deleteAllData()
    }
    fun dismissError() { error = null }
}
