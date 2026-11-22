package hu.grafit.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemeCssAndroidTest {
    @Test fun unchangedDefaultColorAndCssCanBeAppliedWithAndroidIcu() {
        val css = ThemeCss.replace("", "secondary", "#CCC2DC")
        assertEquals("#CCC2DC", ThemeCss.parse(css)["secondary"])
        assertEquals(css, ThemeCss.replace(css, "secondary", "#CCC2DC"))
        assertEquals(ThemeCss.parse(ThemeCss.template), ThemeCss.parse(ThemeCss.format(ThemeCss.parse(ThemeCss.template))))
        assertTrue(ThemeCss.parse(ThemeCss.replace(css, "secondary", null)).isEmpty())
    }
}
