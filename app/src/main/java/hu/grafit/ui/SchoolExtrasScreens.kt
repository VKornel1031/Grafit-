package hu.grafit.ui

import android.Manifest
import android.os.Build
import android.text.Html
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import hu.grafit.data.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

internal fun extrasPlain(text: String) = Html.fromHtml(text, Html.FROM_HTML_MODE_COMPACT).toString().trim()
internal fun extraDate(date: LocalDate) = date.format(DateTimeFormatter.ofPattern("MMM d., EEEE", Locale.forLanguageTag("hu-HU")))
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExtraDateButton(label: String, date: LocalDate, onChange: (LocalDate) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }) { Text("$label · ${date}") }
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest = { open = false }, confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }; open = false }) { Text("Kiválasztás") }
        }, dismissButton = { TextButton(onClick = { open = false }) { Text("Mégse") } }) { DatePicker(state) }
    }
}
@Composable
internal fun TimetableChangesScreen(vm: GrafitViewModel) {
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val changes = vm.data?.lessonChanges.orEmpty().sortedByDescending { it.detectedAt }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Órarend-változások", style = MaterialTheme.typography.headlineSmall)
            Text("A már letöltött napok eltérései: terem, tanár, időpont, elmaradás és új órák. Az első letöltés alapállapotot hoz létre.")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Értesítés az új változásokról", Modifier.weight(1f))
                Switch(vm.extras.changesNotify, { enabled ->
                    vm.updateExtras(vm.extras.copy(changesNotify = enabled, notifiedChanges = vm.data?.lessonChanges.orEmpty().map { it.id }.toSet()))
                    if (enabled) {
                        if (!vm.notificationOptions.enabled) vm.configureNotifications(vm.notificationOptions.copy(enabled = true, categories = emptySet()))
                        if (Build.VERSION.SDK_INT >= 33 && !hu.grafit.notifications.SchoolNotifications.canNotify(context)) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                })
            }
            Text("A változásokat a meglévő indításkori, kézi vagy kiválasztott heti szinkron észleli. Ez nem indít új háttérbeli órarend-lekérést.", style = MaterialTheme.typography.bodySmall)
        }
        if (changes.isEmpty()) item { Text("Még nincs észlelt változás. A következő sikeres letöltést a korábban mentett órarendhez hasonlítjuk.") }
        items(changes, key = { it.id }) { change ->
            GrafitCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(change.subject, style = MaterialTheme.typography.titleMedium)
                Text(change.date, color = MaterialTheme.colorScheme.primary)
                Text(change.details)
                Text("Észlelve: " + Instant.ofEpochMilli(change.detectedAt).atZone(schoolZone).format(DateTimeFormatter.ofPattern("MM.dd. HH:mm")), style = MaterialTheme.typography.labelSmall)
            } }
        }
    }
}
@Composable
internal fun CatchUpScreen(vm: GrafitViewModel) {
    var from by rememberSaveable { mutableStateOf(today().minusDays(7).toString()) }
    var to by rememberSaveable { mutableStateOf(today().toString()) }
    var task by remember { mutableStateOf<SchoolTask?>(null) }
    var lesson by remember { mutableStateOf<Lesson?>(null) }
    var entry by remember { mutableStateOf<StudyEntry?>(null) }
    val start = LocalDate.parse(from); val end = LocalDate.parse(to)
    val valid = end >= start && end <= start.plusDays(30)
    val data = vm.data
    val records = remember(data, from, to) { if (valid && data != null) catchUpItems(data, start, end, today()) else emptyList() }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Hiányoztam", style = MaterialTheme.typography.headlineSmall)
            Text("Gyűjtsd össze a kimaradt tananyagot, határidőket és a kapcsolódó tantárgyak következő két heti dolgozatait.")
            ExtraDateButton("Ettől", start, { from = it.toString() })
            ExtraDateButton("Eddig", end, { to = it.toString() })
            if (!valid) Text("Válassz legfeljebb 31 napos, növekvő időszakot.", color = MaterialTheme.colorScheme.error)
            Row {
                TextButton(onClick = { vm.selectDate(start) }, enabled = !vm.busy && !vm.demo) { Text("Kezdő hét betöltése") }
                if (start.minusDays(start.dayOfWeek.value.toLong() - 1) != end.minusDays(end.dayOfWeek.value.toLong() - 1)) TextButton(onClick = { vm.selectDate(end) }, enabled = !vm.busy && !vm.demo) { Text("Záró hét betöltése") }
            }
            Text("Csak a mentett órák és feladatok látszanak. Hosszabb időszaknál a köztes heteket az Órarendben töltheted le. A készre jelölés a saját pótlásodat követi.", style = MaterialTheme.typography.bodySmall)
        }
        if (valid && records.isEmpty()) item { Text("Nincs mentett tananyag vagy feladat erre az időszakra.") }
        items(records, key = { it.key }) { item ->
            GrafitCard(Modifier.fillMaxWidth(), onClick = { task = item.task; lesson = item.lesson }) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(item.key in vm.extras.catchUpDone, { done -> vm.updateExtras(vm.extras.copy(catchUpDone = if (done) vm.extras.catchUpDone + item.key else vm.extras.catchUpDone - item.key)) })
                        Column(Modifier.weight(1f)) { Text(item.title, fontWeight = FontWeight.Medium); Text(item.date, style = MaterialTheme.typography.labelSmall) }
                    }
                    Text(extrasPlain(item.notes), maxLines = 4)
                    TextButton(onClick = {
                        vm.saveEntry(StudyEntry("catchup:${item.key}", "Pótlás: ${item.title}".take(160), item.lesson?.subject ?: item.task?.subject.orEmpty(),
                            today().toString(), kind = "study", notes = extrasPlain(item.notes)))
                    }, enabled = vm.study.entries.none { it.id == "catchup:${item.key}" }) {
                        Text(if (vm.study.entries.any { it.id == "catchup:${item.key}" }) "Tanulási tervhez adva" else "Tanulási tervhez adom")
                    }
                }
            }
        }
    }
    task?.let { MessageDetailDialog(it) { task = null } }
    lesson?.let { LessonDetailDialog(it, vm.data?.tasks.orEmpty()) { lesson = null } }
}
@Composable
internal fun WorkloadScreen(vm: GrafitViewModel) {
    var from by rememberSaveable { mutableStateOf(today().toString()) }
    var chosen by rememberSaveable { mutableStateOf(today().toString()) }
    var task by remember { mutableStateOf<SchoolTask?>(null) }
    var lesson by remember { mutableStateOf<Lesson?>(null) }
    var entry by remember { mutableStateOf<StudyEntry?>(null) }
    val start = LocalDate.parse(from)
    val days = remember(vm.data, vm.study, from) { vm.data?.let { workload(it, vm.study, start) }.orEmpty() }
    val selected = days.firstOrNull { it.date.toString() == chosen }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("Terheléstérkép", style = MaterialTheme.typography.headlineSmall)
            Text("Két hét órái, házi feladatai, dolgozatai és saját tervei egy nézetben.")
            Row {
                TextButton(onClick = { from = start.minusDays(14).toString(); chosen = from }) { Text("‹ Előző") }
                TextButton(onClick = { from = today().toString(); chosen = from }) { Text("Most") }
                TextButton(onClick = { from = start.plusDays(14).toString(); chosen = from }) { Text("Következő ›") }
            }
        }
        items(days.chunked(7)) { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { day ->
                    val color = when { day.score >= 16 -> MaterialTheme.colorScheme.errorContainer; day.score >= 8 -> MaterialTheme.colorScheme.primaryContainer; day.score > 0 -> MaterialTheme.colorScheme.secondaryContainer; else -> MaterialTheme.colorScheme.surfaceContainerLow }
                    Surface(onClick = { chosen = day.date.toString() }, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium, color = color,
                        border = if (chosen == day.date.toString()) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null) {
                        Column(Modifier.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(day.date.format(DateTimeFormatter.ofPattern("EEEEE", Locale.forLanguageTag("hu-HU"))), style = MaterialTheme.typography.labelSmall)
                            Text("${day.date.dayOfMonth}", style = MaterialTheme.typography.titleMedium)
                            Text("${day.score} p", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        item {
            Text("1 óra = 1 pont · házi / saját terv = 2 pont · dolgozat = 4 pont. 8 ponttól közepes, 16-tól sűrű nap. Ez tervezési segítség, nem hivatalos terhelésmérés.", style = MaterialTheme.typography.bodySmall)
            Text("A hiányzó órarendi adatok miatt az üres nap nem feltétlenül szabadnap.", style = MaterialTheme.typography.bodySmall)
        }
        selected?.let { day ->
            item {
                Text(extraDate(day.date), style = MaterialTheme.typography.titleLarge)
                Text("${day.level} · ${day.lessons} óra · ${day.homework} házi · ${day.tests} dolgozat · ${day.personal} saját terv")
                TextButton(onClick = { vm.selectDate(day.date) }, enabled = !vm.busy && !vm.demo) { Text("Kijelölt hét betöltése") }
            }
            if (day.items.isEmpty()) item { Text("Nincs mentett esemény.") }
            items(day.items, key = { it.key }) { item ->
                GrafitCard(Modifier.fillMaxWidth(), onClick = {
                    entry = vm.study.entries.firstOrNull { "personal:${it.id}" == item.key }
                    task = vm.data?.tasks?.firstOrNull { "school:${it.kind}:${taskKey(it)}" == item.key }
                    lesson = vm.data?.lessons?.firstOrNull { "lesson:${it.id}" == item.key }
                }) { Column(Modifier.padding(16.dp)) {
                    Text(item.title, style = MaterialTheme.typography.titleMedium)
                    Text("${item.kind} · ${if (item.allDay) "Egész nap" else item.start.toLocalTime()}")
                    if (item.notes.isNotBlank()) Text(extrasPlain(item.notes), maxLines = 3)
                } }
            }
        }
    }
    entry?.let { EntryEditor(it, { entry = null }, { vm.saveEntry(it); entry = null }, { vm.deleteEntry(it.id); entry = null }) }
    task?.let { MessageDetailDialog(it) { task = null } }
    lesson?.let { LessonDetailDialog(it, vm.data?.tasks.orEmpty()) { lesson = null } }
}
