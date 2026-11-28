package hu.grafit.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import hu.grafit.data.*
import hu.grafit.integrations.GoogleCalendarSync
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

private val toolTitles = linkedMapOf("simulator" to "Mi lenne, ha?", "planner" to "Tanulási terv", "stats" to "Statisztikák",
    "calendar" to "Naptár", "accounts" to "Fiókok", "search" to "Keresés", "prep" to "Dolgozatfelkészülés", "widgets" to "Widgetek")
private fun plain(value: String) = android.text.Html.fromHtml(value, android.text.Html.FROM_HTML_MODE_COMPACT).toString().trim()
@Composable
internal fun ToolsScreen(vm: GrafitViewModel, onAddAccount: () -> Unit) {
    var tool by rememberSaveable { mutableStateOf<String?>(null) }
    val activity = androidx.activity.compose.LocalActivity.current
    LaunchedEffect(Unit) { activity?.intent?.getStringExtra("tool")?.takeIf { it in toolTitles }?.let { tool = it; activity.intent.removeExtra("tool") } }
    BackHandler(tool != null) { tool = null }
    Column(Modifier.fillMaxSize()) {
        if (tool != null) TextButton(onClick = { tool = null }, modifier = Modifier.padding(horizontal = 12.dp)) { Text("‹ Eszközök · ${toolTitles[tool]}") }
        vm.toolsMessage?.let { Text(it, Modifier.padding(horizontal = 20.dp, vertical = 6.dp), style = MaterialTheme.typography.bodySmall) }
        when (tool) {
            "simulator" -> SimulatorScreen(vm)
            "planner" -> PlannerScreen(vm)
            "stats" -> StatisticsScreen(vm)
            "calendar" -> CalendarScreen(vm)
            "accounts" -> AccountsScreen(vm, onAddAccount)
            "search" -> SearchScreen(vm)
            "prep" -> PreparationScreen(vm)
            "widgets" -> WidgetSettings(vm)
            else -> LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Text("A te iskolai eszköztárad", style = MaterialTheme.typography.headlineSmall) }
                val descriptions = listOf("Próbajegyek, célátlag és ösztöndíj", "Saját feladatok, tanulási blokkok és emlékeztetők", "Havi trendek, tantárgyak és hiányzások",
                    "Órák, határidők, saját események és Google Naptár", "Titkosított, külön tárolt KRÉTA-fiókok", "Keresés az összes mentett iskolai adatban", "Jegyzetek, tananyag és felkészülési lista", "Android-kezdőképernyős widgetek")
                items(toolTitles.entries.toList()) { entry -> GrafitCard(onClick = { tool = entry.key }) {
                    Column(Modifier.padding(20.dp)) { Text(entry.value, style = MaterialTheme.typography.titleMedium); Text(descriptions[toolTitles.keys.indexOf(entry.key)], style = MaterialTheme.typography.bodySmall) }
                } }
            }
        }
    }
}

@Composable
private fun SimulatorScreen(vm: GrafitViewModel) {
    val data = vm.data ?: return
    var reveal by remember { mutableStateOf(!vm.appearance.hideGrades) }
    if (!reveal) { Button(onClick = { reveal = true }, modifier = Modifier.padding(20.dp)) { Text("Jegyszimulátor felfedése") }; return }
    val subjects = data.gradeSummaries()
    var selected by rememberSaveable { mutableStateOf(subjects.firstOrNull()?.name.orEmpty()) }
    var number by rememberSaveable { mutableIntStateOf(5) }
    var weight by rememberSaveable { mutableFloatStateOf(100f) }
    var target by remember(selected) { mutableFloatStateOf(vm.study.goals[selected]?.toFloat() ?: 4f) }
    val simulated = simulatedSubjects(data, vm.study.simulations)
    val group = simulated.firstOrNull { it.name == selected }
    var menu by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Mi lenne, ha?", style = MaterialTheme.typography.headlineSmall); Text("A próbajegyek csak a saját készülékeden szerepelnek. A KRÉTA jegyeit nem módosítják.") }
        item {
            Box { OutlinedButton(onClick = { menu = true }) { Text(selected.ifBlank { "Nincs tantárgy" }) }
                DropdownMenu(menu, { menu = false }) { subjects.forEach { DropdownMenuItem(text = { Text(it.name) }, onClick = { selected = it.name; menu = false }) } } }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { (1..5).forEach { n -> FilterChip(selected = number == n, onClick = { number = n }, label = { Text("$n") }) } }
            Text("Jegysúly: ${weight.toInt()}%")
            Slider(weight, { weight = it }, valueRange = 10f..300f, steps = 28)
            Button(onClick = { vm.updateStudy(vm.study.copy(simulations = vm.study.simulations + SimulatedGrade(selected, number, weight.toInt()))) }, enabled = selected.isNotBlank()) { Text("Próbajegy hozzáadása") }
        }
        item {
            val actual = subjects.firstOrNull { it.name == selected }?.grades?.let(::average)
            val predicted = group?.grades?.let(::average)
            val annual = annualAverageOfSubjects(simulated)
            Text("Tantárgyi átlag: ${formatAverage(actual)} → ${formatAverage(predicted)}", style = MaterialTheme.typography.titleLarge)
            Text("Éves próbált átlag: ${formatAverage(annual)}")
            Text("Ösztöndíjbecslés: ${annual?.let(::technikumScholarshipEstimate)?.let { "$it Ft / hó" } ?: "—"}")
            Text("A hivatalos ösztöndíj a jogosultságtól és az előző tanév év végi átlagától függ.", style = MaterialTheme.typography.bodySmall)
        }
        item {
            Text("Célátlag: ${"%.2f".format(Locale.ROOT, target)}")
            Slider(target, { target = it }, valueRange = 1f..5f, steps = 15, onValueChangeFinished = { vm.updateStudy(vm.study.copy(goals = vm.study.goals + (selected to target.toDouble()))) })
            val needed = gradesNeeded(group?.grades.orEmpty(), target.toDouble(), number, weight.toInt())
            Text(when (needed) { null -> "Ezzel a jeggyel és súllyal a cél nem érhető el."; 0 -> "A célátlagot már eléred."; else -> "$needed további $number-ös jegy kellene (${weight.toInt()}% súly)." })
        }
        items(vm.study.simulations.withIndex().toList()) { indexed ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${indexed.value.subject}: ${indexed.value.value} · ${indexed.value.weight}%", Modifier.weight(1f))
                TextButton(onClick = { vm.updateStudy(vm.study.copy(simulations = vm.study.simulations.filterIndexed { i, _ -> i != indexed.index })) }) { Text("Törlés") }
            }
        }
        item { OutlinedButton(onClick = { vm.updateStudy(vm.study.copy(simulations = emptyList())) }) { Text("Próbajegyek törlése") } }
    }
}
private fun formatAverage(value: Double?) = value?.let { "%.2f".format(Locale.forLanguageTag("hu-HU"), it) } ?: "—"

@Composable
private fun PlannerScreen(vm: GrafitViewModel) {
    var week by rememberSaveable { mutableStateOf(today().toString()) }
    var editor by remember { mutableStateOf<StudyEntry?>(null) }
    val monday = LocalDate.parse(week).let { it.minusDays((it.dayOfWeek.value - 1).toLong()) }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Tanulási terv", style = MaterialTheme.typography.headlineSmall)
            Row { TextButton(onClick = { week = monday.minusWeeks(1).toString() }) { Text("‹ Előző") }; TextButton(onClick = { week = today().toString() }) { Text("Ez a hét") }; TextButton(onClick = { week = monday.plusWeeks(1).toString() }) { Text("Következő ›") } }
            Button(onClick = { editor = newEntry(if (today() in monday..monday.plusDays(6)) today() else monday) }) { Text("Saját feladat / tanulás hozzáadása") }
        }
        (0L..6).forEach { offset ->
            val day = monday.plusDays(offset)
            item { Text(day.format(DateTimeFormatter.ofPattern("MMM d., EEEE", Locale.forLanguageTag("hu-HU"))), style = MaterialTheme.typography.titleMedium) }
            val entries = vm.study.entries.filter { it.date == day.toString() && it.kind != "checklist" }.sortedBy { it.time }
            if (entries.isEmpty()) item { Text("Nincs saját terv.", style = MaterialTheme.typography.bodySmall) }
            items(entries, key = { it.id }) { entry -> EntryRow(entry, { vm.toggleEntry(entry.id) }, { editor = entry }) }
            items(vm.data?.tasks.orEmpty().filter { it.date == day.toString() && it.kind in setOf("Házi feladat", "Dolgozat") }) { task ->
                GrafitCard { Column(Modifier.padding(14.dp)) {
                    Text("KRÉTA · ${task.kind} · ${task.subject}", fontWeight = FontWeight.Medium)
                    Text(plain(task.text), maxLines = 3)
                    TextButton(onClick = { editor = newEntry(day).copy(title = "${task.kind}: ${task.subject}".take(160), subject = task.subject, notes = plain(task.text), kind = "study", examKey = if (task.kind == "Dolgozat") taskKey(task) else "") }) { Text("Tanulást tervezek hozzá") }
                } }
            }
        }
    }
    editor?.let { EntryEditor(it, { editor = null }, { vm.saveEntry(it); editor = null }, { vm.deleteEntry(it.id); editor = null }) }
}
private fun newEntry(day: LocalDate = today()) = StudyEntry(UUID.randomUUID().toString(), "", date = day.toString())
@Composable
private fun EntryRow(entry: StudyEntry, onToggle: () -> Unit, onEdit: () -> Unit) {
    GrafitCard(onClick = onEdit) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(entry.done, { onToggle() })
        Column(Modifier.weight(1f)) { Text(entry.title, fontWeight = FontWeight.Medium); Text("${entry.time} · ${entry.subject} · ${entry.minutes} perc", style = MaterialTheme.typography.bodySmall) }
        Text("›")
    } }
}
@Composable
private fun EntryEditor(initial: StudyEntry, onDismiss: () -> Unit, onSave: (StudyEntry) -> Unit, onDelete: () -> Unit) {
    var title by remember(initial.id) { mutableStateOf(initial.title) }
    var subject by remember(initial.id) { mutableStateOf(initial.subject) }
    var date by remember(initial.id) { mutableStateOf(initial.date) }
    var time by remember(initial.id) { mutableStateOf(initial.time) }
    var minutes by remember(initial.id) { mutableStateOf(initial.minutes.toString()) }
    var notes by remember(initial.id) { mutableStateOf(initial.notes) }
    var kind by remember(initial.id) { mutableStateOf(initial.kind) }
    var reminder by remember(initial.id) { mutableIntStateOf(initial.reminder) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val valid = title.isNotBlank() && runCatching { LocalDate.parse(date); if (time.isNotBlank()) LocalTime.parse(time); require(minutes.toInt() in 5..720) }.isSuccess
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Saját terv") }, text = {
        Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(title, { title = it.take(160) }, label = { Text("Cím") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(subject, { subject = it.take(120) }, label = { Text("Tantárgy (opcionális)") }, modifier = Modifier.fillMaxWidth())
            Row(Modifier.horizontalScroll(rememberScrollState())) { (listOf("task" to "Feladat", "study" to "Tanulás", "event" to "Esemény") + if (initial.examKey.isNotBlank()) listOf("checklist" to "Ellenőrzőpont") else emptyList()).forEach { (id, text) -> FilterChip(kind == id, { kind = id }, label = { Text(text) }) } }
            OutlinedTextField(date, { date = it }, label = { Text("Dátum: ÉÉÉÉ-HH-NN") }, singleLine = true)
            OutlinedTextField(time, { time = it }, label = { Text("Időpont: ÓÓ:PP (üres = egész nap)") }, singleLine = true)
            OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit).take(3) }, label = { Text("Időtartam percben (5–720)") }, singleLine = true)
            OutlinedTextField(notes, { notes = it.take(12000) }, label = { Text("Jegyzet") }, minLines = 3)
            Text("Emlékeztető")
            Row(Modifier.horizontalScroll(rememberScrollState())) { listOf(-1 to "Nincs", 0 to "Kezdéskor", 15 to "15 perccel", 60 to "1 órával").forEach { (value, label) ->
                FilterChip(reminder == value, {
                    reminder = value
                    if (value >= 0 && Build.VERSION.SDK_INT >= 33 && !hu.grafit.notifications.SchoolNotifications.canNotify(context)) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }, label = { Text(label) })
            } }
            Text("Az Android energiatakarékossága késleltetheti az emlékeztetőt. Időpont nélkül 08:00 az alap.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onDelete) { Text("Terv törlése") }
        }
    }, confirmButton = { TextButton(enabled = valid, onClick = { onSave(initial.copy(title = title.trim(), subject = subject.trim(), date = date, time = time, minutes = minutes.toInt(), kind = kind, notes = notes, reminder = reminder)) }) { Text("Mentés") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Mégse") } })
}

@Composable
private fun StatisticsScreen(vm: GrafitViewModel) {
    val data = vm.data ?: return
    var reveal by remember { mutableStateOf(!vm.appearance.hideGrades) }
    val groups = data.gradeSummaries().filter { average(it.grades) != null }.sortedByDescending { average(it.grades) }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Statisztikák", style = MaterialTheme.typography.headlineSmall) }
        if (!reveal) item { Button(onClick = { reveal = true }) { Text("Jegystatisztikák felfedése") } }
        else {
            item { Text("Éves átlag: ${formatAverage(data.annualAverage())}", style = MaterialTheme.typography.titleLarge)
                Text("Erősség: ${groups.firstOrNull()?.name ?: "—"} · Fejlesztendő: ${groups.lastOrNull()?.name ?: "—"}") }
            item { Text("Havi súlyozott átlag", style = MaterialTheme.typography.titleMedium) }
            items(monthlyGradeStats(data.grades)) { month -> StatisticBar(month.month, month.average, "${month.count} jegy") }
            item { Text("Tantárgyi átlagok", style = MaterialTheme.typography.titleMedium) }
            items(groups) { StatisticBar(it.name, average(it.grades), "${it.grades.count { g -> g.number != null }} jegy") }
            item { Text("Jegyeloszlás", style = MaterialTheme.typography.titleMedium)
                (1..5).forEach { number ->
                    val count = data.grades.count { it.number == number && it.valueType == "Osztalyzat" }
                    Text("$number: $count db")
                    LinearProgressIndicator(progress = { count.toFloat() / data.grades.count { it.number != null && it.valueType == "Osztalyzat" }.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        item { Text("Hiányzások", style = MaterialTheme.typography.titleMedium)
            Text("Igazolt: ${data.absences.count { it.state == "Igazolt" }} · Igazolatlan: ${data.absences.count { it.state == "Igazolatlan" }}")
            Text("Igazolásra vár: ${data.absences.count { it.state == "Igazolando" }} · Késés összesen: ${data.absences.sumOf { it.minutes }} perc")
        }
        items(data.absences.filter { it.date.length >= 7 }.groupBy { it.date.take(7) }.toSortedMap().entries.toList()) { Text("${it.key}: ${it.value.size} mulasztás · ${it.value.sumOf { a -> a.minutes }} perc késés") }
    }
}
@Composable
private fun StatisticBar(title: String, value: Double?, detail: String) {
    Column { Text("$title · ${formatAverage(value)}", fontWeight = FontWeight.Medium)
        LinearProgressIndicator(progress = { ((value ?: 0.0) / 5.0).toFloat() }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
        Text(detail, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun CalendarScreen(vm: GrafitViewModel) {
    var date by rememberSaveable { mutableStateOf(today().toString()) }
    var mode by rememberSaveable { mutableStateOf("month") }
    var editor by remember { mutableStateOf<StudyEntry?>(null) }
    var detail by remember { mutableStateOf<CalendarItem?>(null) }
    var remove by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.loadCalendars() }
    val selected = LocalDate.parse(date)
    val all = remember(vm.data, vm.study) { vm.data?.let { calendarItems(it, vm.study) }.orEmpty() }
    LaunchedEffect(Unit) { vm.loadCalendars() }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Naptár", style = MaterialTheme.typography.headlineSmall)
            Row { listOf("day" to "Nap", "week" to "Hét", "month" to "Hónap").forEach { (id, title) -> FilterChip(mode == id, { mode = id }, label = { Text(title) }) } }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { date = when(mode) { "month" -> selected.minusMonths(1); "week" -> selected.minusWeeks(1); else -> selected.minusDays(1) }.toString() }) { Text("‹") }
                TextButton(onClick = { date = today().toString() }, modifier = Modifier.weight(1f)) { Text(selected.format(DateTimeFormatter.ofPattern("yyyy. MMMM d.", Locale.forLanguageTag("hu-HU")))) }
                TextButton(onClick = { date = when(mode) { "month" -> selected.plusMonths(1); "week" -> selected.plusWeeks(1); else -> selected.plusDays(1) }.toString() }) { Text("›") }
            }
            val first = if (mode == "month") selected.withDayOfMonth(1).let { it.minusDays((it.dayOfWeek.value - 1).toLong()) } else selected.minusDays((selected.dayOfWeek.value - 1).toLong())
            if (mode != "day") {
                Row { listOf("H", "K", "Sze", "Cs", "P", "Szo", "V").forEach { Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall) } }
                repeat(if (mode == "month") 6 else 1) { week -> Row(Modifier.fillMaxWidth()) {
                    repeat(7) { weekday ->
                        val day = first.plusDays((week * 7 + weekday).toLong())
                        val hasEvents = all.any { it.start.toLocalDate() == day }
                        Surface(onClick = { date = day.toString() }, modifier = Modifier.weight(1f).padding(1.dp), shape = CircleShape,
                            color = if (day == selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent) {
                            Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(day.dayOfMonth.toString(), color = if (day.month != selected.month && mode == "month") MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                                Text(if (hasEvents) "•" else " ", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                } }
            }
            Text("A naptár az elmentett órákat és feladatokat mutatja.", style = MaterialTheme.typography.bodySmall)
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                OutlinedButton(onClick = { vm.selectDate(selected) }, enabled = !vm.busy && !vm.demo) { Text("Kijelölt hét letöltése") }
                TextButton(onClick = { editor = newEntry(selected).copy(kind = "event") }) { Text("Saját esemény") }
            }
        }
        val rangeStart = if (mode == "week") selected.minusDays((selected.dayOfWeek.value - 1).toLong()) else selected
        val rangeEnd = if (mode == "week") rangeStart.plusDays(7) else rangeStart.plusDays(1)
        val shown = all.filter { it.start.toLocalDate() >= rangeStart && it.start.toLocalDate() < rangeEnd }
        if (shown.isEmpty()) item { Text("Erre az időszakra nincs mentett esemény.") }
        items(shown, key = { it.key }) { item -> GrafitCard(onClick = { detail = item }) { Column(Modifier.padding(16.dp)) {
            Text(item.title, fontWeight = FontWeight.Medium)
            Text("${item.start.toLocalDate()} · ${if (item.allDay) "Egész nap" else item.start.toLocalTime().toString()} · ${item.kind}", style = MaterialTheme.typography.bodySmall)
        } } }
        item {
            HorizontalDivider()
            Text("Google Naptár / készüléknaptár", style = MaterialTheme.typography.titleMedium)
            if (vm.demo) Text("Bemutató módból nem exportálunk valódi naptárba.")
            Text("Válassz egy Google-fiókhoz tartozó, írható naptárat. Az Android Google-szinkronja továbbítja az eseményeket a felhőbe. A következő 90 nap mentett adatai egyirányúan frissülnek; más alkalmazás eseményeit nem módosítjuk. Engedélyezéskor a háttérben is ellenőrizzük az új feladatokat körülbelül 15 percenként. Naptárváltás előtt szükség esetén távolítsd el a régi naptár Grafit-eseményeit.", style = MaterialTheme.typography.bodySmall)
            if (!GoogleCalendarSync.permitted(context)) Button(onClick = { permission.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)) }) { Text("Naptárengedély kérése") }
            else {
                if (vm.calendars.isEmpty()) Text("Nem található írható naptár. Kapcsold be a Google Naptár szinkronját az Android fiókbeállításaiban.")
                vm.calendars.forEach { calendar ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(vm.study.calendarId == calendar.id, { vm.updateStudy(vm.study.copy(calendarId = calendar.id, calendarSync = false)) })
                        Text("${if (calendar.google) "Google · " else ""}${calendar.name}\n${calendar.account}", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text("Exportált kategóriák", style = MaterialTheme.typography.titleSmall)
                listOf("Órarend", "Házi feladat", "Dolgozat", "Tanulás", "Saját feladat", "Saját esemény").forEach { kind ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(kind in vm.study.calendarKinds, { checked -> vm.updateStudy(vm.study.copy(calendarKinds = if (checked) vm.study.calendarKinds + kind else vm.study.calendarKinds - kind)) })
                        Text(kind)
                    }
                }
                NotificationToggle("Automatikus export engedélyezése", vm.study.calendarSync) { enabled -> vm.updateStudy(vm.study.copy(calendarSync = enabled && vm.study.calendarId >= 0 && !vm.demo)) }
                OutlinedButton(onClick = vm::refreshPersonalIntegrations, enabled = vm.study.calendarSync) { Text("Szinkronizálás most") }
                TextButton(onClick = { remove = true }, enabled = vm.study.calendarId >= 0) { Text("Grafit-események eltávolítása ebből a naptárból") }
            }
        }
    }
    detail?.let { item -> AlertDialog(onDismissRequest = { detail = null }, title = { Text(item.title) }, text = {
        Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
            Text("${item.start} – ${item.end}"); Text(plain(item.notes))
            vm.study.entries.firstOrNull { "personal:${it.id}" == item.key }?.let { entry -> TextButton(onClick = { detail = null; editor = entry }) { Text("Szerkesztés") } }
        }
    }, confirmButton = { TextButton(onClick = { detail = null }) { Text("Bezárás") } }) }
    editor?.let { EntryEditor(it, { editor = null }, { vm.saveEntry(it); editor = null }, { vm.deleteEntry(it.id); editor = null }) }
    if (remove) AlertDialog(onDismissRequest = { remove = false }, title = { Text("Grafit-események törlése?") }, text = { Text("Csak az ehhez a Grafit-fiókhoz tartozó exportok törlődnek a kijelölt naptárból. Az automatikus export kikapcsol.") },
        confirmButton = { TextButton(onClick = { remove = false; vm.removeCalendarExports() }) { Text("Törlés") } }, dismissButton = { TextButton(onClick = { remove = false }) { Text("Mégse") } })
}

@Composable
private fun AccountsScreen(vm: GrafitViewModel, addAccount: () -> Unit) {
    var forget by remember { mutableStateOf<String?>(null) }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Mentett fiókok", style = MaterialTheme.typography.headlineSmall)
            Text("A fiókok munkamenete és gyorsítótára külön, titkosítva marad a készüléken. A háttérértesítések és widgetek az aktív fiókot használják.")
            Button(onClick = addAccount, enabled = !vm.busy && !vm.profileBusy) { Text("Másik KRÉTA-fiók hozzáadása") }
        }
        items(vm.savedAccounts, key = { it.first }) { (key, name) -> GrafitCard { Column(Modifier.padding(16.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            Text(key.substringBefore(':'), style = MaterialTheme.typography.bodySmall)
            if (key == vm.activeAccount && !vm.demo) Text("Aktív fiók", color = MaterialTheme.colorScheme.primary)
            else Row {
                TextButton(onClick = { vm.switchAccount(key) }, enabled = !vm.busy && !vm.profileBusy) { Text("Váltás") }
                TextButton(onClick = { forget = key }, enabled = !vm.busy && !vm.profileBusy) { Text("Elfelejtés") }
            }
        } } }
    }
    forget?.let { key -> AlertDialog(onDismissRequest = { forget = null }, title = { Text("Fiók elfelejtése?") }, text = { Text("A mentett munkamenet és gyorsítótár törlődik a fióklistából. A saját helyi profil és tanulási terv megmarad.") },
        confirmButton = { TextButton(onClick = { vm.forgetAccount(key); forget = null }) { Text("Elfelejtés") } }, dismissButton = { TextButton(onClick = { forget = null }) { Text("Mégse") } }) }
}

@Composable
private fun SearchScreen(vm: GrafitViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    val data = vm.data
    val study = vm.study
    val hidden = vm.appearance.hideGrades
    val hits by produceState<List<SearchHit>>(emptyList(), query, data, study, hidden) {
        kotlinx.coroutines.delay(120)
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { data?.let { searchSchool(it, study, query, hidden) }.orEmpty() }
    }
    var selected by remember { mutableStateOf<SearchHit?>(null) }
    var editor by remember { mutableStateOf<StudyEntry?>(null) }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Keresés mindenben", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(query, { query = it.take(200) }, modifier = Modifier.fillMaxWidth(), label = { Text("Tantárgy, tanár, feladat, tananyag…") }, singleLine = true)
            Text("Helyi keresés a mentett adatokban. A jegyértékek rejtéskor nem szerepelnek a találatok címében.", style = MaterialTheme.typography.bodySmall)
        }
        if (hits.isEmpty()) item { Text(if (query.isBlank()) "Írj be keresőkifejezést." else "Nincs találat a mentett adatokban.") }
        items(hits) { hit -> GrafitCard(onClick = { selected = hit }) { Column(Modifier.padding(14.dp)) {
            Text(if (hit.grade != null && vm.appearance.hideGrades) hit.grade.subject else hit.title, style = MaterialTheme.typography.titleMedium)
            Text("${hit.kind} · ${hit.date}", style = MaterialTheme.typography.labelSmall)
            Text(plain(hit.body), maxLines = 2)
        } } }
    }
    selected?.let { hit -> when {
        hit.task != null -> MessageDetailDialog(hit.task) { selected = null }
        hit.lesson != null -> LessonDetailDialog(hit.lesson, vm.data?.tasks.orEmpty()) { selected = null }
        else -> AlertDialog(onDismissRequest = { selected = null }, title = { Text(if (hit.grade != null && vm.appearance.hideGrades) hit.grade.subject else hit.title) }, text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                Text("${hit.kind} · ${hit.date}"); Text(plain(hit.body))
                hit.entry?.let { entry -> TextButton(onClick = { editor = entry; selected = null }) { Text("Szerkesztés") } }
            }
        }, confirmButton = { TextButton(onClick = { selected = null }) { Text("Bezárás") } })
    } }
    editor?.let { EntryEditor(it, { editor = null }, { vm.saveEntry(it); editor = null }, { vm.deleteEntry(it.id); editor = null }) }
}

@Composable
private fun PreparationScreen(vm: GrafitViewModel) {
    var selected by remember { mutableStateOf<SchoolTask?>(null) }
    var editor by remember { mutableStateOf<StudyEntry?>(null) }
    val exam = selected
    var note by remember(exam?.let(::taskKey)) { mutableStateOf(exam?.let { vm.study.examNotes[taskKey(it)] }.orEmpty()) }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Dolgozatfelkészülés", style = MaterialTheme.typography.headlineSmall) }
        if (exam == null) {
            val exams = vm.data?.tasks.orEmpty().filter { it.kind == "Dolgozat" }.sortedWith(compareBy<SchoolTask> { it.date < today().toString() }.thenBy { it.date })
            if (exams.isEmpty()) item { Text("Nincs letöltött bejelentett dolgozat. Saját felkészülést a Tanulási tervben is létrehozhatsz.") }
            items(exams) { task -> GrafitCard(onClick = { selected = task }) { Column(Modifier.padding(16.dp)) { Text(task.subject, fontWeight = FontWeight.Medium); Text("${task.date} · ${plain(task.text)}") } } }
        } else {
            item {
                TextButton(onClick = { selected = null }) { Text("‹ Dolgozatok") }
                Text("${exam.subject} · ${exam.date}", style = MaterialTheme.typography.titleLarge)
                Text(plain(exam.text))
                OutlinedTextField(note, { note = it.take(16000) }, label = { Text("Saját felkészülési jegyzet") }, minLines = 4, modifier = Modifier.fillMaxWidth())
                Button(onClick = { vm.updateStudy(vm.study.copy(examNotes = vm.study.examNotes + (taskKey(exam) to note))) }) { Text("Jegyzet mentése") }
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    TextButton(onClick = { editor = newEntry(runCatching { LocalDate.parse(exam.date) }.getOrDefault(today())).copy(title = "Felkészülés: ${exam.subject}", subject = exam.subject, kind = "study", examKey = taskKey(exam)) }) { Text("Tanulási blokk") }
                    TextButton(onClick = { vm.saveEntry(newEntry(runCatching { LocalDate.parse(exam.date) }.getOrDefault(today())).copy(title = "Új ellenőrzőpont", subject = exam.subject, kind = "checklist", examKey = taskKey(exam))) }) { Text("Ellenőrzőpont") }
                }
            }
            items(vm.study.entries.filter { it.examKey == taskKey(exam) }) { entry -> EntryRow(entry, { vm.toggleEntry(entry.id) }, { editor = entry }) }
            item { Text("Kapcsolódó tananyag az elmúlt 30 napból", style = MaterialTheme.typography.titleMedium) }
            val end = runCatching { LocalDate.parse(exam.date) }.getOrDefault(today())
            val topics = vm.data?.lessons.orEmpty().filter { lesson ->
                val same = if (exam.subjectId != null && lesson.subjectId != null) exam.subjectId.substringBefore(',') == lesson.subjectId.substringBefore(',') else searchKey(exam.subject) == searchKey(lesson.subject)
                same && lesson.start.toLocalDate() in end.minusDays(30)..end && lesson.topic.isNotBlank()
            }.map { it.topic }.distinct()
            if (topics.isEmpty()) item { Text("Nincs ehhez a dolgozathoz letöltött tananyag.") }
            items(topics) { Text("• ${plain(it)}") }
        }
    }
    editor?.let { EntryEditor(it, { editor = null }, { vm.saveEntry(it); editor = null }, { vm.deleteEntry(it.id); editor = null }) }
}

@Composable
private fun WidgetSettings(vm: GrafitViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val manager = android.appwidget.AppWidgetManager.getInstance(context)
    Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Android-widgetek", style = MaterialTheme.typography.headlineSmall)
        Text("Tartsd nyomva az Android kezdőképernyőjét, válaszd a Widgetek menüt, majd a Grafitot. Négy külön widget érhető el: Mai órarend, Következő tanóra, Feladatok és Jegyek.")
        if (manager.isRequestPinAppWidgetSupported) {
            listOf("Mai órarend" to hu.grafit.widgets.GrafitWidget::class.java,
                "Következő tanóra" to hu.grafit.widgets.NextClassWidget::class.java,
                "Feladatok" to hu.grafit.widgets.HomeworkWidget::class.java,
                "Jegyek" to hu.grafit.widgets.GradesWidget::class.java).forEach { (label, cls) ->
                OutlinedButton(onClick = { manager.requestPinAppWidget(android.content.ComponentName(context, cls), null, null) }) { Text("$label hozzáadása") }
            }
        }
        NotificationToggle("Jegyek megjelenítése a widgeten", vm.study.widgetGrades) { vm.updateStudy(vm.study.copy(widgetGrades = it)) }
        Text("A widgetek az aktív fiók helyben mentett adatait mutatják. A frissítő gomb helyi újrarajzolást végez; hálózati szinkronhoz nyisd meg a Grafitot. Az Android az időszakos frissítést késleltetheti.", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = vm::refreshPersonalIntegrations) { Text("Widgetek frissítése") }
    }
}
