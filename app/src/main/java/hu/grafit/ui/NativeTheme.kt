package hu.grafit.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

internal val LocalAppearance = staticCompositionLocalOf { Appearance() }
internal val LocalThemeTokens = staticCompositionLocalOf<Map<String, String>> { emptyMap() }

internal fun cssColor(value: String): Color {
    val hex = value.removePrefix("#").let { if (it.length == 3) it.map { c -> "$c$c" }.joinToString("") else it }
    return Color((0xFF000000L or hex.toLong(16)).toInt())
}
internal fun contrastColor(background: Color): Color = if (background.luminance() > .179f) Color.Black else Color.White

internal fun customColors(base: ColorScheme, tokens: Map<String, String>): ColorScheme {
    fun color(name: String, fallback: Color) = tokens[name]?.let(::cssColor) ?: fallback
    fun on(name: String, background: String, fallback: Color, actual: Color) =
        color(name, if (tokens.containsKey(background)) contrastColor(actual) else fallback)
    val background = color("background", base.background)
    val surface = color("surface", base.surface)
    val primary = color("primary", base.primary)
    val secondary = color("secondary", base.secondary)
    val tertiary = color("tertiary", base.tertiary)
    val pc = color("primary-container", if (tokens.containsKey("primary")) lerp(primary, background, .82f) else base.primaryContainer)
    val sc = color("secondary-container", if (tokens.containsKey("secondary")) lerp(secondary, background, .82f) else base.secondaryContainer)
    val tc = color("tertiary-container", if (tokens.containsKey("tertiary")) lerp(tertiary, background, .82f) else base.tertiaryContainer)
    return base.copy(primary = primary, surfaceTint = primary, onPrimary = on("on-primary", "primary", base.onPrimary, primary),
        primaryContainer = pc, onPrimaryContainer = color("on-primary-container", if (pc != base.primaryContainer) contrastColor(pc) else base.onPrimaryContainer),
        secondary = secondary, onSecondary = on("on-secondary", "secondary", base.onSecondary, secondary),
        secondaryContainer = sc, onSecondaryContainer = color("on-secondary-container", if (sc != base.secondaryContainer) contrastColor(sc) else base.onSecondaryContainer),
        tertiary = tertiary, onTertiary = on("on-tertiary", "tertiary", base.onTertiary, tertiary),
        tertiaryContainer = tc, onTertiaryContainer = color("on-tertiary-container", if (tc != base.tertiaryContainer) contrastColor(tc) else base.onTertiaryContainer),
        background = background, onBackground = on("on-background", "background", base.onBackground, background),
        surface = surface, onSurface = on("on-surface", "surface", base.onSurface, surface),
        surfaceVariant = color("surface-variant", base.surfaceVariant),
        onSurfaceVariant = color("on-surface-variant", if (tokens.containsKey("surface")) contrastColor(surface).copy(alpha = .78f) else base.onSurfaceVariant),
        surfaceContainerLow = color("card", if (tokens.containsKey("surface")) lerp(surface, primary, .04f) else base.surfaceContainerLow),
        surfaceContainer = if (tokens.containsKey("surface")) surface else base.surfaceContainer,
        surfaceContainerHigh = if (tokens.containsKey("surface")) lerp(surface, primary, .08f) else base.surfaceContainerHigh,
        outline = color("outline", base.outline), outlineVariant = color("outline-variant", base.outlineVariant),
        error = color("error", base.error), onError = on("on-error", "error", base.onError, color("error", base.error)),
        errorContainer = color("error-container", base.errorContainer), onErrorContainer = on("on-error-container", "error-container", base.onErrorContainer, color("error-container", base.errorContainer)))
}

internal fun customTypography(a: Appearance): Typography {
    val family = when (a.fontFamily) { "serif" -> FontFamily.Serif; "mono" -> FontFamily.Monospace; else -> FontFamily.SansSerif }
    fun TextStyle.custom() = copy(fontSize = fontSize * a.textScale, lineHeight = lineHeight * a.textScale, fontFamily = family)
    val t = Typography()
    return t.copy(displayLarge = t.displayLarge.custom(), displayMedium = t.displayMedium.custom(), displaySmall = t.displaySmall.custom(),
        headlineLarge = t.headlineLarge.custom(), headlineMedium = t.headlineMedium.custom(), headlineSmall = t.headlineSmall.custom(),
        titleLarge = t.titleLarge.custom(), titleMedium = t.titleMedium.custom(), titleSmall = t.titleSmall.custom(),
        bodyLarge = t.bodyLarge.custom(), bodyMedium = t.bodyMedium.custom(), bodySmall = t.bodySmall.custom(),
        labelLarge = t.labelLarge.custom(), labelMedium = t.labelMedium.custom(), labelSmall = t.labelSmall.custom())
}

@Composable
internal fun GrafitCard(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val style = LocalAppearance.current.cardStyle
    val border = if (style == "outlined") BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f)) else null
    val elevation = CardDefaults.cardElevation(defaultElevation = if (style == "elevated") 5.dp else 0.dp)
    val contentColor = if (color == MaterialTheme.colorScheme.primaryContainer) MaterialTheme.colorScheme.onPrimaryContainer
        else if ("on-surface" in LocalThemeTokens.current) MaterialTheme.colorScheme.onSurface else contrastColor(color)
    val colors = CardDefaults.cardColors(containerColor = color, contentColor = contentColor)
    if (onClick == null) Card(modifier = modifier, shape = MaterialTheme.shapes.large, colors = colors, border = border, elevation = elevation, content = content)
    else Card(onClick = onClick, modifier = modifier, shape = MaterialTheme.shapes.large, colors = colors, border = border, elevation = elevation, content = content)
}

@Composable
internal fun gradeAccent(number: Int?): Color? {
    val defaults = listOf("#BA1A1A", "#B65B16", "#927000", "#4D7C4A", "#216B4A")
    return number?.takeIf { it in 1..5 }?.let { cssColor(LocalThemeTokens.current["grade-$it"] ?: defaults[it - 1]) }
}
@Composable
internal fun gradeContainer(number: Int?): Color = gradeAccent(number)?.let {
    lerp(it, MaterialTheme.colorScheme.surface, .80f)
} ?: MaterialTheme.colorScheme.secondaryContainer
