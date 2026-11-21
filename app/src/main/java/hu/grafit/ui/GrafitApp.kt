package hu.grafit.ui

import android.os.Build
import android.content.Intent
import android.app.Activity
import hu.grafit.KretaLoginActivity
import hu.grafit.BuildConfig
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import hu.grafit.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val hu = Locale.forLanguageTag("hu-HU")
private val dateFormat = DateTimeFormatter.ofPattern("MMMM d., EEEE", hu)
private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
private val seeds = listOf(Color(0xFF506A53), Color(0xFF51658F), Color(0xFF80547C), Color(0xFF99613D), Color(0xFF5F6368), Color(0xFF006C67), Color(0xFF8E4A5E))
private enum class Page(val title: String, val icon: ImageVector) {
    Home("Kezdőlap", Icons.Outlined.Home), Timetable("Órarend", Icons.Outlined.CalendarMonth),
    Grades("Jegyek", Icons.Outlined.School), Absences("Hiányzások", Icons.Outlined.EventBusy)
}

@Composable
fun GrafitApp(vm: GrafitViewModel) {
    val a = vm.appearance
    ApplyRefreshRate(a.refreshRate)
    val context = LocalContext.current
    val dark = a.mode == "dark" || (a.mode == "system" && isSystemInDarkTheme())
    val baseColors = if (a.dynamic && Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) darkColorScheme(primary = lerp(seeds[a.color], Color.White, .45f), primaryContainer = seeds[a.color].copy(alpha = .35f),
        background = Color(0xFF111510), surface = Color(0xFF111510))
    else lightColorScheme(primary = seeds[a.color], primaryContainer = seeds[a.color].copy(alpha = .17f),
        background = Color(0xFFF8FAF5), surface = Color(0xFFF8FAF5))
    val tokens = remember(a.customCss) { ThemeCss.parse(a.customCss) }
    val colors = customColors(if (dark && a.pureBlack) baseColors.copy(background = Color.Black, surface = Color.Black) else baseColors, tokens)
    ApplySystemBars(contrastColor(colors.surface) == Color.White,
        contrastColor(if (a.pillNavigation) colors.background else colors.surfaceContainerLow) == Color.White)
    CompositionLocalProvider(LocalAppearance provides a, LocalThemeTokens provides tokens) {
    MaterialTheme(colorScheme = colors, typography = remember(a.textScale, a.fontFamily) { customTypography(a) }, shapes = Shapes(medium = RoundedCornerShape(a.radius.dp),
        large = RoundedCornerShape(a.radius.dp), extraLarge = RoundedCornerShape((a.radius + 8).dp))) {
        Surface(Modifier.fillMaxSize()) { AppContent(vm) }
        UpdatePrompt(vm.updates)
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppContent(vm: GrafitViewModel) {
    val pager = rememberPagerState(pageCount = { Page.entries.size })
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val page = Page.entries[pager.targetPage]
    fun navigate(target: Page) {
        if (vm.appearance.haptics) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        scope.launch { pager.animateScrollToPage(target.ordinal, animationSpec = tween(FirkaMotion.tabMillis, easing = FirkaMotion.easeInOut)) }
    }
    var settings by rememberSaveable { mutableStateOf(false) }
    var login by rememberSaveable { mutableStateOf(false) }
    var profileMenu by rememberSaveable { mutableStateOf(false) }
    var logoutDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val loginLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        login = false
        if (result.resultCode == Activity.RESULT_OK) {
            val code = result.data?.getStringExtra(KretaLoginActivity.CODE)
            val verifier = result.data?.getStringExtra(KretaLoginActivity.VERIFIER)
            if (!code.isNullOrBlank() && !verifier.isNullOrBlank()) vm.login(code, verifier)
        }
    }
    fun openLogin() {
        if (login || vm.profileBusy) return
        login = true
        loginLauncher.launch(Intent(context, KretaLoginActivity::class.java))
    }
    val active = vm.signedIn || vm.demo
    LaunchedEffect(active) { if (active) login = false }
    LaunchedEffect(vm.appearance) {
        if ((page == Page.Grades && !vm.appearance.showGrades) ||
            (page == Page.Absences && !vm.appearance.showAbsences)) pager.scrollToPage(Page.Home.ordinal)
    }
    BackHandler(settings || login || page != Page.Home) {
        when { settings -> settings = false; login -> login = false; else -> navigate(Page.Home) }
    }
    Scaffold(
        topBar = {
            val pill = vm.appearance.pillToolbar
            Surface(modifier = if (pill) Modifier.statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp) else Modifier,
                shape = if (pill) CircleShape else RoundedCornerShape(0.dp),
                color = if (pill) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface,
                shadowElevation = if (pill) 2.dp else 0.dp) {
            TopAppBar(windowInsets = if (pill) WindowInsets(0, 0, 0, 0) else TopAppBarDefaults.windowInsets,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent), title = { Text(if (settings) "Megjelenés" else if (login) "KRÉTA bejelentkezés" else "grafit",
                fontWeight = FontWeight.SemiBold) }, navigationIcon = {
                if (settings || login) IconButton(onClick = { settings = false; login = false }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Vissza") }
            }, actions = {
                if (active && !settings && !login) {
                    IconButton(onClick = vm::reload, enabled = !vm.busy) { Icon(Icons.Outlined.Refresh, "Frissítés") }
                    ProfileBubble(vm, profileMenu, onClick = { profileMenu = true })
                }
            })
            }
        }, bottomBar = {
            if (active && !settings && !login) {
            val pill = vm.appearance.pillNavigation
            Surface(modifier = if (pill) Modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp) else Modifier,
                shape = if (pill) CircleShape else RoundedCornerShape(0.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            NavigationBar(containerColor = Color.Transparent, tonalElevation = 0.dp,
                windowInsets = if (pill) WindowInsets(0, 0, 0, 0) else NavigationBarDefaults.windowInsets) {
                Page.entries.filter { (it != Page.Grades || vm.appearance.showGrades) &&
                    (it != Page.Absences || vm.appearance.showAbsences) }.forEach { item ->
                    NavigationBarItem(selected = page == item, onClick = { navigate(item) },
                        icon = { Icon(item.icon, if (!vm.appearance.navigationLabels && page != item) item.title else null) },
                        alwaysShowLabel = vm.appearance.navigationLabels, label = { Text(item.title) })
                }
            }
            }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize()) {
                vm.error?.let { error ->
                    Surface(color = MaterialTheme.colorScheme.errorContainer) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(error, color = MaterialTheme.colorScheme.onErrorContainer)
                            FlowRow {
                                if (active) TextButton(onClick = vm::reload, enabled = !vm.busy) { Text("Újrapróbálás") }
                                if (vm.signedIn && vm.authenticationRequired) TextButton(onClick = { openLogin() }, enabled = !vm.profileBusy) { Text("Bejelentkezés újra") }
                                TextButton(onClick = vm::dismissError) { Text("Bezárás") }
                            }
                        }
                    }
                }
                val route = when {
                    !active -> "welcome"
                    settings -> "settings"
                    else -> "content"
                }
                AnimatedContent(targetState = route, modifier = Modifier.weight(1f), label = "screenTransition",
                    transitionSpec = {
                        ((fadeIn(tween(FirkaMotion.enterMillis, easing = FirkaMotion.easeOut)) +
                            slideInVertically(tween(FirkaMotion.enterMillis, easing = FirkaMotion.easeOut)) { it * 4 / 100 }) togetherWith
                            (fadeOut(tween(FirkaMotion.exitMillis, easing = FirkaMotion.easeIn)) +
                            slideOutVertically(tween(FirkaMotion.exitMillis, easing = FirkaMotion.easeIn)) { it * 4 / 100 })).using(null)
                    }) { currentRoute ->
                    when (currentRoute) {
                        "welcome" -> Welcome(vm, onLogin = { openLogin() })
                        "settings" -> Settings(vm, onLogout = { logoutDialog = true })
                        else -> Column(Modifier.fillMaxSize()) {
                            if (vm.demo) Text("Bemutató mód · mintaadatok", Modifier.fillMaxWidth()
                                .background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 20.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelMedium)
                            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), key = { it }) { index ->
                                when (Page.entries[index]) {
                                    Page.Home -> Home(vm, { navigate(Page.Timetable) }, { navigate(Page.Grades) })
                                    Page.Timetable -> Timetable(vm)
                                    Page.Grades -> Grades(vm)
                                    Page.Absences -> Absences(vm)
                                }
                            }
                        }
                    }
                }
            }
            // Draw above the content without participating in its vertical layout.
            if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }
    if (profileMenu && active) ProfileMenu(vm, onDismiss = { profileMenu = false },
        onSettings = { settings = true }, onLogout = { logoutDialog = true })
    if (logoutDialog) AlertDialog(onDismissRequest = { logoutDialog = false }, title = { Text("Kijelentkezés?") },
        text = { Text("A mentett munkamenet és az iskolai adatok törlődnek erről az eszközről.") },
        confirmButton = { TextButton(onClick = { vm.logout(); settings = false; logoutDialog = false }) { Text("Kijelentkezés") } },
        dismissButton = { TextButton(onClick = { logoutDialog = false }) { Text("Mégse") } })
}

@Composable
private fun Welcome(vm: GrafitViewModel, onLogin: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp), verticalArrangement = Arrangement.Center) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer) {
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
    val now by produceState(LocalDateTime.now(schoolZone)) {
        while (true) {
            value = LocalDateTime.now(schoolZone)
            delay(30_000)
        }
    }
    val dayDate = now.toLocalDate()
    val dailyLessons = remember(data?.lessons, dayDate) { data?.lessons.orEmpty().filter { it.start.toLocalDate() == dayDate } }
    val day = schoolDay(dailyLessons, now)
    val next = day.next
    val recent = remember(data?.grades) { data?.grades.orEmpty().sortedByDescending { it.date }.take(3) }
    val tasks = remember(data?.tasks) { data?.tasks.orEmpty().sortedByDescending { it.date } }
    FirkaLoadedContent(loaded = data != null) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Heading("Szia, ${vm.profile.displayName.ifBlank { vm.displayName.substringAfterLast(' ') }}!", today().format(dateFormat).replaceFirstChar { it.uppercase(hu) })
            if (!data?.school.isNullOrBlank()) Text(data!!.school, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            GrafitCard(color = MaterialTheme.colorScheme.primaryContainer, onClick = timetable) {
                Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(
                    MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = .55f)
                ))).padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Schedule, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(when (day.status) {
                            SchoolDayStatus.Ongoing -> "MOST TART"
                            SchoolDayStatus.Upcoming -> "KÖVETKEZŐ ÓRA"
                            SchoolDayStatus.Finished -> "MÁRA VÉGEZTÉL"
                            SchoolDayStatus.NoClasses -> "SZABAD NAP"
                        }, style = MaterialTheme.typography.labelMedium)
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(next?.subject ?: if (day.status == SchoolDayStatus.NoClasses) "Ma nincsenek óráid." else "Mára nincs több órád.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    Text(next?.let { "${it.start.toLocalDate()} · ${it.start.format(timeFormat)}–${it.end.format(timeFormat)}" } ?: if (day.status == SchoolDayStatus.NoClasses) "A letöltött órarend szerint ma nincs tanítás." else "Az összes mai tanórád véget ért. Jó pihenést!")
                    if (next != null) Text(listOf(next.room, next.teacher).filter { it.isNotBlank() }.joinToString(" · "),
                        Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium)
                    if (next != null && next.start < now) {
                        val duration = java.time.Duration.between(next.start, next.end).seconds.coerceAtLeast(1)
                        val progress = (java.time.Duration.between(next.start, now).seconds.toFloat() / duration).coerceIn(0f, 1f)
                        Spacer(Modifier.height(20.dp))
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        Text("${java.time.Duration.between(now, next.end).toMinutes().coerceAtLeast(0)} perc az óra végéig",
                            Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        if (vm.appearance.homeMetrics) item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Metric("Tanulmányi átlag", if (vm.appearance.hideGrades) "••" else average(data?.grades.orEmpty())?.let { "%.2f".format(hu, it) } ?: "—", Modifier.weight(1f))
                Metric("Mai tanórák", "${day.lessons.size}", Modifier.weight(1f))
            }
        }
        if (vm.appearance.homeGrades) {
        item { Section("Legutóbbi jegyek", "Összes", grades) }
        if (recent.isEmpty()) item { Empty("Még nincsenek megjeleníthető jegyek.") }
        items(recent) { GradeRow(it, vm.appearance) }
        }
        if (vm.appearance.homeTasks) {
        item { Section("Teendők és hírek") }
        if (tasks.isEmpty()) item { Empty("Nincs megjeleníthető teendő vagy hír.") }
        items(tasks) { task ->
            GrafitCard() {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text("${task.kind} · ${task.date}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(task.subject, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))
                    Text(android.text.Html.fromHtml(task.text, android.text.Html.FROM_HTML_MODE_COMPACT).toString(), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        }
        if (vm.appearance.homeTimetable) {
        item { Section("A mai napod", "Órarend", timetable) }
        val lessons = dailyLessons
        if (lessons.isEmpty()) item { Empty("Erre a napra nincs tanóra.") }
        items(lessons) { LessonRow(it, vm.appearance) }
        }
        item {
            val syncLabel = if (data == null || data.syncedAt == 0L) "Első szinkronizálás folyamatban…"
                else "Utolsó frissítés · ${java.time.Instant.ofEpochMilli(data.syncedAt).atZone(schoolZone).format(DateTimeFormatter.ofPattern("MM. dd. HH:mm"))}"
            Text(listOfNotNull(data?.school, syncLabel).joinToString("\n"),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    }
}
@Composable
private fun Metric(label: String, value: String, modifier: Modifier) {
    GrafitCard(modifier) {
        Column(Modifier.padding(20.dp)) {
            Icon(Icons.Outlined.Insights, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(14.dp))
            Text(value, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable
private fun LessonRow(lesson: Lesson, a: Appearance) {
    var expanded by remember(lesson.id) { mutableStateOf(false) }
    GrafitCard(modifier = Modifier.animateContentSize(), onClick = { expanded = !expanded }) {
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
    val flip by animateFloatAsState(if (revealed) 0f else 180f,
        tween(FirkaMotion.flipMillis, easing = FirkaMotion.easeInOut), label = "gradeReveal")
    GrafitCard(modifier = Modifier.animateContentSize(), onClick = { if (!revealed) revealed = true else expanded = !expanded }) {
        Row(Modifier.fillMaxWidth().padding(if (a.compact) 12.dp else 18.dp), verticalAlignment = Alignment.CenterVertically) {
            val badge = if (flip < 90f) gradeContainer(grade.number) else MaterialTheme.colorScheme.secondaryContainer
            val accent = if (flip < 90f) gradeAccent(grade.number) else null
            Surface(shape = MaterialTheme.shapes.medium, color = badge, contentColor = contrastColor(badge),
                border = accent?.let { BorderStroke(2.dp, it) },
                modifier = Modifier.graphicsLayer { rotationY = flip; cameraDistance = 8 * density }) {
                Text(if (flip < 90f) grade.value else "•", Modifier.graphicsLayer { rotationY = if (flip < 90f) 0f else 180f }
                    .padding(horizontal = 17.dp, vertical = 10.dp), style = MaterialTheme.typography.titleLarge)
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
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Heading("Órarend", date.format(DateTimeFormatter.ofPattern("yyyy. MMMM", hu)))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = { vm.selectDate(date.minusDays(7)) }, enabled = !vm.busy) { Icon(Icons.Outlined.ChevronLeft, "Előző hét") }
            TextButton(onClick = { vm.selectDate(today()) }, enabled = !vm.busy, modifier = Modifier.weight(1f)) {
                Text(if (date == today()) "Ma" else date.format(DateTimeFormatter.ofPattern("MMM d., EEEE", hu)),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
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
        AnimatedContent(targetState = date, label = "timetableDate", transitionSpec = {
            val direction = if (targetState > initialState) 1 else -1
            (slideInHorizontally(tween(FirkaMotion.enterMillis, easing = FirkaMotion.easeInOut)) { it * direction } togetherWith
                slideOutHorizontally(tween(FirkaMotion.enterMillis, easing = FirkaMotion.easeInOut)) { -it * direction }).using(null)
        }) { shownDate ->
            val shownLessons = vm.data?.lessons.orEmpty().filter { it.start.toLocalDate() == shownDate }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                if (shownLessons.none { it.hasSubject && it.state != "Elmaradt" }) item { Empty("Ezen a napon nincsenek tanóráid a letöltött órarend szerint.") }
                items(shownLessons) { LessonRow(it, vm.appearance) }
            }
        }
    }
}
@Composable
private fun Grades(vm: GrafitViewModel) {
    var selectedSubject by rememberSaveable { mutableStateOf<String?>(null) }
    val subjects = remember(vm.data) {
        val collator = java.text.Collator.getInstance(hu)
        vm.data?.gradeSummaries().orEmpty().sortedWith { a, b -> collator.compare(a.name, b.name) }
    }
    val yearly = remember(subjects) { annualAverageOfSubjects(subjects)?.let(::roundedGradeAverage) }
    val scholarship = technikumScholarshipEstimate(yearly)
    val hidden = vm.appearance.hideGrades
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Heading("Jegyeid", "Tantárgyak és átlagok") }
        if (subjects.isEmpty()) item { Empty("Még nincsenek tantárgyak vagy jegyek.") }
        subjects.forEach { summary ->
            val name = summary.name
            val grades = summary.grades
            item(key = "subject:$name") {
                val expanded = selectedSubject == name
                GrafitCard(modifier = Modifier.animateItem(), onClick = { selectedSubject = if (expanded) null else name }) {
                    Row(Modifier.fillMaxWidth().padding(if (vm.appearance.compact) 12.dp else 18.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(name, fontWeight = FontWeight.Medium)
                            Text("Tantárgy átlag", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(if (hidden) "••" else average(grades)?.let { "%.2f".format(hu, roundedGradeAverage(it)) } ?: "—",
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary)
                        Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            if (expanded) "Jegyek összecsukása" else "Jegyek megnyitása")
                    }
                }
            }
            if (selectedSubject == name) {
                if (grades.isEmpty()) item(key = "empty:$name") { Empty("Ebből a tantárgyból még nincs jegy.") }
                items(grades.sortedByDescending { it.date }) { GradeRow(it, vm.appearance) }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GradeSummaryWidget("Éves átlag", if (hidden) "••" else yearly?.let { "%.2f".format(hu, it) } ?: "—",
                    "Tantárgyi átlagok átlaga", Icons.Outlined.Insights, Modifier.weight(1f).fillMaxHeight())
                GradeSummaryWidget("Meghatározott ösztöndíj átlag alapján",
                    if (hidden) "••" else scholarship?.let { java.text.NumberFormat.getIntegerInstance(hu).format(it) + " Ft" } ?: "—",
                    "Becsült összeg / hó", Icons.Outlined.AccountBalanceWallet, Modifier.weight(1f).fillMaxHeight())
            }
        }
        item {
            Text("Az éves átlag az elérhető, súlyozott tantárgyi átlagok számtani átlaga, nem a bizonyítvány átlaga. " +
                "Az ösztöndíj a jelenlegi átlag alapján becsült összeg a technikumi szakirányú oktatásra. " +
                "A hivatalos összeg az előző tanév év végi osztályzataitól és a jogosultságtól függ. " +
                "Ágazati alapoktatásban jogosultság esetén havi 8 000 Ft jár. " +
                "Hiányzó vagy 2,00 alatti átlagnál nem adunk becslést.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { uriHandler.openUri("https://szfszc.hu/kepzesek/osztondijak") }) {
                Text("Ösztöndíjszabályok · SZFSZC")
            }
        }
    }
}

@Composable
private fun GradeSummaryWidget(label: String, value: String, description: String, icon: ImageVector, modifier: Modifier) {
    GrafitCard(modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
            GrafitCard() {
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
        FirkaSwitch(label = title, checked = checked, onCheckedChange = onChange)
    }
}
@Composable
private fun Settings(vm: GrafitViewModel, onLogout: () -> Unit) {
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val a = vm.appearance
    var about by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Heading("Legyen a tiéd.", "Személyre szabás") }
        item {
            GrafitCard(color = MaterialTheme.colorScheme.primaryContainer) {
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
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                seeds.forEachIndexed { index, color ->
                    val labels = listOf("Zsálya", "Kék", "Mályva", "Agyag", "Grafit", "Türkiz", "Bordó")
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
            Slider(value = a.radius, onValueChange = { vm.customize(a.copy(radius = it)) }, valueRange = 0f..40f, steps = 9)
            Toggle("Kompakt kártyák", a.compact, { vm.customize(a.copy(compact = it)) })
            Toggle("Jegyek elrejtése", a.hideGrades, { vm.customize(a.copy(hideGrades = it)) }, "Érintsd meg a jegyet a felfedéshez")
        }
        item { NotificationSettings(vm) }
        item { AppearanceOptions(vm) }
        item {
            Section("Mozgás és képfrissítés")
            val highRefreshSupported = supports120Hz()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = a.refreshRate == 60 || !highRefreshSupported, onClick = { vm.customize(a.copy(refreshRate = 60)) }, label = { Text("60 Hz") })
                FilterChip(selected = a.refreshRate == 120 && highRefreshSupported, onClick = { vm.customize(a.copy(refreshRate = 120)) },
                    enabled = highRefreshSupported, label = { Text("120 Hz") })
            }
            Text(if (highRefreshSupported) "A tényleges képfrissítést az Android és az energiatakarékosság is szabályozza."
                else "Ez a kijelző jelenleg nem kínál 120 Hz-es módot. A Grafit 60 Hz-et kér.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Section("Navigáció")
            Toggle("Jegyek az alsó sávban", a.showGrades, { vm.customize(a.copy(showGrades = it)) })
            Toggle("Hiányzások az alsó sávban", a.showAbsences, { vm.customize(a.copy(showAbsences = it)) })
        }
        item {
            Section("Alkalmazásfrissítések")
            Text("Telepített változat: ${vm.updates.version}")
            OutlinedButton(onClick = { vm.updates.check(manual = true) }, enabled = !vm.updates.checking) {
                Text(if (vm.updates.checking) "Ellenőrzés…" else "GitHub-frissítés keresése")
            }
            vm.updates.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Text("Indításkor egyszer ellenőrizzük a stabil GitHub-kiadásokat. A letöltési oldalt a böngésződ nyitja meg.", style = MaterialTheme.typography.bodySmall)
        }
        item {
            HorizontalDivider()
            TextButton(onClick = { about = true }) { Icon(Icons.Outlined.Info, null); Spacer(Modifier.width(10.dp)); Text("A Grafitról és a licencről") }
            TextButton(onClick = onLogout, enabled = !vm.busy && !vm.profileBusy) { Icon(Icons.AutoMirrored.Outlined.Logout, null); Spacer(Modifier.width(10.dp)); Text(if (vm.demo) "Kilépés a bemutatóból" else "Kijelentkezés") }
        }
    }
    if (about) AlertDialog(onDismissRequest = { about = false }, title = { Text("Grafit ${BuildConfig.VERSION_NAME}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Natív Android kliens, Kotlin és Jetpack Compose alapokon.\n\nA KRÉTA-integráció és a navigáció a QwIT Development és közreműködői Firka projektjének adaptációja.\n\nCC BY-NC-SA 4.0 · Nem kereskedelmi felhasználás.\ngithub.com/QwIT-Development/firka\n\nA Grafit független alkalmazás.")
                TextButton(onClick = { uriHandler.openUri("https://grafit.kodiweb.hu/") }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.OpenInNew, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("grafit.kodiweb.hu")
                }
                Text("Made By Kodi ❤️", Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        },
        confirmButton = { TextButton(onClick = { about = false }) { Text("Rendben") } })
}
