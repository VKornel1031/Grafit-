package hu.grafit.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
internal fun UpdatePrompt(updates: UpdateController) {
    val context = LocalContext.current
    var installWhenReady by rememberSaveable { mutableStateOf(false) }
    fun install() {
        runCatching { context.startActivity(updates.installerIntent()) }
            .onFailure { updates.reportError(it.message ?: "Az Android telepítője nem nyitható meg.") }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (updates.canInstall()) install() else updates.reportError("A telepítési engedély nem lett megadva. A Telepítés gombbal újra megpróbálhatod.")
    }
    fun requestInstall() {
        if (updates.canInstall()) install()
        else runCatching { permission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))) }
            .onFailure { updates.reportError("Nyisd meg az Android beállításaiban a Grafit → Ismeretlen alkalmazások telepítése engedélyt.") }
    }
    LaunchedEffect(updates.file, installWhenReady, updates.dismissed) {
        if (updates.dismissed) installWhenReady = false
        else if (updates.file != null && installWhenReady) { installWhenReady = false; requestInstall() }
    }
    val release = updates.release
    if (release != null && !updates.dismissed) AlertDialog(onDismissRequest = updates::dismiss,
        title = { Text("Elérhető a Grafit ${release.version}") }, text = {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Telepített változat: ${updates.version}")
                if (release.notes.isNotBlank()) Text(release.notes, style = MaterialTheme.typography.bodySmall)
                Text("A frissítés a GitHub-kiadásból töltődik le. A telepítést az Android rendszerablakában kell jóváhagyni; az adataid megmaradnak.", style = MaterialTheme.typography.bodySmall)
                if (updates.downloading) {
                    LinearProgressIndicator(progress = { updates.progress }, modifier = Modifier.fillMaxWidth())
                    Text("Letöltés és ellenőrzés · ${(updates.progress * 100).toInt()}%")
                }
                updates.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }, confirmButton = { TextButton(enabled = !updates.downloading, onClick = {
            if (updates.file != null) requestInstall() else { installWhenReady = true; updates.download() }
        }) { Text(if (updates.file != null) "Telepítés" else "Letöltés és frissítés") } },
        dismissButton = { TextButton(onClick = updates::dismiss) { Text(if (updates.downloading) "Letöltés megszakítása" else "Később") } })
}
