package hu.grafit.ui

import android.app.Application
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import androidx.compose.runtime.*
import hu.grafit.data.*
import kotlinx.coroutines.*
import java.io.File
import java.security.MessageDigest

internal class UpdateController(private val app: Application, private val scope: CoroutineScope) {
    private val service = AppUpdates()
    private var downloadJob: Job? = null
    var release by mutableStateOf<AppRelease?>(null); private set
    var checking by mutableStateOf(false); private set
    var downloading by mutableStateOf(false); private set
    var progress by mutableStateOf(0f); private set
    var message by mutableStateOf<String?>(null); private set
    var error by mutableStateOf<String?>(null); private set
    var file by mutableStateOf<File?>(null); private set
    var dismissed by mutableStateOf(false); private set
    val version: String by lazy { installedInfo().versionName ?: "0.0.0" }

    @Suppress("DEPRECATION")
    private fun installedInfo(): PackageInfo = app.packageManager.getPackageInfo(app.packageName, signatureFlags())
    @Suppress("DEPRECATION")
    private fun signatureFlags() = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
    @Suppress("DEPRECATION")
    private fun identity(info: PackageInfo): ApkIdentity {
        val signers = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
        return ApkIdentity(info.packageName, if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong(),
            signers.orEmpty().map { signature -> MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
                .joinToString("") { "%02x".format(it) } }.toSet())
    }

    fun check(manual: Boolean = false) {
        if (checking || downloading) return
        checking = true; error = null; message = null
        scope.launch {
            try {
                val next = withContext(Dispatchers.IO) { service.latest(version) }
                if (next != release) file = null
                release = next
                dismissed = false
                if (manual && next == null) message = "Nincs újabb, telepíthető stabil GitHub-kiadás."
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (manual) message = "A frissítés nem ellenőrizhető. Ellenőrizd a kapcsolatot, és próbáld újra." }
            finally { checking = false }
        }
    }
    fun dismiss() { downloadJob?.cancel(); dismissed = true }
    fun reportError(value: String) { error = value }
    fun download() {
        val next = release ?: return
        if (downloading) return
        downloading = true; progress = 0f; error = null; file = null
        downloadJob = scope.launch {
            try {
                val apk = service.download(next, File(app.cacheDir, "updates")) { value -> scope.launch { progress = value } }
                try {
                    withContext(Dispatchers.IO) {
                        @Suppress("DEPRECATION")
                        val candidate = app.packageManager.getPackageArchiveInfo(apk.absolutePath, signatureFlags())
                            ?: throw IllegalArgumentException("A letöltött fájl nem olvasható Android-alkalmazásként.")
                        validateUpdate(identity(installedInfo()), identity(candidate))
                    }
                    file = apk
                } catch (e: Exception) { apk.delete(); throw e }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = if (e is IllegalArgumentException || e is java.io.IOException) e.message else "Nem sikerült letölteni a frissítést. Próbáld újra." }
            finally { downloading = false; downloadJob = null }
        }
    }
    fun canInstall(): Boolean = app.packageManager.canRequestPackageInstalls()
    fun installerIntent(): Intent {
        val apk = requireNotNull(file) { "Az APK még nem érhető el." }
        require(apk.isFile) { "A frissítési fájl már nem érhető el. Töltsd le újra." }
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.updates", apk)
        return Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
