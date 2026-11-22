package hu.grafit.ui

import android.app.Application
import android.content.pm.PackageInfo
import androidx.compose.runtime.*
import hu.grafit.data.*
import kotlinx.coroutines.*

internal class UpdateController(private val app: Application, private val scope: CoroutineScope) {
    private val service = AppUpdates()
    var release by mutableStateOf<AppRelease?>(null); private set
    var checking by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null); private set
    var error by mutableStateOf<String?>(null); private set
    var dismissed by mutableStateOf(false); private set
    val version: String by lazy { installedInfo().versionName ?: "0.0.0" }

    @Suppress("DEPRECATION")
    private fun installedInfo(): PackageInfo = app.packageManager.getPackageInfo(app.packageName, 0)

    fun check(manual: Boolean = false) {
        if (checking) return
        checking = true; error = null; message = null
        scope.launch {
            try {
                val next = withContext(Dispatchers.IO) { service.latest(version, installedInfo().let { if (android.os.Build.VERSION.SDK_INT >= 28) it.longVersionCode else @Suppress("DEPRECATION") it.versionCode.toLong() }) }
                release = next
                dismissed = false
                if (manual && next == null) message = if (service.noPublicReleases)
                    "Nincs nyilvánosan elérhető GitHub-kiadás. A repository és a közzétett Release legyen nyilvános."
                else service.missingApkVersion?.let {
                    "Elérhető a(z) $it kiadás, de nincs hozzá telepíthető APK-melléklet."
                } ?: "Nincs újabb, telepíthető stabil GitHub-kiadás. Azonos verziószámú APK nem számít új frissítésnek."
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (manual) message = "A frissítés nem ellenőrizhető. Ellenőrizd a kapcsolatot, és próbáld újra." }
            finally { checking = false }
        }
    }
    fun dismiss() { dismissed = true }
    fun reportError(value: String) { error = value }
}
