package hu.grafit.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Display
import android.view.View
import android.hardware.display.DisplayManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalView
import kotlin.math.abs

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

private fun View.currentDisplay(): Display? = display
    ?: context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)

private fun Display.matchingMode(rate: Int): Display.Mode? = supportedModes
    .filter { it.physicalWidth == mode.physicalWidth && it.physicalHeight == mode.physicalHeight }
    .filter { abs(it.refreshRate - rate) < 1f }
    .minByOrNull { abs(it.refreshRate - rate) }

@Composable
internal fun supports120Hz(): Boolean = LocalView.current.currentDisplay()?.matchingMode(120) != null

/** Android retains control of the effective refresh rate, including power-saving limits. */
@Composable
internal fun ApplyRefreshRate(rate: Int) {
    val view = LocalView.current
    DisposableEffect(view, rate) {
        val window = view.context.activity()?.window
        val originalMode = window?.attributes?.preferredDisplayModeId ?: 0
        val originalRate = window?.attributes?.preferredRefreshRate ?: 0f
        val display = view.currentDisplay()
        val selected = display?.matchingMode(rate) ?: display?.matchingMode(60)
        if (window != null) {
            window.attributes = window.attributes.apply {
                preferredDisplayModeId = selected?.modeId ?: 0
                preferredRefreshRate = selected?.refreshRate ?: 0f
            }
        }
        onDispose {
            if (window != null) window.attributes = window.attributes.apply {
                preferredDisplayModeId = originalMode
                preferredRefreshRate = originalRate
            }
        }
    }
}

@Composable
internal fun ApplySystemBars(dark: Boolean) {
    val view = LocalView.current
    SideEffect {
        view.context.activity()?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
}
