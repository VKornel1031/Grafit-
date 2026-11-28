package hu.grafit.ui

import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import hu.grafit.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Composable
internal fun ExcusesScreen(vm: GrafitViewModel) {
    var editing by remember { mutableStateOf<ExcuseRecord?>(null) }
    var pendingOnly by remember { mutableStateOf(true) }
    val allDays = (vm.data?.absences.orEmpty().map { it.date } + vm.extras.excuses.map { it.date }).distinct().filter { runCatching { LocalDate.parse(it) }.isSuccess }.sortedDescending()
    val days = if (pendingOnly) allDays.filter { excuseStillNeeded(vm.data, it) } else allDays
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Igazoláskövető", style = MaterialTheme.typography.headlineSmall)
            Text("Határidő, saját állapot, jegyzet és titkosított melléklet a hiányzásokhoz. Az iskolai szabályok szerint te állítod be a határidőt.")
            Text("A saját jelölés és a csatolmány nem küld be igazolást a KRÉTA-ba. A hivatalos állapotot külön mutatjuk.", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(pendingOnly, { pendingOnly = true }, label = { Text("Intézendők") })
                FilterChip(!pendingOnly, { pendingOnly = false }, label = { Text("Összes") })
            }
        }
        if (days.isEmpty()) item { Text(if (pendingOnly) "Nincs intézendő mentett hiányzás." else "Nincs mentett hiányzás.") }
        items(days, key = { it }) { day ->
            val official = vm.data?.absences.orEmpty().filter { it.date == day }
            val record = vm.extras.excuses.firstOrNull { it.date == day } ?: ExcuseRecord(day)
            val overdue = record.status != "submitted" && record.deadline.isNotBlank() && runCatching { LocalDate.parse(record.deadline) < today() }.getOrDefault(false)
            GrafitCard(Modifier.fillMaxWidth(), onClick = { editing = record }) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(day, style = MaterialTheme.typography.titleMedium)
                Text(if (official.isEmpty()) "Nincs jelenleg mentett KRÉTA-sor." else "KRÉTA: " + official.groupingBy { it.state }.eachCount().entries.joinToString { "${it.key}: ${it.value}" })
                Text("Saját állapot: ${excuseStatus(record.status)}")
                if (record.deadline.isNotBlank()) Text("Határidő: ${record.deadline}" + if (overdue) " · lejárt" else "", color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                if (record.attachment.isNotBlank()) Text("Melléklet: ${record.fileName}", style = MaterialTheme.typography.bodySmall)
                if (record.notes.isNotBlank()) Text(record.notes, maxLines = 2)
            } }
        }
    }
    editing?.let { original ->
        key(original.date) { ExcuseEditor(vm, original, onDismiss = { editing = null }) }
    }
}
private fun excuseStatus(status: String) = when(status) { "prepared" -> "Előkészítve"; "submitted" -> "Átadtam / beküldtem"; else -> "Intézendő" }
@Composable
private fun ExcuseEditor(vm: GrafitViewModel, initial: ExcuseRecord, onDismiss: () -> Unit) {
    var draft by remember { mutableStateOf(initial) }
    val imported = remember { mutableListOf<String>() }
    var disposed by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose {
        disposed = true
        if (!saved) imported.forEach(vm::discardToolFile)
    } }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importToolFile(uri) { attachment ->
            if (disposed) { vm.discardToolFile(attachment.id); return@importToolFile }
            imported += attachment.id
            draft = draft.copy(attachment = attachment.id, fileName = attachment.name, mime = attachment.mime)
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun dismiss(keep: Boolean) {
        if (vm.extrasBusy) return
        saved = keep
        imported.filter { !keep || it != draft.attachment }.forEach(vm::discardToolFile)
        onDismiss()
    }
    AlertDialog(onDismissRequest = { dismiss(false) }, title = { Text("Igazolás · ${initial.date}") }, text = {
        Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Saját nyilvántartás · nem hivatalos KRÉTA-állapot", style = MaterialTheme.typography.labelMedium)
            listOf("pending", "prepared", "submitted").forEach { status ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(draft.status == status, { draft = draft.copy(status = status) }); Text(excuseStatus(status))
                }
            }
            ExtraDateButton("Határidő", runCatching { LocalDate.parse(draft.deadline) }.getOrDefault(today().plusDays(7)), { draft = draft.copy(deadline = it.toString()) })
            if (draft.deadline.isNotBlank()) TextButton(onClick = { draft = draft.copy(deadline = "", remind = false) }) { Text("Határidő törlése") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Emlékeztető a határidő napján 08:00-kor", Modifier.weight(1f))
                Switch(draft.remind, { enabled ->
                    draft = draft.copy(remind = enabled, deadline = if (enabled && draft.deadline.isBlank()) today().plusDays(7).toString() else draft.deadline)
                    if (enabled && Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }, enabled = draft.status != "submitted" && excuseStillNeeded(vm.data, draft.date))
            }
            OutlinedTextField(draft.notes, { if (it.length <= 4000) draft = draft.copy(notes = it) }, label = { Text("Saját jegyzet") }, modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = { picker.launch(arrayOf("application/pdf", "image/*")) }, enabled = !vm.extrasBusy) { Text("Kép vagy PDF csatolása · max. 8 MB") }
            if (vm.extrasBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (draft.attachment.isNotBlank()) {
                Text(draft.fileName)
                TextButton(onClick = {
                    val account = vm.activeAccount ?: return@TextButton
                    val attachment = draft.attachment; val mime = draft.mime
                    scope.launch {
                        runCatching {
                            val file = withContext(Dispatchers.IO) { vm.extrasStore.export(account, attachment, mime) }
                            if (vm.activeAccount == account) {
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                                context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply { clipData = ClipData.newRawUri("Igazolás", uri) })
                            }
                        }.onFailure { error = "Nem nyitható meg a melléklet. Telepíts a fájltípushoz megjelenítőt." }
                    }
                }) { Text("Melléklet megnyitása") }
                TextButton(onClick = { draft = draft.copy(attachment = "", fileName = "", mime = "") }) { Text("Melléklet eltávolítása") }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onClick = { vm.saveExcuse(draft); dismiss(true) }, enabled = !vm.extrasBusy) { Text("Mentés") } },
        dismissButton = { TextButton(onClick = { dismiss(false) }, enabled = !vm.extrasBusy) { Text("Mégse") } })
}
