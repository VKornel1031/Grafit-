package hu.grafit.ui

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ThemeSettingsTest {
    @Test fun existingPreferencesMigrateWithoutLosingOptions() {
        val a = Appearance.parse(JSONObject("""{"mode":"dark","color":3,"dynamic":false,"compact":true,"hide":true,"grades":false,"absences":false,"radius":12,"refreshRate":60}"""))
        assertEquals("dark", a.mode); assertEquals(3, a.color); assertFalse(a.dynamic)
        assertTrue(a.compact); assertTrue(a.hideGrades); assertFalse(a.showGrades); assertFalse(a.showAbsences)
        assertEquals(12f, a.radius); assertEquals(60, a.refreshRate)
        assertFalse(a.pillToolbar); assertTrue(a.homeGrades)
    }
    @Test fun everyNewSettingAndCssRoundTripsLocally() {
        val a = Appearance(pillToolbar = true, pillNavigation = true, navigationLabels = false,
            cardStyle = "elevated", textScale = 1.2f, fontFamily = "serif", pureBlack = true,
            haptics = false, homeMetrics = false, homeGrades = false, homeTasks = false, homeTimetable = false,
            customCss = ":root { --grafit-grade-1: #FF0000; --grafit-primary: #abc; }")
        assertEquals(a, Appearance.parse(JSONObject(a.json())))
    }
    @Test fun invalidStoredValuesFallBackToUsableSettings() {
        val a = Appearance.parse(JSONObject("""{"mode":"bogus","color":999,"radius":-10,"textScale":10,"fontFamily":"invalid","cardStyle":"invalid","customCss":"body { display:none; }"}"""))
        assertEquals("system", a.mode); assertEquals(6, a.color); assertEquals(0f, a.radius)
        assertEquals(1.3f, a.textScale); assertEquals("sans", a.fontFamily); assertEquals("outlined", a.cardStyle)
        assertEquals("", a.customCss)
    }
    @Test fun cssSupportsCommentsAndLastDeclarationWins() {
        val tokens = ThemeCss.parse(":root { /* theme */ --grafit-primary:#abc; --grafit-primary:#123456; --grafit-radius:32px; }")
        assertEquals("#123456", tokens["primary"])
        assertEquals(tokens, ThemeCss.parse(ThemeCss.format(tokens)))
    }
    @Test fun invalidAndUnsupportedCssIsRejectedBeforeItCanBeSaved() {
        listOf("body { color:red; }", ":root { --grafit-unknown: 1; }", ":root { --grafit-primary: url(https://example.com); }",
            ":root { --grafit-primary:#12345; }", ":root { --grafit-radius:100px; }", ":root { --grafit-font-scale:NaN; }",
            ":root { --grafit-toolbar:wide; }", "x".repeat(16_385)).forEach {
            assertTrue(it.take(60), runCatching { ThemeCss.parse(it) }.isFailure)
        }
    }
    @Test fun cssOverridesNativeControlsAndNativeControlsCanChangeThemAgain() {
        val a = Appearance(customCss = ":root { --grafit-radius:32px; --grafit-toolbar:pill; --grafit-font-scale:1.2; --grafit-card-style:elevated; }").resolveCss()
        assertTrue(a.pillToolbar); assertEquals(32f, a.radius); assertEquals(1.2f, a.textScale); assertEquals("elevated", a.cardStyle)
        val b = a.copy(pillToolbar = false, radius = 8f, textScale = 1f, cardStyle = "filled").syncCssControls(a).resolveCss()
        assertFalse(b.pillToolbar); assertEquals(8f, b.radius); assertEquals(1f, b.textScale); assertEquals("filled", b.cardStyle)
    }
    @Test fun editingOneColorPreservesTheRestAndCanBeReset() {
        val css = ThemeCss.replace(ThemeCss.template, "grade-1", "#FF0000")
        assertEquals("#216B4A", ThemeCss.parse(css)["grade-5"])
        assertEquals("#FF0000", ThemeCss.parse(css)["grade-1"])
        assertFalse("grade-1" in ThemeCss.parse(ThemeCss.replace(css, "grade-1", null)))
    }
    @Test fun customPrimaryAndBackgroundProduceReadableAutomaticTextColors() {
        val c = customColors(androidx.compose.material3.lightColorScheme(), ThemeCss.parse(":root { --grafit-primary:#000; --grafit-background:#000; --grafit-surface:#000; }"))
        assertEquals(androidx.compose.ui.graphics.Color.White, c.onPrimary)
        assertEquals(androidx.compose.ui.graphics.Color.White, c.onBackground)
        assertEquals(androidx.compose.ui.graphics.Color.White, c.onSurface)
    }

    @Test fun rootBlocksRejectExtraBracesAndAllowWhitespaceWithoutRegexBraceParsing() {
        assertEquals("#CCC2DC", ThemeCss.parse(":root \n { --grafit-secondary: #CCC2DC; }")["secondary"])
        listOf(":root { } :root { }", ":root { --grafit-primary:{red}; }", ":root {", ":root }", ":rootx {}").forEach {
            assertTrue(it, runCatching { ThemeCss.parse(it) }.isFailure)
        }
    }
}
