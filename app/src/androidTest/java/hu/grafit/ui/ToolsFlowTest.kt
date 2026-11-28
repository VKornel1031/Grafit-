package hu.grafit.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ToolsFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun toolHubOpensSimulatorAndCalendarInDemo() {
        val vm = GrafitViewModel(compose.activity.application, checkUpdatesOnStart = false)
        compose.runOnUiThread { vm.startDemo() }
        compose.setContent { GrafitApp(vm) }
        compose.onNodeWithText("Eszközök").performClick()
        compose.onNodeWithText("Mi lenne, ha?").performClick()
        compose.onNodeWithText("Próbajegy hozzáadása").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("‹ Eszközök · Mi lenne, ha?").performClick()
        compose.onNodeWithText("Naptár").performScrollTo().performClick()
        compose.onNodeWithText("Saját esemény").assertExists()
    }
}
