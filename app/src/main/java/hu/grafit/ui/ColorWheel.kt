package hu.grafit.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.*

internal fun wheelCoordinates(x: Float, y: Float, radius: Float): Pair<Float, Float> {
    val hue = ((atan2(y, x) * 180f / PI.toFloat()) + 360f) % 360f
    return hue to (hypot(x, y) / radius.coerceAtLeast(1f)).coerceIn(0f, 1f)
}
internal fun colorHsv(c: Color): FloatArray {
    val max = maxOf(c.red, c.green, c.blue)
    val min = minOf(c.red, c.green, c.blue)
    val delta = max - min
    val hue = when {
        delta == 0f -> 0f
        max == c.red -> 60f * ((c.green - c.blue) / delta % 6f)
        max == c.green -> 60f * ((c.blue - c.red) / delta + 2f)
        else -> 60f * ((c.red - c.green) / delta + 4f)
    }
    return floatArrayOf((hue + 360f) % 360f, if (max == 0f) 0f else delta / max, max)
}

@Composable
internal fun ColorWheel(value: String, onChange: (String) -> Unit) {
    val initial = remember { colorHsv(runCatching { cssColor(value) }.getOrDefault(Color.White)) }
    var hue by remember { mutableFloatStateOf(initial[0]) }
    var saturation by remember { mutableFloatStateOf(initial[1]) }
    var brightness by remember { mutableFloatStateOf(initial[2]) }
    LaunchedEffect(value) {
        if (value.matches(Regex("#[0-9a-fA-F]{3}([0-9a-fA-F]{3})?"))) {
            val hsv = colorHsv(cssColor(value))
            if (hsv[1] > 0f) hue = hsv[0]
            saturation = hsv[1]; brightness = hsv[2]
        }
    }
    fun emit() { onChange("#%06X".format(Color.hsv(hue, saturation, brightness).toArgb() and 0xFFFFFF)) }
    val choose by rememberUpdatedState<(Offset, Float) -> Unit>({ point, radius ->
        val hs = wheelCoordinates(point.x, point.y, radius)
        hue = hs.first; saturation = hs.second
        if (brightness == 0f) brightness = 1f
        emit()
    })
    Canvas(Modifier.fillMaxWidth().height(210.dp).semantics { contentDescription = "Színkör: árnyalat és telítettség" }
        .pointerInput(Unit) { detectTapGestures { choose(it - Offset(size.width / 2f, size.height / 2f), minOf(size.width, size.height) / 2f - 8.dp.toPx()) } }
        .pointerInput(Unit) { detectDragGestures { change, _ ->
            change.consume()
            choose(change.position - Offset(size.width / 2f, size.height / 2f), minOf(size.width, size.height) / 2f - 8.dp.toPx())
        } }) {
        val radius = size.minDimension / 2f - 8.dp.toPx()
        drawCircle(Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red), center), radius)
        drawCircle(Brush.radialGradient(listOf(Color.White, Color.Transparent), center, radius), radius)
        drawCircle(Color.Black.copy(alpha = 1f - brightness), radius)
        val angle = hue * PI.toFloat() / 180f
        val marker = center + Offset(cos(angle), sin(angle)) * saturation * radius
        drawCircle(Color.Black, 9.dp.toPx(), marker)
        drawCircle(Color.White, 7.dp.toPx(), marker)
        drawCircle(Color.hsv(hue, saturation, brightness), 5.dp.toPx(), marker)
    }
    Text("Fényerő · ${(brightness * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium)
    Slider(brightness, { brightness = it; emit() }, modifier = Modifier.semantics { contentDescription = "Szín fényereje" })
    // Accessible alternatives to touch gestures on the circular picker.
    Text("Árnyalat", style = MaterialTheme.typography.labelMedium)
    Slider(hue, { hue = it; emit() }, valueRange = 0f..359.99f, modifier = Modifier.semantics { contentDescription = "Szín árnyalata" })
    Text("Telítettség", style = MaterialTheme.typography.labelMedium)
    Slider(saturation, { saturation = it; emit() }, modifier = Modifier.semantics { contentDescription = "Szín telítettsége" })
}
