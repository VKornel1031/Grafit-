package hu.grafit.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class GrafitFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun demoNavigationAndCustomizationWork() {
        val vm = GrafitViewModel(compose.activity.application)
        compose.setContent { GrafitApp(vm) }
        compose.onNodeWithText("Kipróbálom mintaadatokkal").performClick()
        compose.onNodeWithText("Bemutató mód · mintaadatok").assertIsDisplayed()
        compose.onAllNodesWithText("Órarend").onFirst().assertExists()
        compose.onNodeWithText("Jegyek").performClick()
        compose.onNodeWithText("Jegyeid").assertIsDisplayed()
        compose.onNodeWithText("Hiányzások").performClick()
        compose.onNodeWithText("Igazolatlan").assertIsDisplayed()
        compose.onNodeWithContentDescription("Megjelenés és beállítások").performClick()
        compose.onNodeWithText("Legyen a tiéd.").assertIsDisplayed()
        compose.onNodeWithText("Sötét").performClick()
        compose.onNodeWithText("Sötét").assertIsSelected()
    }
}
