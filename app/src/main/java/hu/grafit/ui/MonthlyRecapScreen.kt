package hu.grafit.ui

import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import hu.grafit.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.roundToInt

private val recapHu = Locale.forLanguageTag("hu-HU")
private fun monthName(month: YearMonth) = month.format(DateTimeFormatter.ofPattern("yyyy. MMMM", recapHu))
private fun recapNumber(value: Double?) = value?.let { "%.2f".format(recapHu, it) } ?: "—"
@Composable
internal fun MonthlyRecapScreen(vm: GrafitViewModel) {
    val current by produceState(YearMonth.from(today())) { while (isActive) { value = YearMonth.from(today()); delay(60_000) } }
    var selected by rememberSaveable { mutableStateOf(current.toString()) }
    var lastCurrent by rememberSaveable { mutableStateOf(current.toString()) }
    LaunchedEffect(current) { if (selected == lastCurrent) selected = current.toString(); lastCurrent = current.toString() }
    val month = YearMonth.parse(selected)
    val recap = remember(vm.data, vm.study, month) { vm.data?.let { monthlyRecap(it, vm.study, month) } }
    var story by rememberSaveable { mutableStateOf(false) }
    var reveal by remember(vm.activeAccount, vm.demo, vm.appearance.hideGrades) { mutableStateOf(!vm.appearance.hideGrades) }
    var confirm by remember { mutableStateOf(false) }
    val ongoing = month == current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("A hónapod", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text("A saját iskolai történeted, hónapról hónapra.", style = MaterialTheme.typography.bodyLarge)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { selected = month.minusMonths(1).toString() }, enabled = month > current.minusMonths(23)) { Icon(Icons.Outlined.ChevronLeft, "Előző hónap") }
            Text(monthName(month).replaceFirstChar { it.uppercase(recapHu) }, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { selected = month.plusMonths(1).toString() }, enabled = month < current) { Icon(Icons.Outlined.ChevronRight, "Következő hónap") }
        }
        GrafitCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            Text(if (ongoing) "A havi előzetesed már elérhető." else "Nézd vissza ezt a hónapot.", style = MaterialTheme.typography.headlineSmall)
            Text(if (ongoing) "Nem kell megvárnod a hónap végét. A mostanáig mentett adataidból már ma megnézheted az összefoglalót." else "Jegyek, előrelépések, határidők és saját tervek a mentett adataid alapján.")
            Button(onClick = { if (reveal) story = true else confirm = true }, enabled = recap != null, modifier = Modifier.fillMaxWidth()) { Text("Összefoglaló megnyitása") }
        } }
        Text("Minden hónapban automatikusan új időszak indul; az összefoglaló bármelyik nap visszanézhető. A folyamatban lévő hónap az új szinkronok után frissül. Az első előzetes már ezzel a frissítéssel megnyílik.")
        Text("Az összefoglaló kizárólag a mentett iskolai adatokból és saját terveidből készül. Nem tölt le külön adatokat, és nem állít teljes jelenléti vagy hivatalos tanulmányi kimutatást.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (recap == null) Text("Előbb töltsd le az iskolai adataidat, vagy próbáld ki bemutató módban.")
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Havi adatok felfedése?") },
        text = { Text("Az összefoglaló a rejtett jegyeidet és azok statisztikáit is megmutatja. Ez csak erre a megnyitásra vonatkozik.") },
        confirmButton = { TextButton(onClick = { confirm = false; reveal = true; story = true }) { Text("Felfedés") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Mégse") } })
    if (story && recap != null && reveal) key(vm.activeAccount, vm.demo, month) {
        RecapStories(recap, ongoing, onDismiss = { story = false })
    }
}
@Composable
private fun RecapStories(recap: MonthlyRecap, ongoing: Boolean, onDismiss: () -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    var paused by rememberSaveable { mutableStateOf(false) }
    var held by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var share by remember { mutableStateOf(false) }
    var resumed by remember { mutableStateOf(true) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val progress = remember(page) { Animatable(0f) }
    val motion = remember { Animatable(0f) }
    val animations = android.animation.ValueAnimator.areAnimatorsEnabled()
    val playing = !paused && !held && !dragging && resumed && !share && animations
    LaunchedEffect(page, playing) {
        if (playing && page in 1..6) { progress.animateTo(1f, tween(((1f - progress.value) * 7500).roundToInt().coerceAtLeast(1), easing = LinearEasing)); page++ }
    }
    LaunchedEffect(playing) {
        if (playing) while (isActive) { motion.animateTo(1f, tween(((1f - motion.value) * 16000).roundToInt().coerceAtLeast(1), easing = LinearEasing)); motion.snapTo(0f) }
    }
    val theme = LocalThemeTokens.current
    val intro = theme["primary"]?.let(::cssColor) ?: Color(0xFF4C2A69)
    val dark = theme["background"]?.let(::cssColor) ?: Color(0xFF141319)
    val background = if (page == 0 || page == 7) intro else dark
    val foreground = contrastColor(background)
    fun next() { if (page < 7) page++ }
    fun previous() { if (page > 0) page-- }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.let { window ->
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = foreground == Color.Black
                controller.isAppearanceLightNavigationBars = foreground == Color.Black
            }
        }
        Surface(Modifier.fillMaxSize(), color = background, contentColor = foreground) {
            Box(Modifier.fillMaxSize().safeDrawingPadding()
                .pointerInput(page) { detectTapGestures(onPress = { held = true; try { tryAwaitRelease() } finally { held = false } },
                    onLongPress = {}, onTap = { point -> if (point.x < size.width * .35f) previous() else next() }) }
                .pointerInput(page) {
                    var distance = 0f
                    detectHorizontalDragGestures(onDragStart = { distance = 0f; dragging = true }, onDragEnd = { dragging = false; if (distance < -size.width * .15f) next() else if (distance > size.width * .15f) previous() },
                        onDragCancel = { dragging = false }) { change, amount -> change.consume(); distance += amount }
                }) {
                StoryDecorations(motion.value, page, foreground)
                Column(Modifier.fillMaxSize().padding(horizontal = 22.dp)) {
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        (0..7).forEach { index -> LinearProgressIndicator(progress = { when { index < page -> 1f; index == page -> if (page == 7) 1f else progress.value; else -> 0f } },
                            modifier = Modifier.weight(1f).height(3.dp), color = foreground, trackColor = foreground.copy(alpha = .22f), drawStopIndicator = {}) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("grafit · A hónapod", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        IconButton(onClick = { paused = !paused }) { Icon(if (paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause, if (paused) "Folytatás" else "Szünet") }
                        IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "Összefoglaló bezárása") }
                    }
                    Text(monthName(recap.month) + if (ongoing) " · havi előzetes" else " · havi összefoglaló", style = MaterialTheme.typography.labelMedium)
                    AnimatedContent(page, modifier = Modifier.weight(1f).fillMaxWidth(), label = "recapStory", transitionSpec = { fadeIn(tween(450)) togetherWith fadeOut(tween(220)) }) { shown ->
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 32.dp), verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            when (shown) {
                                0 -> {
                                    Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(72.dp))
                                    Spacer(Modifier.height(24.dp))
                                    StoryTitle("Ez a te hónapod.")
                                    StoryText("Jegyek, apró sikerek és a saját ritmusod. Nézzük, mit hozott ${monthName(recap.month)}!")
                                    Spacer(Modifier.height(32.dp))
                                    Button(onClick = ::next, colors = ButtonDefaults.buttonColors(containerColor = foreground, contentColor = background), modifier = Modifier.fillMaxWidth()) { Text("Indítás") }
                                }
                                1 -> {
                                    StoryTitle("Ennyi számszerű jegy érkezett ebben a hónapban")
                                    StoryCount(recap.numericCount)
                                    StoryText("A havi súlyozott átlagod")
                                    Text(recapNumber(recap.average), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                                    if (recap.average != null && recap.previousAverage != null) StoryText("Előző hónap: ${recapNumber(recap.previousAverage)} · eltérés: ${"%+.2f".format(recapHu, recap.average - recap.previousAverage)}")
                                    if (recap.numericCount == 0) StoryText("Erre a hónapra még nincs mentett, súlyozható számszerű jegy.")
                                }
                                2 -> {
                                    StoryTitle(if (recap.improved != null) "Itt léptél előre." else "A hónap egyik erőssége")
                                    Spacer(Modifier.height(24.dp))
                                    Icon(Icons.Outlined.TrendingUp, null, Modifier.size(80.dp))
                                    StoryTitle(recap.improved ?: recap.strongest ?: "Minden kis lépés számít.")
                                    recap.improvement?.let { StoryText("+${recapNumber(it)} a tantárgy havi átlagában az előző hónaphoz képest.") }
                                    if (recap.improved == null && recap.strongest != null) StoryText("Ebben a tantárgyban volt a legmagasabb havi súlyozott átlagod.")
                                    StoryText("Egy nehezebb hónap sem határozza meg, mire vagy képes.")
                                }
                                3 -> {
                                    StoryTitle("Így álltak össze a jegyeid")
                                    Spacer(Modifier.height(28.dp))
                                    (5 downTo 1).forEach { grade ->
                                        val count = recap.distribution[grade] ?: 0
                                        Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text("$grade", Modifier.width(28.dp), style = MaterialTheme.typography.titleLarge)
                                            LinearProgressIndicator(progress = { if (recap.numericCount == 0) 0f else count.toFloat() / recap.numericCount }, modifier = Modifier.weight(1f).height(12.dp), color = foreground, trackColor = foreground.copy(alpha = .15f), drawStopIndicator = {})
                                            Text("$count", Modifier.width(36.dp), textAlign = TextAlign.End)
                                        }
                                    }
                                    StoryText("${recap.activeDays} különböző napon szerepel értékelés a havi adataidban.")
                                }
                                4 -> {
                                    StoryTitle("Ezek a határidők kísérték a hónapodat")
                                    StoryCount(recap.homework + recap.exams)
                                    StoryText("${recap.homework} házi feladat · ${recap.exams} bejelentett dolgozat")
                                    StoryText("A mentett határidők száma, nem a beadott vagy teljesített feladatoké.")
                                }
                                5 -> {
                                    StoryTitle("Egy pillantás a jelenléti adatokra")
                                    StoryCount(recap.absenceCount)
                                    StoryText("mentett hiányzási bejegyzés")
                                    StoryText("${recap.lateMinutes} perc mentett késés")
                                    StoryText("Ez nem teljes jelenléti arány: csak a KRÉTA-ból mentett hiányzásokat és késéseket összesíti.")
                                }
                                6 -> {
                                    StoryTitle("A saját terveidből")
                                    StoryCount(recap.completed)
                                    StoryText("erre a hónapra tervezett tételt jelöltél készre")
                                    Icon(Icons.Outlined.TaskAlt, null, Modifier.padding(24.dp).size(72.dp))
                                    StoryText(if (recap.completed > 0) "Az apró lépések is számítanak. Jó, hogy időt szántál rájuk." else "A következő apró lépést bármikor megtervezheted.")
                                }
                                else -> {
                                    Icon(Icons.Outlined.FavoriteBorder, null, Modifier.size(80.dp))
                                    StoryTitle(if (ongoing) "A történeted még folytatódik." else "Ez volt a hónapod.")
                                    StoryText("Bármikor visszanézheted. Jövő hónapban új történet vár.\nA saját ritmusodban. Grafit.")
                                    Button(onClick = { share = true }, colors = ButtonDefaults.buttonColors(containerColor = foreground, contentColor = background), modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) { Text("Megosztható kártya") }
                                    TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = foreground)) { Text("Vissza az eszközökhöz") }
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = ::previous, enabled = page > 0, colors = ButtonDefaults.textButtonColors(contentColor = foreground)) { Text("Előző") }
                        Text("${page + 1} / 8", Modifier.align(Alignment.CenterVertically), style = MaterialTheme.typography.labelSmall)
                        TextButton(onClick = { if (page == 7) onDismiss() else next() }, colors = ButtonDefaults.textButtonColors(contentColor = foreground)) { Text(if (page == 7) "Kész" else "Következő") }
                    }
                }
            }
        }
        if (share) RecapShareDialog(recap, onDismiss = { share = false })
    }
}
@Composable private fun StoryTitle(text: String) { Text(text, Modifier.fillMaxWidth().padding(vertical = 12.dp), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) }
@Composable private fun StoryText(text: String) { Text(text, Modifier.fillMaxWidth().padding(vertical = 10.dp), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center) }
@Composable private fun StoryCount(value: Int) {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val animated by animateIntAsState(if (started) value else 0, tween(1100, easing = FastOutSlowInEasing), label = "recapCounter")
    Text("$animated", Modifier.padding(vertical = 18.dp), style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Black)
}
@Composable
private fun StoryDecorations(phase: Float, page: Int, color: Color) {
    Canvas(Modifier.fillMaxSize()) {
        val angle = phase * PI.toFloat() * 2
        (0..11).forEach { index ->
            val x = size.width * ((index * .271f + .12f) % 1f)
            val y = size.height * ((index * .177f + .08f) % 1f) + sin(angle + index) * 12.dp.toPx()
            val opacity = if (page == 0 || page == 7) .2f else .08f
            rotate(index * 27f + sin(angle) * 9, Offset(x, y)) {
                if (index % 3 == 0) {
                    drawRoundRect(Brush.linearGradient(listOf(Color(0xFFCE8EEE).copy(alpha = opacity * 2), Color(0xFF7753C6).copy(alpha = opacity))), Offset(x, y), Size(32.dp.toPx(), 42.dp.toPx()), cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()))
                    drawLine(color.copy(alpha = opacity), Offset(x + 8.dp.toPx(), y + 8.dp.toPx()), Offset(x + 8.dp.toPx(), y + 34.dp.toPx()), 2.dp.toPx())
                } else if (index % 3 == 1) {
                    val radius = 5.dp.toPx()
                    drawLine(color.copy(alpha = opacity), Offset(x - radius, y), Offset(x + radius, y), 2.dp.toPx())
                    drawLine(color.copy(alpha = opacity), Offset(x, y - radius), Offset(x, y + radius), 2.dp.toPx())
                } else drawCircle(color.copy(alpha = opacity), 4.dp.toPx(), Offset(x, y))
            }
        }
    }
}
@Composable
private fun RecapShareDialog(recap: MonthlyRecap, onDismiss: () -> Unit) {
    var grades by remember { mutableStateOf(false) }
    var attendance by remember { mutableStateOf(false) }
    var plans by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text("Te választod, mit osztasz meg") }, text = {
        Column {
            Text("A kártyán nincs név, iskola vagy fiókazonosító. Csak a kiválasztott adatok kerülnek rá.")
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(grades, { grades = it }, enabled = !busy); Text("Jegyek és átlag") }
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(attendance, { attendance = it }, enabled = !busy); Text("Hiányzások és késések") }
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(plans, { plans = it }, enabled = !busy); Text("Saját tervek") }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onClick = {
        val includeGrades = grades; val includeAttendance = attendance; val includePlans = plans
        busy = true
        scope.launch {
            runCatching {
                val file = withContext(Dispatchers.IO) { createRecapCard(context.cacheDir, recap, includeGrades, includeAttendance, includePlans) }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply { clipData = ClipData.newRawUri("Grafit havi összefoglaló", uri) }, "Havi összefoglaló megosztása"))
            }.onFailure { error = "A kártya nem osztható meg. Próbáld újra." }
            busy = false
        }
    }, enabled = !busy) { Text("Kártya megosztása") } }, dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Mégse") } })
}
private fun createRecapCard(cache: File, recap: MonthlyRecap, grades: Boolean, attendance: Boolean, plans: Boolean): File {
    val bitmap = Bitmap.createBitmap(1080, 1350, Bitmap.Config.ARGB_8888)
    try {
        val canvas = AndroidCanvas(bitmap)
        canvas.drawColor(android.graphics.Color.rgb(24, 22, 30))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; typeface = Typeface.DEFAULT_BOLD }
        fun line(text: String, y: Float, size: Float) { paint.textSize = size; canvas.drawText(text, 88f, y, paint) }
        line("grafit", 150f, 58f); line("A hónapom", 320f, 86f); line(monthName(recap.month), 400f, 40f)
        var y = 580f
        if (grades) { line("${recap.numericCount} jegy · ${recapNumber(recap.average)} átlag", y, 50f); y += 120f }
        if (attendance) { line("${recap.absenceCount} hiányzás · ${recap.lateMinutes} perc késés", y, 42f); y += 120f }
        if (plans) { line("${recap.completed} készre jelölt saját terv", y, 46f); y += 120f }
        line("A saját ritmusomban.", 1160f, 42f)
        line("Mentett adatok alapján · grafit.kodiweb.hu", 1250f, 26f)
        val folder = File(cache, "school_exports").apply { mkdirs() }
        folder.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 60 * 60 * 1000 }?.forEach(File::delete)
        return File(folder, "grafit-${recap.month}-${UUID.randomUUID()}.png").apply { outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
    } finally { bitmap.recycle() }
}
