package hu.grafit.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import hu.grafit.data.*
import hu.grafit.notifications.SchoolNotifications

@Composable
internal fun NotificationSettings(vm: GrafitViewModel) {
    val context = LocalContext.current
    val options = vm.notificationOptions
    var allowed by remember { mutableStateOf(SchoolNotifications.canNotify(context)) }
    var denied by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<SchoolEventKind?>(null) }
    LifecycleResumeEffect(Unit) {
        allowed = SchoolNotifications.canNotify(context)
        onPauseOrDispose { }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = SchoolNotifications.canNotify(context)
        denied = !granted
        vm.configureNotifications(vm.notificationOptions.copy(enabled = granted && allowed))
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Értesítések", style = MaterialTheme.typography.titleLarge)
        NotificationToggle("Iskolai értesítések", options.enabled) { enabled ->
            if (!enabled) vm.configureNotifications(options.copy(enabled = false))
            else if (Build.VERSION.SDK_INT >= 33 && !allowed) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            else if (allowed) vm.configureNotifications(options.copy(enabled = true))
            else denied = true
        }
        Text("Új jegyek, házi feladatok és bejelentett dolgozatok ellenőrzése a háttérben is, körülbelül 15 percenként. " +
            "Az Android energiatakarékossága késleltetheti az érkezést. Az első lekérés a meglévő adatokból nem küld értesítést.",
            style = MaterialTheme.typography.bodySmall)
        if (denied || (options.enabled && !allowed)) Text("Az értesítéseket az Android beállításaiban is engedélyezned kell.",
            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }) { Text("Android értesítési beállításai") }
        SchoolEventKind.entries.forEach { kind ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = kind in options.categories, onCheckedChange = { checked ->
                    vm.configureNotifications(options.copy(categories = if (checked) options.categories + kind else options.categories - kind))
                })
                Text(kind.label, Modifier.weight(1f))
                TextButton(onClick = { editing = kind }) { Text("Szöveg") }
            }
        }
        Text("Gyengébb jegy határa: ${options.badGradeThreshold} vagy rosszabb", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..4).forEach { number -> FilterChip(selected = options.badGradeThreshold == number,
                onClick = { vm.configureNotifications(options.copy(badGradeThreshold = number)) }, label = { Text("$number") }) }
        }
        Text("A határ alatti vagy azzal egyenlő jegy a külön gyengébbjegy-szöveget használja; ugyanarról a jegyről egy értesítés érkezik.",
            style = MaterialTheme.typography.bodySmall)
        LiveLessonSettings(vm)
        OutlinedButton(onClick = { SchoolNotifications.post(context, SchoolEvent("preview", SchoolEventKind.Grade,
            mapOf("tantargy" to "Matematika", "jegy" to "5", "tema" to "Mintaértesítés", "tanar" to "Minta tanár", "datum" to today().toString())), options, preview = true) },
            enabled = options.enabled && allowed) { Text("Próbaértesítés") }
    }
    editing?.let { kind ->
        var title by remember(kind) { mutableStateOf(options.title(kind)) }
        var body by remember(kind) { mutableStateOf(options.message(kind)) }
        AlertDialog(onDismissRequest = { editing = null }, title = { Text(kind.label) }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(title, { title = it.take(120) }, label = { Text("Cím") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(body, { body = it.take(500) }, label = { Text("Üzenet") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                Text("Változók: {tantargy}, {datum}, {tema}" + if (kind in setOf(SchoolEventKind.Grade, SchoolEventKind.BadGrade))
                    ", {jegy}, {tanar}" else ", {feladat}", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { title = kind.defaultTitle; body = kind.defaultMessage }) { Text("Alapértelmezett szöveg") }
            }
        }, confirmButton = { TextButton(onClick = {
            vm.configureNotifications(options.copy(titles = options.titles + (kind to title.trim()), messages = options.messages + (kind to body.trim())))
            editing = null
        }, enabled = title.isNotBlank() && body.isNotBlank()) { Text("Mentés") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Mégse") } })
    }
}

@Composable
internal fun NotificationToggle(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}


@Composable
private fun LiveLessonSettings(vm: GrafitViewModel) {
    val options = vm.notificationOptions
    var editing by remember { mutableStateOf(false) }
    HorizontalDivider(Modifier.padding(vertical = 8.dp))
    Text("Élő tanóra", style = MaterialTheme.typography.titleMedium)
    NotificationToggle("Visszaszámlálás a tanóra végéig", options.liveLesson) {
        vm.configureNotifications(options.copy(liveLesson = it))
    }
    Text("Az elmentett órarend alapján működik, a háttérben is. Alapból monokróm. Android 16-tól kérhető élő állapotsávjelzés; " +
        "a pillát és annak megjelenését az Android kezeli. Régebbi rendszeren csendes, folyamatos értesítés látható. " +
        "Az órakezdési jelzés energiatakarékosság miatt késhet; a visszaszámlálás az órarend szerinti időpontot használja.",
        style = MaterialTheme.typography.bodySmall)
    if (options.liveLesson && !options.enabled) Text("A működéshez kapcsold be az iskolai értesítéseket is.", style = MaterialTheme.typography.bodySmall)
    NotificationToggle("Élő állapotsávjelzés kérése", options.livePromoted) {
        vm.configureNotifications(options.copy(livePromoted = it))
    }
    NotificationToggle("Óra előrehaladásának megjelenítése", options.liveProgress) {
        vm.configureNotifications(options.copy(liveProgress = it))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Monokróm", "Kék", "Zöld").forEachIndexed { index, label ->
            FilterChip(selected = options.liveColor == index, onClick = { vm.configureNotifications(options.copy(liveColor = index)) }, label = { Text(label) })
        }
    }
    TextButton(onClick = { editing = true }) { Text("Élő értesítés szövege") }
    if (editing) {
        var title by remember { mutableStateOf(options.liveTitle) }
        var message by remember { mutableStateOf(options.liveMessage) }
        AlertDialog(onDismissRequest = { editing = false }, title = { Text("Tanóra visszaszámlálása") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(title, { title = it.take(120) }, label = { Text("Cím") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(message, { message = it.take(500) }, label = { Text("Üzenet") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                Text("Változók: {tantargy}, {tanar}, {terem}, {tema}, {veg}. A hátralévő időt az Android külön, folyamatosan frissíti.",
                    style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { title = "{tantargy}"; message = "{terem} · {tanar}" }) { Text("Alapértelmezett szöveg") }
            }
        }, confirmButton = { TextButton(onClick = {
            vm.configureNotifications(options.copy(liveTitle = title.trim(), liveMessage = message.trim()))
            editing = false
        }, enabled = title.isNotBlank()) { Text("Mentés") } }, dismissButton = { TextButton(onClick = { editing = false }) { Text("Mégse") } })
    }
}
