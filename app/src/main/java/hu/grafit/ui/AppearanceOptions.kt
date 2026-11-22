package hu.grafit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
internal fun AppearanceOptions(vm: GrafitViewModel) {
    val a = vm.appearance
    var cssEditor by rememberSaveable { mutableStateOf(false) }
    var colorEditor by rememberSaveable { mutableStateOf<String?>(null) }
    var allColors by rememberSaveable { mutableStateOf(false) }
    var resetColors by rememberSaveable { mutableStateOf(false) }
    var reset by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider()
        Text("Az egész alkalmazás megjelenése", style = MaterialTheme.typography.titleLarge)
        Text("A színek, betűk és kártyastílusok minden natív képernyőre és menüre érvényesek.", style = MaterialTheme.typography.bodySmall)
        AppearanceToggle("Pill alakú felső eszköztár", a.pillToolbar) { vm.customize(a.copy(pillToolbar = it)) }
        AppearanceToggle("Lebegő, pill alakú alsó sáv", a.pillNavigation) { vm.customize(a.copy(pillNavigation = it)) }
        AppearanceToggle("Navigációs feliratok mindig", a.navigationLabels) { vm.customize(a.copy(navigationLabels = it)) }
        Text("Kártyák stílusa", style = MaterialTheme.typography.titleMedium)
        Choices(listOf("filled" to "Kitöltött", "outlined" to "Körvonalas", "elevated" to "Árnyékos"), a.cardStyle) { vm.customize(a.copy(cardStyle = it)) }
        Text("Betűméret · ${(a.textScale * 100).toInt()}%", style = MaterialTheme.typography.titleMedium)
        Slider(a.textScale, { vm.customize(a.copy(textScale = it)) }, valueRange = .85f..1.3f, steps = 8)
        Text("A rendszer akadálymentesítési betűmérete továbbra is érvényes.", style = MaterialTheme.typography.bodySmall)
        Choices(listOf("sans" to "Letisztult", "serif" to "Talpas", "mono" to "Monospace"), a.fontFamily) { vm.customize(a.copy(fontFamily = it)) }
        AppearanceToggle("Tiszta fekete sötét háttér", a.pureBlack) { vm.customize(a.copy(pureBlack = it)) }
        AppearanceToggle("Rezgés lapváltáskor", a.haptics) { vm.customize(a.copy(haptics = it)) }
        HorizontalDivider()
        Text("Saját színek és jegyszínek", style = MaterialTheme.typography.titleLarge)
        Text("A saját színek a Material You és a kiválasztott paletta színeit felülírják. A jegyszínek minden jegykártyán érvényesek.", style = MaterialTheme.typography.bodySmall)
        val basic = listOf("primary" to "Elsődleges szín", "secondary" to "Másodlagos szín", "tertiary" to "Harmadlagos szín",
            "background" to "Háttér", "surface" to "Felületek", "card" to "Kártyák háttere") + (1..5).map { "grade-$it" to "$it-es jegyek színe" }
        val tokens = ThemeCss.parse(a.customCss)
        (basic + if (allColors) ThemeCss.colors.filter { name -> basic.none { it.first == name } }.map { it to "CSS · $it" } else emptyList())
            .forEach { (name, label) ->
                OutlinedButton(onClick = { colorEditor = name }, modifier = Modifier.fillMaxWidth()) {
                    val color = tokens[name]?.let(::cssColor) ?: roleDefault(name)
                    Box(Modifier.size(24.dp).background(color, CircleShape))
                    Spacer(Modifier.width(12.dp))
                    Text(label, Modifier.weight(1f))
                    Text(tokens[name] ?: "Alap", style = MaterialTheme.typography.labelSmall)
                }
            }
        OutlinedButton(onClick = { resetColors = true }, enabled = tokens.keys.any { it in ThemeCss.colors }, modifier = Modifier.fillMaxWidth()) {
            Text("Összes saját szín visszaállítása")
        }
        TextButton(onClick = { allColors = !allColors }) { Text(if (allColors) "Kevesebb színszerep" else "Összes színszerep") }
        OutlinedButton(onClick = { cssEditor = true }, modifier = Modifier.fillMaxWidth()) { Text("Egyéni CSS-téma szerkesztése") }
        Text("Támogatott: --grafit- CSS-változók egy :root blokkban. Színek: #RGB vagy #RRGGBB; méretek: natív képernyőegységek. Webes szelektorok, külső stíluslapok és tetszőleges böngészős CSS nem használható.", style = MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        Text("Kezdőlap elemei", style = MaterialTheme.typography.titleLarge)
        AppearanceToggle("Tanulmányi összesítő", a.homeMetrics) { vm.customize(a.copy(homeMetrics = it)) }
        AppearanceToggle("Legutóbbi jegyek", a.homeGrades) { vm.customize(a.copy(homeGrades = it)) }
        AppearanceToggle("Teendők és hírek", a.homeTasks) { vm.customize(a.copy(homeTasks = it)) }
        AppearanceToggle("Mai órarend", a.homeTimetable) { vm.customize(a.copy(homeTimetable = it)) }
        TextButton(onClick = { reset = true }) { Text("Megjelenés alaphelyzetbe állítása") }
    }
    colorEditor?.let { role ->
        val fallback = roleDefault(role)
        var value by rememberSaveable(role) { mutableStateOf(ThemeCss.parse(a.customCss)[role] ?: "#%06X".format(fallback.toArgb() and 0xFFFFFF)) }
        val validation = runCatching { ThemeCss.replace(a.customCss, role, value) }
        AlertDialog(onDismissRequest = { colorEditor = null }, title = { Text("Szín · $role") },
            text = { Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ColorWheel(value) { value = it }
                OutlinedTextField(value, { value = it }, singleLine = true, label = { Text("Hex szín") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii), isError = validation.isFailure)
                if (validation.isSuccess) Box(Modifier.fillMaxWidth().height(56.dp).background(cssColor(value), MaterialTheme.shapes.medium))
                else Text(validation.exceptionOrNull()?.message.orEmpty(), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { vm.customize(a.copy(customCss = ThemeCss.replace(a.customCss, role, null))); colorEditor = null }) { Text("Eredeti szín visszaállítása") }
            } }, confirmButton = { TextButton(enabled = validation.isSuccess, onClick = {
                vm.customize(a.copy(customCss = validation.getOrThrow())); colorEditor = null
            }) { Text("Alkalmazás") } }, dismissButton = { TextButton(onClick = { colorEditor = null }) { Text("Mégse") } })
    }
    if (cssEditor) {
        var draft by rememberSaveable { mutableStateOf(a.customCss) }
        val validation = runCatching { ThemeCss.parse(draft) }
        AlertDialog(onDismissRequest = { cssEditor = false }, title = { Text("Egyéni CSS-téma") }, text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(draft, { draft = it }, modifier = Modifier.fillMaxWidth(), minLines = 6,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii), isError = validation.isFailure)
                validation.exceptionOrNull()?.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = { draft = ThemeCss.template }) { Text("Példa betöltése") }
                Text("Színek: ${ThemeCss.colors.joinToString()}. Egyéb: radius, font-scale, font-family (sans/serif/mono), toolbar és navigation (pill/standard), card-style (filled/outlined/elevated). Minden név előtt --grafit- áll.", style = MaterialTheme.typography.bodySmall)
            }
        }, confirmButton = { TextButton(enabled = validation.isSuccess, onClick = {
            vm.customize(a.copy(customCss = draft)); cssEditor = false
        }) { Text("Alkalmazás") } }, dismissButton = { TextButton(onClick = { cssEditor = false }) { Text("Mégse") } })
    }
    if (resetColors) AlertDialog(onDismissRequest = { resetColors = false }, title = { Text("Saját színek visszaállítása?") },
        text = { Text("Az összes saját felületi és jegyszín törlődik a CSS-témából. A kiválasztott paletta vagy Material You színei térnek vissza. Az elrendezés, betűk és egyéb beállítások megmaradnak.") },
        confirmButton = { TextButton(onClick = {
            vm.customize(a.copy(customCss = ThemeCss.resetColors(a.customCss)))
            resetColors = false
        }) { Text("Színek visszaállítása") } },
        dismissButton = { TextButton(onClick = { resetColors = false }) { Text("Mégse") } })
    if (reset) AlertDialog(onDismissRequest = { reset = false }, title = { Text("Megjelenés visszaállítása?") },
        text = { Text("A téma, CSS és elrendezés alapértékekre áll. A profilod és az iskolai adatok megmaradnak.") },
        confirmButton = { TextButton(onClick = { vm.customize(Appearance()); reset = false }) { Text("Visszaállítás") } },
        dismissButton = { TextButton(onClick = { reset = false }) { Text("Mégse") } })
}

@Composable
private fun AppearanceToggle(label: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f).padding(end = 12.dp))
        FirkaSwitch(label, checked, change)
    }
}
@Composable
private fun Choices(options: List<Pair<String, String>>, selected: String, change: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) -> FilterChip(selected == value, { change(value) }, label = { Text(label) }) }
    }
}
@Composable
private fun roleDefault(role: String): Color {
    val c = MaterialTheme.colorScheme
    return when (role) {
        "primary" -> c.primary; "secondary" -> c.secondary; "tertiary" -> c.tertiary
        "background" -> c.background; "surface" -> c.surface; "card" -> c.surfaceContainerLow
        "on-primary" -> c.onPrimary; "primary-container" -> c.primaryContainer; "on-primary-container" -> c.onPrimaryContainer
        "on-secondary" -> c.onSecondary; "secondary-container" -> c.secondaryContainer; "on-secondary-container" -> c.onSecondaryContainer
        "on-tertiary" -> c.onTertiary; "tertiary-container" -> c.tertiaryContainer; "on-tertiary-container" -> c.onTertiaryContainer
        "on-background" -> c.onBackground; "on-surface" -> c.onSurface; "surface-variant" -> c.surfaceVariant
        "on-surface-variant" -> c.onSurfaceVariant; "outline" -> c.outline; "outline-variant" -> c.outlineVariant
        "error" -> c.error; "on-error" -> c.onError; "error-container" -> c.errorContainer; "on-error-container" -> c.onErrorContainer
        else -> if (role.startsWith("grade-")) listOf("#BA1A1A", "#B65B16", "#927000", "#4D7C4A", "#216B4A")[role.last().digitToInt() - 1].let(::cssColor) else c.onSurface
    }
}
