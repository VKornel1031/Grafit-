package hu.grafit.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ToolsFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun gradesOpenSimulatorAndReturnToSubjects() {
        val vm = GrafitViewModel(compose.activity.application, checkUpdatesOnStart = false)
        compose.runOnUiThread { vm.startDemo() }
        compose.setContent { GrafitApp(vm) }
        compose.onNodeWithText("Eszközök").assertDoesNotExist()
        compose.onNodeWithText("Jegyek").performClick()
        compose.onNodeWithText("Mi lenne, ha?").performClick()
        compose.onNodeWithText("Próbajegy hozzáadása").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Vissza").performClick()
        compose.onNodeWithText("Tantárgyak és átlagok").assertIsDisplayed()
    }

    @Test fun profileOpensFullWidthToolsAndCalendar() {
        val vm = GrafitViewModel(compose.activity.application, checkUpdatesOnStart = false)
        compose.runOnUiThread { vm.startDemo() }
        compose.setContent { GrafitApp(vm) }
        compose.onNodeWithContentDescription("Profil megnyitása").performClick()
        compose.onNodeWithText("Eszközök / Egyéb").performScrollTo().performClick()
        compose.onNodeWithText("Mi lenne, ha?").assertDoesNotExist()
        val planner = compose.onNodeWithText("Tanulási terv").fetchSemanticsNode().boundsInRoot
        val statistics = compose.onNodeWithText("Statisztikák").fetchSemanticsNode().boundsInRoot
        assertEquals(planner.left, statistics.left, 1f)
        assertEquals(planner.right, statistics.right, 1f)
        compose.onNodeWithText("Naptár").performScrollTo().performClick()
        compose.onNodeWithText("Saját esemény").assertExists()
        compose.onNodeWithText("‹ Eszközök · Naptár").performClick()
        compose.onNodeWithText("A te iskolai eszköztárad").assertIsDisplayed()
        compose.onNodeWithContentDescription("Vissza").performClick()
        compose.onNodeWithText("Kezdőlap").assertExists()
    }
}
