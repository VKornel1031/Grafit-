package hu.grafit.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import hu.grafit.data.releasePageUrl

@Composable
internal fun UpdatePrompt(updates: UpdateController) {
    val context = LocalContext.current
    val release = updates.release
    if (release != null && !updates.dismissed) AlertDialog(onDismissRequest = updates::dismiss,
        title = { Text("Elérhető a Grafit ${release.version}") }, text = {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Telepített változat: ${updates.version}")
                if (release.notes.isNotBlank()) Text(release.notes, style = MaterialTheme.typography.bodySmall)
                Text("A gomb megnyitja a hivatalos GitHub-kiadást a böngésződben. Onnan töltheted le az APK-t; a telepítést az Android kezeli. A Grafit nem kér alkalmazástelepítési engedélyt.",
                    style = MaterialTheme.typography.bodySmall)
                updates.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }, confirmButton = { TextButton(onClick = {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(releasePageUrl(release.version)))
                    .addCategory(Intent.CATEGORY_BROWSABLE))
            }.onFailure { updates.reportError("A böngésző nem nyitható meg. Próbáld újra egy telepített böngészővel.") }
        }) { Text("GitHub letöltési oldal") } },
        dismissButton = { TextButton(onClick = updates::dismiss) { Text("Később") } })
}
