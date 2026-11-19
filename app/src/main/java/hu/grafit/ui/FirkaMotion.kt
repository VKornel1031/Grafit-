package hu.grafit.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** Timings and curves adapted from Firka's router, feed, timetable and grade reveal. */
internal object FirkaMotion {
    val easeOut = CubicBezierEasing(0f, 0f, .58f, 1f)
    val easeIn = CubicBezierEasing(.42f, 0f, 1f, 1f)
    val easeInOut = CubicBezierEasing(.42f, 0f, .58f, 1f)
    const val tabMillis = 175
    const val enterMillis = 300
    const val exitMillis = 250
    const val flipMillis = 400
}

@Composable
internal fun FirkaLoadedContent(loaded: Boolean, content: @Composable AnimatedVisibilityScope.() -> Unit) {
    val state = remember { MutableTransitionState(loaded) }
    state.targetState = loaded
    AnimatedVisibility(visibleState = state,
        enter = fadeIn(tween(FirkaMotion.enterMillis, easing = FirkaMotion.easeOut)) +
            slideInVertically(tween(FirkaMotion.enterMillis, easing = FirkaMotion.easeOut)) { it * 4 / 100 },
        exit = fadeOut(tween(FirkaMotion.exitMillis, easing = FirkaMotion.easeIn)), content = content)
}

@Composable
internal fun FirkaSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val position by animateFloatAsState(if (checked) 1f else 0f,
        tween(150, easing = FirkaMotion.easeOut), label = "togglePosition")
    val colors = MaterialTheme.colorScheme
    val track by animateColorAsState(if (checked) colors.primary else colors.primary.copy(alpha = .15f),
        tween(150, easing = FirkaMotion.easeOut), label = "toggleTrack")
    val thumb by animateColorAsState(if (checked) colors.surface else colors.primary,
        tween(150, easing = FirkaMotion.easeOut), label = "toggleThumb")
    Box(
        Modifier.size(52.dp, 48.dp)
            .semantics { contentDescription = label }
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        contentAlignment = Alignment.Center) {
        Box(Modifier.size(40.dp, 24.dp)
            .background(track, CircleShape)) {
            Box(Modifier
                .align(Alignment.CenterStart).offset(x = (2 + position * 16).dp).size(20.dp)
                .background(thumb, CircleShape))
        }
    }
}
