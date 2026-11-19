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
        compose.onNodeWithContentDescription("Profil megnyitása").performClick()
        compose.onNodeWithText("Beállítások").performClick()
        compose.onNodeWithText("Legyen a tiéd.").assertIsDisplayed()
        compose.onNodeWithText("Sötét").performClick()
        compose.onNodeWithText("Sötét").assertIsSelected()
    }

    @Test fun profileNameAndSettingsSurviveRecreation() {
        val vm = GrafitViewModel(compose.activity.application)
        val profiles = hu.grafit.data.ProfileStore(compose.activity.application)
        val original = profiles.read("demo")
        val appearance = vm.appearance
        try {
            compose.setContent { GrafitApp(vm) }
            compose.onNodeWithText("Kipróbálom mintaadatokkal").performClick()
            compose.onNodeWithContentDescription("Profil megnyitása").performClick()
            compose.onNodeWithText("Megjelenített név").performTextReplacement("Teszt Elek")
            compose.onNodeWithText("Név mentése").performScrollTo().performClick()
            compose.waitUntil(timeoutMillis = 15_000) { !vm.busy && vm.profile.displayName == "Teszt Elek" }
            compose.runOnIdle {
                vm.customize(vm.appearance.copy(mode = "dark", refreshRate = 60))
                val restored = GrafitViewModel(compose.activity.application)
                restored.startDemo()
                org.junit.Assert.assertEquals("Teszt Elek", restored.profile.displayName)
                org.junit.Assert.assertEquals("dark", restored.appearance.mode)
                org.junit.Assert.assertEquals(60, restored.appearance.refreshRate)
            }
        } finally {
            profiles.save("demo", original)
            compose.runOnIdle { vm.customize(appearance) }
        }
    }

    @Test fun loginOpensBrowserWithLoadingState() {
        val vm = GrafitViewModel(compose.activity.application)
        compose.setContent { GrafitApp(vm) }
        compose.onNodeWithText("Bejelentkezés KRÉTA-fiókkal").performClick()
        compose.onNodeWithText("KRÉTA bejelentkezés").assertIsDisplayed()
        compose.onNodeWithTag("kreta-login-browser").assertIsDisplayed()
    }
}
