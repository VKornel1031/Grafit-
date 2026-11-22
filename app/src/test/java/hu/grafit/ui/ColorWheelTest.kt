package hu.grafit.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class ColorWheelTest {
    @Test fun wheelUsesAllQuadrantsAndClampsDraggingOutside() {
        assertEquals(0f, wheelCoordinates(10f, 0f, 10f).first, .01f)
        assertEquals(90f, wheelCoordinates(0f, 10f, 10f).first, .01f)
        assertEquals(180f, wheelCoordinates(-10f, 0f, 10f).first, .01f)
        assertEquals(270f, wheelCoordinates(0f, -10f, 10f).first, .01f)
        assertEquals(0f, wheelCoordinates(0f, 0f, 10f).second, .01f)
        assertEquals(1f, wheelCoordinates(100f, 100f, 10f).second, .01f)
    }
    @Test fun hsvRoundTripPreservesExistingColorsIncludingBlackAndWhite() {
        listOf(Color.Black, Color.White, Color.Red, Color.Green, Color.Blue, Color(0xFFCCC2DC)).forEach { color ->
            val hsv = colorHsv(color)
            val actual = Color.hsv(hsv[0], hsv[1], hsv[2])
            assertEquals(color.red, actual.red, .005f)
            assertEquals(color.green, actual.green, .005f)
            assertEquals(color.blue, actual.blue, .005f)
        }
    }
}
