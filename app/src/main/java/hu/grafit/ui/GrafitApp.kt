package hu.grafit.ui

import android.annotation.SuppressLint
import android.os.Build
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import hu.grafit.data.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val hu = Locale.forLanguageTag("hu-HU")
private val dateFormat = DateTimeFormatter.ofPattern("MMMM d., EEEE", hu)
private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
private val seeds = listOf(Color(0xFF506A53), Color(0xFF51658F), Color(0xFF80547C), Color(0xFF99613D))
private enum class Page(val title: String, val icon: ImageVector) {
    Home("Kezdőlap", Icons.Outlined.Home), Timetable("Órarend", Icons.Outlined.CalendarMonth),
    Grades("Jegyek", Icons.Outlined.School), Absences("Hiányzások", Icons.Outlined.EventBusy)
}

@Composable
fun GrafitApp(vm: GrafitViewModel) {
    val a = vm.appearance
    val context = LocalContext.current
    val dark = a.mode == "dark" || (a.mode == "system" && isSystemInDarkTheme())
    val colors = if (a.dynamic && Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) darkColorScheme(primary = lerp(seeds[a.color], Color.White, .45f), primaryContainer = seeds[a.color].copy(alpha = .35f),
        background = Color(0xFF111510), surface = Color(0xFF111510))
    else lightColorScheme(primary = seeds[a.color], primaryContainer = seeds[a.color].copy(alpha = .17f),
        background = Color(0xFFF8FAF5), surface = Color(0xFFF8FAF5))
    MaterialTheme(colorScheme = colors, shapes = Shapes(medium = RoundedCornerShape(a.radius.dp),
        large = RoundedCornerShape(a.radius.dp), extraLarge = RoundedCornerShape((a.radius + 8).dp))) {
        Surface(Modifier.fillMaxSize()) { AppContent(vm) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppContent(vm: GrafitViewModel) {
    var page by rememberSaveable { mutableStateOf(Page.Home) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var login by rememberSaveable { mutableStateOf(false) }
    var logoutDialog by remember { mutableStateOf(false) }
    val active = vm.signedIn || vm.demo
    LaunchedEffect(active) { if (active) login = false }
    LaunchedEffect(vm.appearance) {
        if ((page == Page.Grades && !vm.appearance.showGrades) ||
            (page == Page.Absences && !vm.appearance.showAbsences)) page = Page.Home
    }
    BackHandler(settings || login || page != Page.Home) {
        when { settings -> settings = false; login -> login = false; else -> page = Page.Home }
    }
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(if (settings) "Megjelenés" else if (login) "KRÉTA bejelentkezés" else "grafit",
                fontWeight = FontWeight.SemiBold) }, navigationIcon = {
                if (settings || login) IconButton(onClick = { settings = false; login = false }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Vissza") }
            }, actions = {
                if (active && !settings && !login) {
                    IconButton(onClick = vm::reload, enabled = !vm.busy) { Icon(Icons.Outlined.Refresh, "Frissítés") }
                    IconButton(onClick = { settings = true }) { Icon(Icons.Outlined.Tune, "Megjelenés és beállítások") }
                }
            })
        }, bottomBar = {
            if (active && !settings && !login) NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Page.entries.filter { (it != Page.Grades || vm.appearance.showGrades) &&
                    (it != Page.Absences || vm.appearance.showAbsences) }.forEach { item ->
                    NavigationBarItem(selected = page == item, onClick = { page = item },
                        icon = { Icon(item.icon, null) }, label = { Text(item.title) })
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            vm.error?.let { error ->
                Surface(color = MaterialTheme.colorScheme.errorContainer) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(error, color = MaterialTheme.colorScheme.onErrorContainer)
                        Row {
                            if (active) TextButton(onClick = vm::reload, enabled = !vm.busy) { Text("Újrapróbálás") }
                            if (vm.signedIn) TextButton(onClick = { login = true }) { Text("Bejelentkezés újra") }
                            TextButton(onClick = vm::dismissError) { Text("Bezárás") }
                        }
                    }
                }
            }
            when {
                login -> LoginWebView(onCode = vm::login, onError = { login = false })
                !active -> Welcome(vm, onLogin = { login = true })
                settings -> Settings(vm, onLogout = { logoutDialog = true })
                else -> {
                    if (vm.demo) Text("Bemutató mód · mintaadatok", Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 20.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelMedium)
                    when (page) {
                        Page.Home -> Home(vm, { page = Page.Timetable }, { page = Page.Grades })
                        Page.Timetable -> Timetable(vm)
                        Page.Grades -> Grades(vm)
                        Page.Absences -> Absences(vm)
                    }
                }
            }
        }
    }
    if (logoutDialog) AlertDialog(onDismissRequest = { logoutDialog = false }, title = { Text("Kijelentkezés?") },
        text = { Text("A mentett munkamenet és az iskolai adatok törlődnek erről az eszközről.") },
        confirmButton = { TextButton(onClick = { vm.logout(); settings = false; logoutDialog = false }) { Text("Kijelentkezés") } },
        dismissButton = { TextButton(onClick = { logoutDialog = false }) { Text("Mégse") } })
}

@Composable
private fun Welcome(vm: GrafitViewModel, onLogin: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp), verticalArrangement = Arrangement.Center) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Icon(Icons.Outlined.AutoStories, null, Modifier.padding(24.dp).size(48.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(32.dp))
        Text("Az iskolád.\nA te ritmusod.", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(16.dp))
        Text("Órarend, jegyek és hiányzások egy nyugodtabb, személyre szabható felületen.",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(40.dp))
        Button(onClick = onLogin, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Bejelentkezés KRÉTA-fiókkal") }
        OutlinedButton(onClick = vm::startDemo, enabled = !vm.busy, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 56.dp)) { Text("Kipróbálom mintaadatokkal") }
        Spacer(Modifier.height(24.dp))
        Text("A bejelentkezés a KRÉTA saját oldalán történik. A Grafit nem tárolja a jelszavadat.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Heading(title: String, subtitle: String? = null) {
    Column(Modifier.padding(top = 8.dp, bottom = 8.dp)) {
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
    }
}
@Composable
private fun Section(title: String, action: String? = null, onClick: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (action != null) TextButton(onClick = onClick) { Text(action) }
    }
}
@Composable
private fun Empty(message: String) {
    Text(message, Modifier.fillMaxWidth().padding(vertical = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
}
@Composable
private fun Home(vm: GrafitViewModel, timetable: () -> Unit, grades: () -> Unit) {
    val data = vm.data
    val now = LocalDateTime.now(schoolZone)
    val next = data?.lessons?.firstOrNull { it.end > now && it.state != "Elmaradt" }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Heading("Szia, ${data?.student?.substringAfterLast(' ') ?: "üdv itt"}!", today().format(dateFormat).replaceFirstChar { it.uppercase(hu) }) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), onClick = timetable) {
                Column(Modifier.fillMaxWidth().padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Schedule, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (next?.start?.isBefore(now) == true) "MOST TART" else "KÖVETKEZŐ ÓRA", style = MaterialTheme.typography.labelMedium)
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(next?.subject ?: "Mára végeztél.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    Text(next?.let { "${it.start.toLocalDate()} · ${it.start.format(timeFormat)}–${it.end.format(timeFormat)}" } ?: "Nincs további óra a letöltött órarendben.")
                    if (next != null) Text(listOf(next.room, next.teacher).filter { it.isNotBlank() }.joinToString(" · "),
                        Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Metric("Tanulmányi átlag", if (vm.appearance.hideGrades) "••" else average(data?.grades.orEmpty())?.let { "%.2f".format(hu, it) } ?: "—", Modifier.weight(1f))
                Metric("Mai tanórák", "${data?.lessons?.count { it.start.toLocalDate() == today() } ?: 0}", Modifier.weight(1f))
            }
        }
        item { Section("Legutóbbi jegyek", "Összes", grades) }
        val recent = data?.grades.orEmpty().sortedByDescending { it.date }.take(3)
        if (recent.isEmpty()) item { Empty("Még nincsenek megjeleníthető jegyek.") }
        items(recent) { GradeRow(it, vm.appearance) }
        item { Section("Teendők és hírek") }
        val tasks = data?.tasks.orEmpty().sortedByDescending { it.date }
        if (tasks.isEmpty()) item { Empty("Nincs megjeleníthető teendő vagy hír.") }
        items(tasks) { task ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text("${task.kind} · ${task.date}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(task.subject, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))
                    Text(android.text.Html.fromHtml(task.text, android.text.Html.FROM_HTML_MODE_COMPACT).toString(), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item { Section("A mai napod", "Órarend", timetable) }
        val lessons = data?.lessons.orEmpty().filter { it.start.toLocalDate() == today() }
        if (lessons.isEmpty()) item { Empty("Erre a napra nincs tanóra.") }
        items(lessons) { LessonRow(it, vm.appearance) }
        item { Text(data?.let { "${it.school}\nUtolsó frissítés · ${java.time.Instant.ofEpochMilli(it.syncedAt).atZone(schoolZone).format(DateTimeFormatter.ofPattern("MM. dd. HH:mm"))}" } ?: "Az adatok betöltése folyamatban.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
@Composable
private fun Metric(label: String, value: String, modifier: Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(20.dp)) {
            Text(value, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable
private fun LessonRow(lesson: Lesson, a: Appearance) {
    var expanded by remember(lesson.id) { mutableStateOf(false) }
    Card(onClick = { expanded = !expanded }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().padding(if (a.compact) 12.dp else 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(56.dp)) {
                Text(lesson.start.format(timeFormat), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Text(lesson.end.format(timeFormat), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(Modifier.width(3.dp).height(36.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(lesson.subject, fontWeight = FontWeight.Medium)
                Text(listOf(lesson.room, lesson.teacher).filter { it.isNotEmpty() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (lesson.state == "Elmaradt") Text("Elmaradt", color = MaterialTheme.colorScheme.error)
                if (expanded) Text(lesson.topic.ifBlank { "Nincs megadott tananyag." }, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyMedium)
            }
            Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, "Óra részletei")
        }
    }
}
@Composable
private fun GradeRow(grade: Grade, a: Appearance) {
    var revealed by remember(grade.id, a.hideGrades) { mutableStateOf(!a.hideGrades) }
    var expanded by remember(grade.id) { mutableStateOf(false) }
    Card(onClick = { if (!revealed) revealed = true else expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().padding(if (a.compact) 12.dp else 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(if (revealed) grade.value else "•", Modifier.padding(horizontal = 17.dp, vertical = 10.dp), style = MaterialTheme.typography.titleLarge)
            }
            Column(Modifier.padding(start = 16.dp).weight(1f)) {
                Text(grade.subject, fontWeight = FontWeight.Medium)
                Text(grade.topic.ifBlank { "Értékelés" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${grade.date} · ${grade.weight}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (expanded) Text(grade.teacher, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium)
            }
            Icon(Icons.Outlined.ChevronRight, if (revealed) "Jegy részletei" else "Jegy felfedése")
        }
    }
}
@Composable
private fun Timetable(vm: GrafitViewModel) {
    val date = vm.date
    val lessons = vm.data?.lessons.orEmpty().filter { it.start.toLocalDate() == date }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Heading("Órarend", date.format(DateTimeFormatter.ofPattern("yyyy. MMMM", hu)))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = { vm.selectDate(date.minusDays(7)) }, enabled = !vm.busy) { Icon(Icons.Outlined.ChevronLeft, "Előző hét") }
            TextButton(onClick = { vm.selectDate(today()) }, enabled = !vm.busy) { Text("Ma") }
            IconButton(onClick = { vm.selectDate(date.plusDays(7)) }, enabled = !vm.busy) { Icon(Icons.Outlined.ChevronRight, "Következő hét") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            val monday = date.minusDays((date.dayOfWeek.value - 1).toLong())
            (0L..6L).forEach { offset ->
                val day = monday.plusDays(offset)
                val selected = day == date
                Surface(onClick = { if (!vm.busy) vm.selectDate(day) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(18.dp),
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) {
                    Column(Modifier.padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(day.format(DateTimeFormatter.ofPattern("EEEEE", hu)), style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.height(8.dp)); Text("${day.dayOfMonth}", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
            if (lessons.isEmpty()) item { Empty("Erre a napra nincs tanóra a letöltött órarendben.") }
            items(lessons) { LessonRow(it, vm.appearance) }
        }
    }
}
@Composable
private fun Grades(vm: GrafitViewModel) {
    var subject by rememberSaveable { mutableStateOf<String?>(null) }
    val all = vm.data?.grades.orEmpty()
    val shown = all.filter { subject == null || it.subject == subject }.sortedByDescending { it.date }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Heading("Jegyeid", "Minden eredmény egy helyen") }
        item { Metric("Súlyozott évközi átlag", if (vm.appearance.hideGrades) "••" else average(shown)?.let { "%.2f".format(hu, it) } ?: "—", Modifier.fillMaxWidth()) }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = subject == null, onClick = { subject = null }, label = { Text("Összes") })
                all.map { it.subject }.distinct().sorted().forEach { s ->
                    FilterChip(selected = subject == s, onClick = { subject = s }, label = { Text(s) })
                }
            }
        }
        if (shown.isEmpty()) item { Empty("Még nincsenek jegyek.") }
        items(shown) { GradeRow(it, vm.appearance) }
    }
}
@Composable
private fun Absences(vm: GrafitViewModel) {
    val all = vm.data?.absences.orEmpty()
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Heading("Hiányzások") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Metric("Igazolt", "${all.count { it.state == "Igazolt" }}", Modifier.weight(1f))
                Metric("Igazolatlan", "${all.count { it.state == "Igazolatlan" }}", Modifier.weight(1f))
            }
        }
        if (all.isEmpty()) item { Empty("Nincsenek hiányzások.") }
        items(all.sortedByDescending { it.date }) { absence ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text(absence.subject, fontWeight = FontWeight.SemiBold)
                    Text("${absence.date} · ${when(absence.state) { "Igazolando" -> "Igazolásra vár"; else -> absence.state.ifBlank { "Ismeretlen állapot" } }}",
                        color = if (absence.state == "Igazolatlan") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    if (absence.minutes > 0) Text("${absence.minutes} perc késés")
                }
            }
        }
    }
}

@Composable
private fun Toggle(title: String, checked: Boolean, onChange: (Boolean) -> Unit, subtitle: String? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
@Composable
private fun Settings(vm: GrafitViewModel, onLogout: () -> Unit) {
    val a = vm.appearance
    var about by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Heading("Legyen a tiéd.", "Személyre szabás") }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(24.dp)) {
                    Icon(Icons.Outlined.Palette, null)
                    Spacer(Modifier.height(16.dp))
                    Text("Egy kicsit nyugodtabb.", style = MaterialTheme.typography.titleLarge)
                    Text("A változtatások azonnal megjelennek.", Modifier.padding(top = 4.dp))
                }
            }
        }
        item {
            Section("Téma")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("system" to "Rendszer", "light" to "Világos", "dark" to "Sötét").forEach { (mode, label) ->
                    FilterChip(selected = a.mode == mode, onClick = { vm.customize(a.copy(mode = mode)) }, label = { Text(label) })
                }
            }
            if (Build.VERSION.SDK_INT >= 31) Toggle("Material You", a.dynamic, { vm.customize(a.copy(dynamic = it)) }, "Színek az eszközöd háttérképéből")
        }
        item {
            Text("Kiemelőszín", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                seeds.forEachIndexed { index, color ->
                    val labels = listOf("Zsálya", "Kék", "Mályva", "Agyag")
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(onClick = { vm.customize(a.copy(color = index, dynamic = false)) }, shape = CircleShape, color = color,
                            modifier = Modifier.size(48.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                if (!a.dynamic && a.color == index) Icon(Icons.Outlined.Check, "${labels[index]} kiválasztva", tint = Color.White)
                            }
                        }
                        Text(labels[index], Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        item {
            Text("Kártyák lekerekítése", style = MaterialTheme.typography.titleMedium)
            Slider(value = a.radius, onValueChange = { vm.customize(a.copy(radius = it)) }, valueRange = 8f..32f, steps = 5)
            Toggle("Kompakt kártyák", a.compact, { vm.customize(a.copy(compact = it)) })
            Toggle("Jegyek elrejtése", a.hideGrades, { vm.customize(a.copy(hideGrades = it)) }, "Érintsd meg a jegyet a felfedéshez")
        }
        item {
            Section("Navigáció")
            Toggle("Jegyek az alsó sávban", a.showGrades, { vm.customize(a.copy(showGrades = it)) })
            Toggle("Hiányzások az alsó sávban", a.showAbsences, { vm.customize(a.copy(showAbsences = it)) })
        }
        item {
            HorizontalDivider()
            TextButton(onClick = { about = true }) { Icon(Icons.Outlined.Info, null); Spacer(Modifier.width(10.dp)); Text("A Grafitról és a licencről") }
            TextButton(onClick = onLogout, enabled = !vm.busy) { Icon(Icons.AutoMirrored.Outlined.Logout, null); Spacer(Modifier.width(10.dp)); Text(if (vm.demo) "Kilépés a bemutatóból" else "Kijelentkezés") }
        }
    }
    if (about) AlertDialog(onDismissRequest = { about = false }, title = { Text("Grafit 0.1.0") },
        text = { Text("Natív Android kliens, Kotlin és Jetpack Compose alapokon.\n\nA KRÉTA-integráció és a navigáció a QwIT Development és közreműködői Firka projektjének adaptációja.\n\nCC BY-NC-SA 4.0 · Nem kereskedelmi felhasználás.\ngithub.com/QwIT-Development/firka\n\nA Grafit független alkalmazás.") },
        confirmButton = { TextButton(onClick = { about = false }) { Text("Rendben") } })
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun LoginWebView(onCode: (String, String) -> Unit, onError: () -> Unit) {
    val verifier = rememberSaveable { OAuth.random() }
    val state = rememberSaveable { OAuth.random() }
    var handled by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    if (!request.isForMainFrame) return false
                    val uri = request.url
                    try {
                        val code = OAuth.callbackCode(uri.toString(), state)
                        if (code != null) {
                            if (!handled) { handled = true; onCode(code, verifier) }
                            return true
                        }
                    } catch (e: IllegalArgumentException) { error = e.message; return true }
                    return uri.scheme != "https" || !(uri.host == "e-kreta.hu" || uri.host?.endsWith(".e-kreta.hu") == true)
                }
                override fun onReceivedError(view: WebView, request: WebResourceRequest, failure: android.webkit.WebResourceError) {
                    if (request.isForMainFrame) error = "A KRÉTA bejelentkezési oldala nem tölthető be. Ellenőrizd az internetkapcsolatot."
                }
                override fun onReceivedSslError(view: WebView, handler: android.webkit.SslErrorHandler, failure: android.net.http.SslError) {
                    handler.cancel(); error = "A KRÉTA biztonsági tanúsítványa nem ellenőrizhető."
                }
            }
            loadUrl(OAuth.loginUrl(verifier, state))
        }
    }
    DisposableEffect(webView) { onDispose { webView.stopLoading(); webView.destroy() } }
    Column(Modifier.fillMaxSize()) {
        Text("Biztonságos kapcsolat · idp.e-kreta.hu", Modifier.fillMaxWidth().padding(16.dp), style = MaterialTheme.typography.labelMedium)
        if (error != null) {
            Text(error!!, Modifier.padding(20.dp), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onError) { Text("Vissza és újrapróbálás") }
        } else if (handled) {
            Text("Munkamenet létrehozása…", Modifier.padding(20.dp))
            TextButton(onClick = onError) { Text("Vissza") }
        } else AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
    }
}
